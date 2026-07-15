# 项目审查问题修复设计

## 目标

修复项目审查确认的三个运行与合同问题，并收敛前端生成声明的验证漂移：

1. 同步导出不得在只读事务中写审计。
2. 规则追踪必须先按用户数据范围过滤，再执行稳定的 cursor 分页，不得固定截取全局前 500 条后在内存中过滤。
3. 档案导出只使用有副作用语义正确的 `POST /api/v1/archive-items:export`，不得把完整查询编码到 GET URL。
4. `pnpm check` 不得改写已提交的 `web/src/components.d.ts`。

本次保留现有同步导入导出上限，不引入后台 Job、消息队列或新的基础设施抽象。

## 导出事务与 HTTP 合同

`ArchiveItemImportExportService.exportItems` 继续在一个事务中完成权限校验、数据范围查询、Excel 生成和导出审计写入，但事务改为普通可写事务。导出审计与本次导出保持同一成功边界：Excel 生成或审计写入失败时请求失败，不返回未审计的成功下载。

服务端删除 `GET /api/v1/archive-items:export`，只保留 POST custom method。前端通过现有 `httpClient` 发送 JSON 查询体并读取二进制响应，不再生成 Base64 查询 URL，也不再通过临时 `<a>` 触发该导出。导入模板和电子文件等真正安全的 GET 下载保持不变。

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

导出按钮继续显示同步加载状态。POST 二进制响应成功后使用对象 URL 下载，并在触发后释放对象 URL；失败继续使用统一错误解析。

`components.d.ts` 作为生成文件，统一为生成器实际产物，不再手工维持与生成器冲突的分号或已移除组件声明。验证后工作树应保持干净。

## 错误与兼容性

- 无效 cursor 继续由通用分页组件返回统一 ProblemDetail。
- 规则追踪接口响应合同发生变更，后端 Controller、前端类型、API client、页面和文档必须同批更新。
- GET 导出是尚未正式发布的错误合同，直接删除，不保留兼容分支。
- 不新增统一下载适配层；仅在现有 HTTP client 中增加最小的 POST 二进制请求能力或复用其现有底层请求方法。

## 测试策略

按 TDD 分四个闭环推进：

1. 先增加 Spring 事务集成测试，证明导出审计能在真实 PostgreSQL 事务中写入，再修改只读事务。
2. 先增加规则追踪 Mapper/Service 集成测试，构造超过 500 条不可见追踪和较旧可见追踪，证明 SQL 可见性与 cursor 翻页不漏数、无重复，再替换内存过滤。
3. 先更新 Controller 与前端 API 测试，证明只存在 POST 导出且查询不进入 URL，再修改实现。
4. 先增加生成声明稳定性检查或调整生成文件，运行两次 `pnpm check` 并验证第二次无 Git 差异。

最终运行后端目标测试、完整 `mvn test`、Spotless、前端 `pnpm check`、`pnpm test`、`pnpm build` 和源码行数检查。预览服务没有代码改动，不纳入本次修复验证。
