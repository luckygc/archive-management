# 项目审查问题修复设计

## 目标

修复项目审查确认的三个运行与合同问题，并收敛前端生成声明的验证漂移：

1. 同步导出不得在只读事务中写审计。
2. 规则追踪必须先按用户数据范围过滤，再执行稳定的 cursor 分页，不得固定截取全局前 500 条后在内存中过滤。
3. 档案导出和导入模板先通过有副作用语义正确的 POST 生成临时 S3 对象与用户绑定短链，不得把完整查询编码到 GET URL，也不得通过 XHR 读取文件内容。
4. `pnpm check` 不得改写已提交的 `web/src/components.d.ts`。

本次保留现有同步导入导出上限，不引入后台 Job、消息队列或新的基础设施抽象。

## 生成文件事务与 HTTP 合同

`ArchiveItemImportExportService.exportItems` 继续在一个事务中完成权限校验、数据范围查询、Excel 生成和导出审计写入，但事务改为普通可写事务。生成文件、导出审计、临时存储对象记录和用户短链保持同一数据库事务成功边界：任一步失败时请求失败，不返回未审计或不可用的成功下载。

当前两处直接业务下载统一调整为创建短链 custom method：

```http
POST /api/v1/archive-categories/{categoryId}/archive-items:createImportTemplateDownloadLink
POST /api/v1/archive-items:createExportDownloadLink
```

导出接口在 JSON 请求体中接收当前查询条件；两个接口都返回 `{ url, expiresAt }`。删除导入模板直接 GET、导出直接文件流 GET/POST 和 Base64 查询 URL，不保留兼容分支。电子文件已经使用短链，合同保持不变。

所有最终下载统一使用现有 `GET /api/v1/file-links/{code}:download`。前端先调用对应 POST，成功后通过临时 `<a>` 打开返回的安全 GET 短链；浏览器直接接收附件响应，前端不使用 XHR/fetch 读取 Blob，也不持有 Excel 内容。

## 临时 S3 对象生命周期

导入模板和导出结果都通过现有 `StorageObjectService` 保存到 S3 兼容存储，并复用 `am_storage_object`、`am_file_link`、`FileLinkService` 和 `STORAGE_OBJECT` 目标解析器，不新增生成任务表或下载适配层。

临时存储对象与用户绑定短链都使用 10 分钟 TTL。临时对象的 `am_storage_object.expires_at` 固化过期时间；永久电子文件的 `expires_at` 为空，不进入临时对象清理。

S3 不参与数据库事务。临时对象上传成功后注册事务回滚补偿：如果外层事务最终回滚，按已经返回的 bucket 和 object key 删除 S3 对象，避免产生没有本地记录的孤儿文件。

新增统一过期存储对象清理器，分批读取 `expires_at <= now` 的记录。每条记录先幂等删除 S3 对象，成功后硬删除对应数据库记录；S3 删除失败时保留数据库位置和过期信息，供下次清理重试。短链继续由现有短链清理器删除。最终短链 GET 在读取前继续校验短链存在、未过期、未撤销且属于当前用户。

## 规则追踪可见性与分页

规则追踪接口调整为：

```http
POST /api/v1/archive-rule-traces:search?limit=100&cursor=...
Content-Type: application/json
```

请求体只保留业务筛选字段；`limit`、`cursor` 和 `requestTotal` 由通用 `PageRequest` 参数解析器从 URL query 读取。响应从 `CollectionResponse` 改为 `CursorPageResponse`，排序固定为 `created_at DESC, id DESC`，cursor 同时绑定创建时间和 ID。

可见性在 MyBatis SQL 的 `WHERE` 中完成：

- 全量数据范围用户直接查询全部匹配追踪。
- `ARCHIVE_ITEM` 追踪通过 `am_archive_item` 关联对象，并复用按分类编译的数据范围 SQL group；动态字段条件通过已经校验的分类动态表名和列名进入 `EXISTS`。
- `ARCHIVE_VOLUME` 追踪通过 `am_archive_volume` 关联对象，按分类、全宗、密级和保管期限过滤；含动态字段条件的范围不授予案卷可见性，与现有 `ArchiveVolumeService` 规则一致。
- 其他对象类型只允许追踪创建人查看，保持现有语义。

Service 为当前用户构造一次可见性条件，Mapper 在数据库内完成可见性过滤、cursor 边界和 `limit + 1` 查询。这样每页不会产生逐条档案或案卷读取，也不会因其他用户的前 500 条数据而漏掉当前用户可见记录。

## 前端交互

规则追踪页增加统一 `CursorPagination`，查询条件分为草稿和已提交请求。提交新查询或修改分页大小时清空旧 cursor；上一页、下一页使用已提交请求，只替换 cursor。加载失败显示可重试错误状态，保留当前已成功结果。

导出和模板下载按钮继续显示同步加载状态。创建短链 POST 成功后通过临时 `<a>` 打开返回 URL；POST 失败时使用统一 ProblemDetail 错误解析且不触发下载。前端核心 `httpClient.download()` 继续只生成浏览器可直接打开的安全 GET 链接，不新增 POST Blob 下载能力。

`components.d.ts` 作为生成文件，统一为生成器实际产物，不再手工维持与生成器冲突的分号或已移除组件声明。验证后工作树应保持干净。

## 错误与兼容性

- 无效 cursor 继续由通用分页组件返回统一 ProblemDetail。
- 规则追踪接口响应合同发生变更，后端 Controller、前端类型、API client、页面和文档必须同批更新。
- 原有导入模板直接 GET、导出直接文件流 GET/POST 是尚未正式发布的错误合同，直接删除，不保留兼容分支。
- 创建短链前发生权限不足、数据范围不足、Excel 生成失败、S3 写入失败或短链创建失败时返回统一 ProblemDetail；最终短链不存在、越权或过期时统一返回 404，避免泄漏目标信息。
- 不新增统一下载适配层或前端 Blob 处理；复用现有短链、存储对象和临时 `<a>` 下载能力。

## 测试策略

按 TDD 分五个闭环推进：

1. 先增加 Spring 事务集成测试，证明导出审计能在真实 PostgreSQL 可写事务中写入，再修改只读事务。
2. 先增加临时存储对象和清理测试，证明 10 分钟过期时间、事务回滚 S3 补偿、S3 成功后删除数据库记录以及失败保留重试，再实现生命周期能力。
3. 先更新 Controller 与前端 API 测试，证明模板和导出都先 POST 创建用户绑定短链，再由临时 `<a>` 请求安全 GET，且不存在直接文件流接口或 Blob 读取，再修改实现。
4. 先增加规则追踪 Mapper/Service 集成测试，构造超过 500 条不可见追踪和较旧可见追踪，证明 SQL 可见性与 cursor 翻页不漏数、无重复，再替换内存过滤。
5. 先增加生成声明稳定性检查或调整生成文件，运行两次 `pnpm check` 并验证第二次无 Git 差异。

最终运行后端目标测试、完整 `mvn test`、Spotless、前端 `pnpm check`、`pnpm test`、`pnpm build` 和源码行数检查。预览服务没有代码改动，不纳入本次修复验证。
