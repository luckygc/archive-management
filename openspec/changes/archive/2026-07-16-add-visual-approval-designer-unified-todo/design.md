## Context

当前审批定义以 `List<ApprovalNode>` 保存，只能生成开始事件、顺序用户任务和结束事件；前端通过弹窗表格增删和排序节点。运行期则同时写 Flowable `ACT_*` 和 `am_approval_workflow_task`、`am_approval_workflow_task_candidate`、`am_approval_workflow_opinion`，形成两套任务、候选人与历史数据。审批中心只能查询审批任务，无法汇总其他业务待办。

本变更跨越 PC 画布、审批 HTTP 合同、Flowable 适配、固定实体和跨业务模块，需要同时遵守 `PRODUCT.md`、`DESIGN.md`、`docs/architecture.md`、`api-contract` 与审批规格。管理员是流程设计器的主要用户；档案人员和业务经办人是统一待办的主要用户。

## Goals / Non-Goals

**Goals:**

- 提供可拖拽、连线、配置、校验、保存和发布的生产级审批流程设计器。
- 支持开始、审批、排他条件分支和结束节点，条件使用受控字段/运算符/值，不接受任意表达式。
- 保留草稿画布布局，发布时冻结不可变图版本并生成可执行 Flowable BPMN。
- 以 Flowable 作为审批任务、候选关系、历史和意见的唯一流程真相源。
- 建立一名办理人一条记录的统一待办投影，支持跨业务待办、已办和来源跳转。
- 只保留定义、版本、业务实例绑定和统一待办四张项目表，并保持所有业务模块通过公开 Service 协作。

**Non-Goals:**

- 不引入 Flowable UI/Modeler、IDM、CMMN、DMN 或商业组件。
- 不提供任意 BPMN 元素、任意脚本、用户编辑 XML、并行网关、会签、加签、转办、委托和运行中迁移。
- 不让统一待办成为来源业务的授权或状态真相源。
- 不为尚未接入的业务预建来源适配器、消息中间件或补偿调度框架。

## Decisions

### 1. 使用 LogicFlow core 承载画布，业务图模型独立于组件数据

引入 `@logicflow/core` 2.2.4。该版本使用 Apache-2.0，近期仍维护，提供 SVG 画布、自定义节点、连线、撤销重做、缩放、适应画布和图数据 API，且官方定位包含审批流配置。只引入 core，不引入 extension，避免当前不需要的富文本编辑器、层级布局等传递依赖。

前端建立本地适配组件，把 LogicFlow 节点/边转换为项目 `ApprovalWorkflowGraph`。服务端只接收项目业务图，不持久化 LogicFlow 专用类型、插件状态或 DOM 样式。这样升级画布库不会改变审批 HTTP 和数据库合同。

替代方案：

- `bpmn-js` 能直接编辑 BPMN，但其 bpmn.io 专用许可要求保留模型器标识，且完整 BPMN 对档案管理员过于复杂。
- Vue Flow 使用 MIT 且 Vue 3 原生，但更偏通用节点编辑；审批语义、流程校验和 BPMN 适配仍需同等开发量。
- 自研 SVG/Canvas 会重复处理缩放、坐标、连线、选中、快捷键与撤销历史，不符合最小闭环。

### 2. 项目图模型保存业务语义和布局，不直接保存 BPMN XML

`ApprovalWorkflowGraph` 包含：

- `nodes`：稳定节点编码、节点类型、名称、画布坐标；审批节点另含指定候选用户和允许动作。
- `edges`：稳定连线编码、来源、目标；从排他分支发出的连线另含受控条件或默认分支标记。

开始和结束节点由新建流程自动生成。审批节点仍只支持指定用户；条件键限制为安全编码，运算符首版支持 `EQUALS`、`NOT_EQUALS`、`IN`，条件值作为字符串处理。发布校验要求：恰好一个开始和结束、节点编码唯一、全部节点从开始可达且可到达结束、无环、审批节点候选人有效、排他网关至少两条出边且恰好一条默认分支、非默认出边条件完整。

发布时后端遍历图生成 BPMN startEvent、userTask、exclusiveGateway、endEvent 和 sequenceFlow。业务上下文以受控前缀变量写入 Flowable；条件表达式只由后端根据枚举生成并转义，不接受客户端表达式文本。

替代方案是直接保存 BPMN XML，但这会把 UI 编辑器、安全校验和运行时表达式都绑定到第三方格式，也允许客户端绕过业务能力边界。

### 3. 草稿和发布版本继续由两张定义表负责

`am_approval_workflow_definition.graph_json` 保存当前可编辑草稿；`am_approval_workflow_definition_version.graph_json` 保存发布时不可变快照和 Flowable deployment/process definition 映射。实例继续绑定启动时版本，后续草稿编辑和发布不影响运行实例。

版本列表通过 `GET /api/v1/approval-workflow-definitions/{id}/versions` 提供，规模随发布增长，使用 cursor 分页。其他 CRUD 和 custom method 延续现有资源路径及 `api-contract`。

### 4. Flowable 是审批运行时唯一真相源，审批模块只暴露项目视图

删除项目任务、候选人和意见实体/Repository。`ApprovalProcessEngine` 扩展为项目所需的窄端口：查询活动/历史任务、校验候选人、完成任务、添加评论、查询评论和终止实例。`infrastructure/flowable` 负责把 Flowable 类型转换为审批模块 record，其他模块仍不得依赖 `org.flowable`。

