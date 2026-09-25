## 1. 0.0.1 目标数据库结构

- [x] 1.1 直接重写尚未发布的运行时规则 Flyway，删除本体、字段语义和旧规则/effect DDL，只创建运行时定义、固定动作和决策追踪目标结构；验证：运行时结构 PostgreSQL 集成测试通过，且空库迁移后不存在任何 ontology/field_semantic/旧 effect 表。
- [x] 1.2 为目标结构补齐命名 `NOT NULL`、`CHECK`、外键、唯一索引及执行查询索引；验证：`cd server && mise exec -- mvn -Dtest=ArchiveRuntimeSchemaIntegrationTests test` 退出码为 0，且所有约束名、索引、列类型和枚举白名单与设计一致。
- [x] 1.3 在基础迁移中创建发布后不可变触发器和可延迟跨表约束触发器，不硬编码 `public` schema；验证：`cd server && mise exec -- mvn -Dtest=ArchiveRuntimeTriggerIntegrationTests test` 退出码为 0，且直接 SQL 修改发布定义、删除发布动作、发布空动作规则均被指定 SQLSTATE/约束名拒绝，合法同事务发布成功。

## 2. 真实字段目录与结构化表达式

- [x] 2.1 删除生产代码中的 `ArchiveOntology*` 残留，新增按分类和触发点生成固定字段、动态字段、实物字段和上下文字段的目录服务；验证：字段目录测试通过，且字段代码、类型、读写能力与分类隔离断言全部通过。
- [x] 2.2 重构结构化 AST 校验与求值，覆盖 `all`、`any`、`not`、类型化比较、空值、深度、节点数、集合数和文本长度上限；验证：`cd server && mise exec -- mvn -Dtest=ArchiveRuntimeConditionValidatorTests,ArchiveRuntimeConditionEvaluatorTests test` 退出码为 0，且自由 SQL/脚本、未知字段和类型不兼容操作符均被拒绝。
- [x] 2.3 为字段删除、停用和改型增加已发布运行时定义反向引用保护；验证：`cd server && mise exec -- mvn -Dtest=ArchiveRuntimeFieldReferenceIntegrationTests test` 退出码为 0，且被发布定义引用的字段不能破坏性变更，未引用草稿字段仍可按原元数据规则维护。

## 3. 用户定义约束、规则与固定动作

- [x] 3.1 实现运行时定义和动作的实体、Jakarta Data Repository、复杂查询 Mapper 与显式 Service 状态边界；验证：`mise run server-format-check && mise run server-compile` 均退出码为 0，且 ArchUnit 未报告模块依赖或 Repository 约定违规。
- [x] 3.2 实现约束/规则草稿创建修改、发布、启用、停用和删除，发布校验字段、AST、作用域、触发点及动作兼容性；验证：`cd server && mise exec -- mvn -Dtest=ArchiveRuntimeDefinitionServiceTests test` 退出码为 0，且草稿/发布不可变/启停状态测试全部通过。
- [x] 3.3 实现代码注册的 `REJECT`、`WARN`、`SET_FIELD` 动作处理器及参数合同，禁止动作直接产生持久化或外部副作用；验证：`cd server && mise exec -- mvn -Dtest=ArchiveRuntimeActionHandlerTests test` 退出码为 0，且触发点不兼容、不可写字段、类型转换失败和未知动作均被拒绝。
- [x] 3.4 实现按 `priority, definition_code, id` 的单次执行管线、候选字段赋值、最终约束校验、同字段冲突检测和执行上限；验证：`cd server && mise exec -- mvn -Dtest=ArchiveRuntimeExecutionServiceTests test` 退出码为 0，且后续规则可读取先前候选值、不同值冲突阻断、同值赋值幂等、规则不循环执行。
- [x] 3.5 重构决策追踪为新定义/触发点/动作摘要，并保留数据库内权限与数据范围 cursor 过滤；验证：`cd server && mise exec -- mvn -Dtest=ArchiveRuntimeTraceServiceTests,ArchiveRuntimeTraceMapperIntegrationTests test` 退出码为 0，且跨页无重复遗漏、不可见记录不泄露、敏感事实不落追踪。

## 4. HTTP API 与定义发布

