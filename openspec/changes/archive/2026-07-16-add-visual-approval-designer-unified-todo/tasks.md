## 1. 依赖和持久化边界

- [x] 1.1 将 LogicFlow core 精确版本加入前端 catalog 和 PC 包，保留 Apache-2.0 协议信息；运行 `mise exec -- pnpm --filter @archive-management/web list @logicflow/core`，预期只解析 `@logicflow/core@2.2.4` 且不出现 `@logicflow/extension`。
- [x] 1.2 将审批迁移收敛为定义、定义版本、业务实例和统一待办四张项目表，并补齐唯一约束、用户待办索引和表注释；运行 `rg -n '^create table am_(approval|unified)' server/src/main/resources/db/migration/V20260717_0100__create_approval_workflow.sql`，预期只输出四条建表语句。
- [x] 1.3 新增统一待办固定实体和窄 Jakarta Data Repository，删除审批任务、候选人和意见实体/Repository；运行 `cd server && mise exec -- mvn -DskipTests compile`，预期编译成功且生成元模型无错误。

## 2. 审批流程图和 Flowable 运行时

- [x] 2.1 新增审批流程图、节点类型、条件运算符和连线模型，实现保存与发布结构校验；运行 `cd server && mise exec -- mvn -Dtest=ApprovalWorkflowGraphValidatorTests test`，预期合法流程通过，断路、环、重复编码和非法网关用例被拒绝。
- [x] 2.2 将 BPMN 生成器升级为按流程图输出开始、用户任务、排他网关、默认流和受控条件表达式；运行 `cd server && mise exec -- mvn -Dtest=ApprovalBpmnXmlGeneratorTests test`，预期 XML 结构、条件转义和默认分支断言全部通过。
- [x] 2.3 扩展 Flowable 窄端口和适配器，支持活动/历史任务、候选人、评论、完成和终止，不向业务模块暴露 Flowable 类型；运行 `cd server && mise exec -- mvn -Dtest=FlowableCompatibilityTests,ApprovalWorkflowIntegrationTests test`，预期真实引擎完成部署、分支执行、评论和历史查询。
- [x] 2.4 将审批定义 API 从顺序节点升级为流程图并新增版本 cursor 查询；运行 `cd server && mise exec -- mvn -Dtest=ApprovalWorkflowDefinitionServiceTests test`，预期草稿布局、发布冻结、版本排序和权限测试通过。

## 3. 统一待办和审批办理

- [x] 3.1 实现统一待办幂等投递、按用户 cursor 查询、办理人完成、其他候选取消和来源整体取消；运行 `cd server && mise exec -- mvn -Dtest=UnifiedTodoServiceTests test`，预期投递唯一性和三种状态转换测试通过。
- [x] 3.2 新增 `/api/v1/unified-todos` 列表合同并同步权限、分页和来源路径校验；运行 `cd server && mise exec -- mvn -Dtest=UnifiedTodoControllerTests test`，预期只返回当前用户记录且非法站外路径返回 ProblemDetail。
- [x] 3.3 重构审批实例服务：从 Flowable 查询任务/候选/历史/评论，使用统一待办投影定位并二次授权，移除任务/候选/意见双写；运行 `cd server && mise exec -- mvn -Dtest=ApprovalWorkflowInstanceServiceTests,ApprovalWorkflowIntegrationTests test`，预期同意、分支流转、驳回、撤回、终止、已办和详情用例通过。
- [x] 3.4 更新审批 Controller、HTTP record、架构边界和授权测试；运行 `cd server && mise exec -- mvn -Dtest=ArchitectureRulesTest,ApprovalWorkflowIntegrationTests test`，预期模块依赖、完整 URL、数字待办 ID 和服务端权限断言通过。

## 4. 可视化流程设计器

- [x] 4.1 新增独立设计器路由、LogicFlow 画布适配和开始/审批/条件/结束业务节点，支持添加、拖拽、连线、选择、删除、撤销重做、缩放和适应画布；运行 `mise exec -- pnpm --filter @archive-management/web test -- ApprovalWorkflowDesigner`，预期核心画布命令和图数据转换测试通过。
- [x] 4.2 实现定义信息、节点/连线属性面板、指定用户选择、条件配置、问题列表定位、保存失败保留和未保存离开提示；运行 `mise exec -- pnpm --filter @archive-management/web test -- ApprovalWorkflowDesignerPage`，预期编辑、校验、保存、错误恢复和离开保护测试通过。
- [x] 4.3 调整定义列表、类型和 API client，支持进入设计、新建默认图、保存草稿、发布和查看版本；运行 `mise exec -- pnpm --filter @archive-management/web test -- ApprovalWorkflowDefinitionsPage approval-workflow`，预期列表与 client 合同测试通过。

## 5. 统一待办前端闭环

- [x] 5.1 将审批中心待办/已办切换到统一待办 API，并保持我发起的、办理弹层和流程详情可用；运行 `mise exec -- pnpm --filter @archive-management/web test -- ApprovalCenterPage unified-todo`，预期待办、已办、同意、驳回和来源跳转测试通过。
- [x] 5.2 覆盖设计器和统一待办的加载、空、错误、禁用、提交中、长文本、键盘和窄屏状态；运行 `task web-check && task web-test && task web-build`，预期类型检查、全部 PC 测试和生产构建通过且无控制台构建错误。

## 6. 总体验证和规范收敛

- [x] 6.1 执行 Java 格式化并运行审批、统一待办和架构测试；运行 `task server-format && cd server && mise exec -- mvn -Dtest='Approval*,UnifiedTodo*,FlowableCompatibilityTests,ArchitectureRulesTest' test`，预期 Spotless 完成且目标测试全部通过。
- [x] 6.2 执行后端完整测试和打包；运行 `task server-test && task server-package`，预期 Maven 完整测试及 package 成功。
- [x] 6.3 校准 OpenSpec 主规格、用户文档和依赖许可记录，执行 `task governance-check`，预期 OpenSpec strict validate、治理测试和治理脚本全部通过。