审批意见使用 `TaskService.addComment(taskId, processInstanceId, actionType, message)`；同意、驳回、撤回和终止的动作类型由项目枚举控制。详情通过 Flowable 历史任务和评论组成项目响应，前端不直接读取 `ACT_*`。

### 5. 统一待办是一名办理人一行的可重建查询投影

新增 `am_unified_todo`：来源类型、来源任务 ID、业务类型/ID、标题、节点名称、办理人、状态、来源路径、完成时间和通用审计字段。唯一约束为 `(source_type, source_task_id, assignee_user_id)`；状态为 `PENDING`、`COMPLETED`、`CANCELLED`。

统一待办模块提供具体 `UnifiedTodoService`：

- 来源任务创建时按候选人幂等投递。
- 某办理人完成来源任务时，将其记录置为 `COMPLETED`，同一来源任务其他候选记录置为 `CANCELLED`。
- 来源任务撤回、终止或失效时，将全部未完成记录置为 `CANCELLED`。
- 列表只返回当前用户自己的 `PENDING` 或 `COMPLETED` 记录，并按 `createdAt DESC, id DESC` cursor 分页。

审批操作以统一待办数字 ID 定位投影，但必须再次从 Flowable 查询活动任务和 IdentityLink 校验当前用户；不能仅凭投影授权。其他业务后续通过统一待办 Service 投递，不直接操作其 Repository 或表。

替代方案是让每个来源实时聚合查询。它避免投影写入，但会让统一分页、稳定排序、来源故障隔离和已办语义难以实现。另一方案是一条待办保存候选人数组，但不利于按用户索引、状态分化和幂等投递。

### 6. 设计器采用独立工作区，不在弹窗中塞画布

流程定义列表保留状态、发布版本与启停操作；新建或编辑进入独立路由 `/approval/definitions/new/design` 或 `/approval/definitions/{id}/design`。工作区结构为：顶部保存/发布状态、左侧节点面板、中间 LogicFlow 画布、右侧流程/节点/连线属性面板。画布工具提供撤销、重做、缩放和适应画布；删除使用键盘和显式按钮，所有图标按钮有可访问名称。

界面继续采用 Element Plus、浅色高密度工作台和现有 token。加载使用骨架或画布遮罩；保存失败保留本地编辑状态；离开有未保存修改时提示；发布校验失败同时展示问题列表并定位节点或连线。

## API Owner / Decision / Impact / Gaps & Verification

**Owner**

- 通用 API 由 `openspec/specs/api-contract/spec.md` 负责。
- 审批定义、发布、实例和办理语义由 `approval-workflow` 规格负责。
- 跨业务待办投递、列表和完成语义由 `unified-todo` 规格负责。

**Decision**

- 其余遵循 `api-contract`。
- 定义图作为定义资源字段通过现有 create/patch API 保存；发布仍为 `:publish`。
- 新增定义版本 cursor 子资源和 `/api/v1/unified-todos` cursor 列表；审批动作路径仍属于审批资源，路径 ID 使用统一待办的项目数字 ID。

**Impact**

- 同步 Controller、HTTP record、审批/统一待办 Service 公开边界、前端 types/client 和测试。
- 复用现有 `CursorPageResponse`、`PageRequest`、ProblemDetail 和认证用户解析，无共享基础设施修改。
- LogicFlow 数据只停留在前端适配层，服务端 API 不暴露第三方类型。

**Gaps & verification**

- 无阻塞业务语义；来源路径只允许站内绝对路径并由服务端来源适配提供。
- 通过 Controller 测试核对完整 URL、cursor、数字 ID、权限与 ProblemDetail。

## Risks / Trade-offs

- [LogicFlow 包体和供应链增加] → 只引入 core、锁定精确 catalog 版本、保留 Apache-2.0 notice，并通过生产构建确认按路由拆包。
- [图结构合法但 BPMN 不可执行] → 发布前后端双重校验，并以真实 Flowable 部署和分支执行集成测试验证生成结果。
- [条件值造成表达式或 XML 注入] → 客户端不提交表达式；服务端只接受枚举运算符、受控键并统一转义条件值。
- [统一待办与来源状态短暂不一致] → 来源动作与投影更新放在同一数据库事务；办理前始终回查 Flowable，投影可由来源重建。
- [Flowable 历史清理影响长期详情] → 当前历史保留策略不主动清理；若未来引入清理策略，必须先另行设计归档审计快照，而不是在本变更保留重复表。
- [条件分支后没有可办理任务] → 启动和完成后检查活动任务；无活动任务则按 Flowable 实例结束状态收敛为通过，异常状态返回可排障错误并取消错误投影。

## Migration Plan

1. 当前审批迁移和模块尚未进入已发布基线，直接修改同一未提交迁移：将 `nodes_json` 改为 `graph_json`，删除任务、候选和意见表，新增统一待办表与索引。
2. 先落地图模型、BPMN 生成和 Flowable 窄端口测试，再替换审批实例服务的双写逻辑。
3. 新增统一待办 Service/API，并让审批任务创建、完成、撤回和终止维护投影。
4. 更新前端依赖、路由、列表、独立设计器和审批中心，最后执行前后端定向测试、构建和治理检查。
5. 若本地数据库已经执行旧的未发布迁移，开发者需重建本地数据库；不编写面向生产的兼容迁移，因为旧结构尚未发布。

回滚时恢复当前未提交审批实现和迁移，移除 LogicFlow 依赖及统一待办模块。Flowable 原生表和既有档案业务表不受影响。

## Open Questions

无。条件分支、指定候选用户、独立设计页面和统一待办投影的首版边界已由本设计确定。