- [x] 4.1 新增运行时定义 CRUD、发布、启停、字段目录、试运行和追踪 API，使用完整 `/api/v1` URL、专用 Request/Response 与 ProblemDetail；验证：`cd server && mise exec -- mvn -Dtest=ArchiveRuntimeControllerTests,ArchiveRuntimeControllerProblemDetailTests test` 退出码为 0，且 URL、状态码、字段错误和稳定错误码断言通过。
- [x] 4.2 让运行时定义直接携带作用域，移除 `RULE_SET`、本体和字段语义绑定；验证：无效运行时定义无法发布，已发布定义不可原地修改。
- [x] 4.3 让试运行复用真实执行核心但禁止主数据、审计和追踪写入；验证：`cd server && mise exec -- mvn -Dtest=ArchiveRuntimeSimulationIntegrationTests test` 退出码为 0，且返回顺序、命中、候选变化、警告和阻断与真实执行一致，相关业务表计数不变。

## 5. 真实业务触发接入

- [x] 5.1 在条目创建、修改和删除 Service 的持久化前接入固定触发点，并以最终候选值继续类型、数据范围和数据库校验；验证：`cd server && mise exec -- mvn -Dtest=ArchiveItemRuntimePolicyIntegrationTests test` 退出码为 0，且 PostgreSQL 事务中覆盖字段赋值、警告、阻断回滚、动态字段和审计一致性。
- [x] 5.2 在案卷创建和条目入卷 Service 接入固定触发点；验证：`cd server && mise exec -- mvn -Dtest=ArchiveVolumeRuntimePolicyIntegrationTests test` 退出码为 0，且阻断时案卷、动态行、成员归属和排序均不变。
- [x] 5.3 在电子文件上传元数据落库前接入固定触发点并覆盖对象存储失败补偿；验证：`cd server && mise exec -- mvn -Dtest=ArchiveElectronicFileRuntimePolicyTests test` 退出码为 0，且阻断时无文件元数据，已创建临时对象按现有补偿合同清理或过期。
- [x] 5.4 让导入预检/提交复用条目执行核心，让导出在生成文件前执行固定触发点；验证：`cd server && mise exec -- mvn -Dtest=ArchiveImportRuntimePolicyTests,ArchiveExportRuntimePolicyIntegrationTests test` 退出码为 0，且导入不能绕过规则、导出阻断不产生对象/短链/成功审计。
- [ ] 5.5 对所有业务接入执行完整后端回归；验证：`mise run server-test` 退出码为 0，Docker 可用时 PostgreSQL 集成测试实际执行而非跳过，Docker 不可用时输出准确跳过原因且不得标记数据库行为已验证。

## 6. 管理端运行时规则工作区

- [x] 6.1 删除本体/字段语义路由、页面、类型和 API client，提供独立运行时规则入口；验证：前端检查和测试通过，且路由测试证明菜单和页面不再出现本体或字段语义入口。
- [x] 6.2 实现约束/规则高密度列表和草稿表单，按分类/触发点加载真实字段目录并只展示兼容固定动作；验证：`mise run web-test` 退出码为 0，且组件测试覆盖加载、空状态、字段切换、动作参数、提交中和原位错误恢复。
- [x] 6.3 实现发布校验定位、试运行决策展示、候选字段变化和警告/阻断反馈；验证：`mise run web-test` 退出码为 0，且测试证明后端节点错误定位正确、试运行不会触发保存、警告与阻断状态可区分。
- [x] 6.4 执行前端完整检查与构建；验证：`mise run frontend-ready` 退出码为 0，且生产构建无新增类型错误、lint 错误或设计检测问题。

## 7. 本体历史清理与最终闭环

- [ ] 7.1 删除现行源码、API、前端、权限文案、文档、旧 DDL、相关历史 change 和稳定能力中的本体/字段语义残留，不保留兼容路由、迁移数据或历史说明；验证：治理检查通过，且生产源码、迁移和现行规格中均找不到 ontology/本体/field_semantic。
- [ ] 7.2 更新 `docs/architecture.md` 和相关知识库，只记录用户定义条件、固定触发点/动作、数据库边界和失败关闭；验证：治理检查和 OpenSpec 严格校验通过。
- [ ] 7.3 执行格式、后端、前端和 OpenSpec 最终验证；验证：真实验证入口全部通过，且没有将跳过的 PostgreSQL 测试描述为已验证。
