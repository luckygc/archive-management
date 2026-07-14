# 生产源码职责治理审查整改设计

## 背景

生产源码职责收敛改动已经完成一轮实现，但审查发现行数门禁仍使用前端 300 行、后端 500 行的单一硬阈值，与最终确认的前端 300–500 行、后端 500–700 行合理区间不一致。同时，统计脚本没有跨行维护字符串状态，部分后端拆分留下纯转发方法，实施文档与最终实现存在偏差，前端新边界缺少聚焦交互测试。

本次整改只修复这些已确认问题，不改变 HTTP API、数据库结构、权限、事务、错误响应或页面交互合同。

## 目标

- 前端以 300 行为软提示线、500 行为硬失败线。
- 后端以 500 行为软提示线、700 行为硬失败线。
- 统计脚本正确处理跨行模板字符串和 Java 文本块中的注释标记。
- 保留职责仍然集中的字段元数据协调 Service，并让文档准确描述最终边界。
- 删除本体关系与事件用例的纯转发路径，让 Controller 依赖真实用例 Service。
- 为本轮前端拆分形成的关键交互边界增加稳定测试。
- 全部检查、测试和构建通过，工作区不残留生成文件副作用。

## 行数门禁

`scripts/source-lines.pl` 继续扫描 `server/src/main/java`、`web/src`、`frontend-core/src` 和 `preview`。默认输出超过软提示线的文件，但只有超过硬失败线时才返回非零状态：

- `web`、`frontend-core`：软提示 300，硬失败 500。
- `server`、`preview`：软提示 500，硬失败 700。

`--report` 保持只报告、不失败的语义。脚本允许传入显式扫描目录，以便契约测试使用临时夹具；未传目录时仍扫描仓库默认目录，不新增配置文件或环境变量。

脚本在文件级维护以下词法状态：

- 块注释 `/* ... */`；
- HTML 注释 `<!-- ... -->`；
- TypeScript/Vue 模板字符串 `` `...` ``；
- Java 文本块 `"""..."""`；
- 普通单引号和双引号字符串及反斜杠转义。

契约测试使用 Perl 核心 `Test::More` 和临时目录，不引入新依赖。测试覆盖软提示不失败、硬阈值失败、`--report` 强制成功，以及字符串内部注释标记不影响后续代码统计。

## 后端边界

### 元数据

按照最终 500–700 行合理区间，不再为删除类名而机械拆分 `ArchiveMetadataService`。该类保留字段定义、有效布局、动态表构建和唯一约束的事务协调职责；全宗、分类方案、门类与参考数据继续由已经拆出的具体 Service 承担。

文档删除“必须删除 `ArchiveMetadataService`”和“所有调用方必须直连底层专项协作者”的过时要求，改为：调用方依赖能完整表达业务用例与事务边界的具体 Service，不通过无业务语义的兼容门面转发。

### 本体

`ArchiveOntologyService` 继续负责对象类型、属性类型和属性映射。`ArchiveOntologyRelationService` 负责关系类型与事件类型。`ArchiveOntologyController` 同时注入两个具体 Service：

- 对象、属性、映射端点调用 `ArchiveOntologyService`；
- 关系、事件端点调用 `ArchiveOntologyRelationService`。

删除 `ArchiveOntologyService` 中仅调用 `ArchiveOntologyRelationService` 的公开方法及多余依赖。事务边界保留在实际完成查询或写入的 `ArchiveOntologyRelationService` public 方法上。

## 前端测试边界

不为了追求组件测试数量逐个复制模板细节，只覆盖跨文件后最容易回归的行为合同：

- `ArchiveItemActions`：选择文件后发出 `importFile`，并清空 input 以允许重复选择同一文件。
- `ArchiveItemEditorDrawer`：表单校验成功才发出 `save`，取消与关闭发出 `close`。
- `ArchiveCategoryScopeDialog`：`show()` 使用首个全宗加载范围，保存成功关闭弹层。
- 本体或治理拆分组件：业务按钮发出语义事件，父页面无需读取子组件内部状态。

测试使用项目现有 Vitest、Vue Test Utils 和 API mock 方式，不引入浏览器服务、E2E 框架或新状态管理层。

## 文档同步

同步更新现有生产源码职责治理设计和实施计划：

- 将前端约 300、后端约 500 的单点目标改为软提示线与硬失败线。
- 记录保留 `ArchiveMetadataService` 的最终判断及理由。
- 把不存在的 `ArchiveClassificationService` 修正为实际的 `ArchiveMetadataReferenceService`。
- 标记已经完成的历史步骤，并增加本次整改验证结果，避免完成提交与全空复选框并存。

## 验证

按以下顺序验证：

1. `prove scripts/source-lines.t`；
2. `pnpm run check:source-lines`；
3. 前端新增聚焦测试；
4. `pnpm check`、`pnpm test`、`pnpm build`；
5. 在 `server/` 运行 `mise exec -- mvn -q spotless:check test`；
6. `git diff --check` 和 `git status --short`，确认没有非预期生成文件。

## 实施结果

- 行数门禁契约测试 5 项全部通过，默认扫描仅报告一个 315 行的前端软提示文件。
- 前端 `pnpm check`、24 个测试文件中的 56 项测试及生产构建全部通过；构建仅报告第三方 `@vueuse/core` 的两条 Rolldown PURE 注解位置警告。
- 后端 Spotless 检查与完整 Maven 测试通过。
- 构建对 `web/src/components.d.ts` 产生的分号格式副作用已恢复，没有纳入整改内容。

## 非目标

- 不继续拆分所有 300/500 行以上文件。
- 不删除仍承载完整事务协调职责的 Service。
- 不修改 API DTO、URL、JSON 字段或权限编码。
- 不引入新的统计框架、前端测试框架或基础设施抽象。
