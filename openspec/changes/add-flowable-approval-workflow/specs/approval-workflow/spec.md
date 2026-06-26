## ADDED Requirements

### Requirement: 管理审批流定义

系统 SHALL 允许具备审批流管理权限的用户创建、修改、停用和查询项目自有审批流定义。审批流定义 SHALL 至少包含编码、名称、业务类型、启用状态、节点顺序、候选人策略和允许动作。

#### Scenario: 创建审批流定义

- **WHEN** 管理员提交合法的审批流定义
- **THEN** 系统 SHALL 保存定义草稿
- **AND** 响应 SHALL 返回定义 ID、编码、名称和当前草稿版本

#### Scenario: 拒绝重复定义编码

- **WHEN** 管理员提交的审批流定义编码已被未删除定义使用
- **THEN** 系统 SHALL 拒绝保存
- **AND** 响应 SHALL 使用 Spring `ProblemDetail` 错误格式指出编码已存在

#### Scenario: 停用审批流定义

- **WHEN** 管理员停用审批流定义
- **THEN** 系统 SHALL 阻止该定义后续发起新的流程实例
- **AND** 系统 MUST NOT 影响已经启动的流程实例继续办理

### Requirement: 发布审批流定义

系统 SHALL 将项目自有审批流定义发布为 Flowable BPMN process definition。每次发布 SHALL 生成新的项目定义版本，并记录 Flowable deployment ID、process definition ID 和 process definition key。

#### Scenario: 发布有效定义

- **WHEN** 管理员发布包含至少一个审批节点的有效定义
- **THEN** 系统 SHALL 生成 BPMN XML 并部署到 Flowable
- **AND** 系统 SHALL 保存项目定义版本与 Flowable 定义标识的映射

#### Scenario: 拒绝发布无审批节点定义

- **WHEN** 管理员发布不包含审批节点的定义
- **THEN** 系统 SHALL 拒绝发布
- **AND** 响应 SHALL 指出审批节点不能为空

### Requirement: 发起审批流程实例

系统 SHALL 允许业务模块通过审批流模块发起审批流程实例，并传入业务类型、业务 ID、标题、发起人和业务上下文。流程实例 SHALL 绑定启动时的审批定义版本。

#### Scenario: 发起启用定义的流程

- **WHEN** 业务模块使用已启用且已发布的审批流定义发起流程
- **THEN** 系统 SHALL 启动 Flowable 流程实例
- **AND** 系统 SHALL 保存项目审批实例与业务对象、定义版本和 Flowable process instance ID 的绑定

#### Scenario: 拒绝使用未发布定义发起流程

- **WHEN** 业务模块使用未发布的审批流定义发起流程
- **THEN** 系统 SHALL 拒绝发起
- **AND** 响应 SHALL 指出审批流定义尚未发布

#### Scenario: 运行中实例使用启动版本

- **WHEN** 审批流定义在流程实例启动后再次发布新版本
- **THEN** 已启动实例 SHALL 继续使用启动时绑定的定义版本
- **AND** 系统 MUST NOT 自动迁移运行中实例到新版本

### Requirement: 解析和快照审批待办

系统 SHALL 在审批任务创建时解析候选人策略，并保存项目自有待办快照。待办快照 SHALL 包含任务 ID、实例 ID、节点编码、节点名称、候选用户、办理人、任务状态、创建时间和完成时间。

#### Scenario: 创建待办快照

- **WHEN** Flowable 进入审批用户任务
- **THEN** 系统 SHALL 根据节点候选策略解析候选用户
- **AND** 系统 SHALL 保存待办快照用于审批中心列表查询

#### Scenario: 查询我的待办

- **WHEN** 当前用户查询我的待办
- **THEN** 系统 SHALL 返回候选用户包含当前用户且状态为待办的任务
- **AND** 系统 SHALL 按任务创建时间倒序返回结果

### Requirement: 办理审批任务

系统 SHALL 允许当前待办候选人办理审批任务。办理动作 SHALL 至少支持同意和驳回，并保存审批意见、办理人和办理时间。

#### Scenario: 同意审批任务

- **WHEN** 当前用户是任务候选人并提交同意动作
- **THEN** 系统 SHALL 完成 Flowable 当前任务
- **AND** 系统 SHALL 保存审批意见并将项目待办快照标记为已完成

#### Scenario: 拒绝非候选人办理

- **WHEN** 当前用户不是任务候选人或任务已完成
- **THEN** 系统 SHALL 拒绝办理
- **AND** 响应 SHALL 使用 Spring `ProblemDetail` 错误格式指出无权办理或任务不可办理

#### Scenario: 驳回审批任务

- **WHEN** 当前用户是任务候选人并提交驳回动作
- **THEN** 系统 SHALL 结束或退回流程实例
- **AND** 系统 SHALL 保存驳回意见和最终流程状态

### Requirement: 撤回或终止审批流程实例

系统 SHALL 允许发起人或具备管理权限的用户在规则允许时撤回或终止审批流程实例。撤回或终止 SHALL 同步更新项目实例状态和未完成待办状态。

#### Scenario: 发起人撤回流程

- **WHEN** 发起人在流程未结束时提交撤回动作
- **THEN** 系统 SHALL 终止 Flowable 流程实例
- **AND** 系统 SHALL 将项目审批实例标记为已撤回
- **AND** 系统 SHALL 关闭该实例下未完成待办

#### Scenario: 拒绝撤回已结束流程

- **WHEN** 发起人撤回已通过、已驳回或已终止的流程实例
- **THEN** 系统 SHALL 拒绝撤回
- **AND** 响应 SHALL 指出流程实例已经结束

### Requirement: 查询审批历史和进度

系统 SHALL 支持按流程实例查询审批进度、审批意见和历史节点。历史展示 SHALL 使用项目自有业务快照，不要求前端直接读取 Flowable 原生历史表。

#### Scenario: 查询流程进度

- **WHEN** 用户查询有权限查看的审批流程实例
- **THEN** 系统 SHALL 返回流程定义版本、当前节点、已完成节点、待办任务和审批意见

#### Scenario: 隐藏无权限流程历史

- **WHEN** 用户查询无权限访问的审批流程实例
- **THEN** 系统 SHALL 拒绝查询
- **AND** 响应 SHALL 使用 Spring `ProblemDetail` 错误格式指出无权访问

### Requirement: 隔离 Flowable 原生 API 和表结构

系统 SHALL 将 Flowable 原生服务调用限制在基础设施适配层和审批流模块内部。业务模块 MUST NOT 直接依赖 `org.flowable` API、Flowable 原生实体或 Flowable 原生表结构。

#### Scenario: 业务模块接入审批

- **WHEN** 档案业务需要发起或查询审批
- **THEN** 业务模块 SHALL 调用审批流模块公开服务
- **AND** 业务模块 MUST NOT 直接注入 Flowable `RuntimeService`、`TaskService` 或 `RepositoryService`

#### Scenario: 架构边界验证

- **WHEN** 执行后端架构测试
- **THEN** 测试 SHALL 阻止非审批模块和非基础设施适配层直接依赖 `org.flowable`
