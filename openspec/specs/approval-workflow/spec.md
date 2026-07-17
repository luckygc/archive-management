# approval-workflow Specification

## Purpose
规定通用审批流程从可视化流程图设计、结构校验、不可变版本发布，到 Flowable 运行时执行、统一待办办理、撤回终止和历史查询的完整业务语义，并约束候选人、受控条件表达式、服务端授权以及流程数据真相源边界。
## Requirements
### Requirement: 管理审批流定义

系统 SHALL 允许具备审批流管理权限的用户创建、修改、停用和查询项目自有审批流定义。审批流定义 SHALL 至少包含编码、名称、业务类型、启用状态和流程图；流程图 SHALL 包含节点、连线、节点业务属性和画布布局。

#### Scenario: 创建审批流定义

- **WHEN** 管理员提交合法的审批流定义基础信息和草稿流程图
- **THEN** 系统 SHALL 保存定义草稿
- **AND** 响应 SHALL 返回定义 ID、编码、名称、当前草稿版本和流程图

#### Scenario: 拒绝重复定义编码

- **WHEN** 管理员提交的审批流定义编码已被未删除定义使用
- **THEN** 系统 SHALL 拒绝保存
- **AND** 响应 SHALL 使用 Spring `ProblemDetail` 错误格式指出编码已存在

#### Scenario: 停用审批流定义

- **WHEN** 管理员停用审批流定义
- **THEN** 系统 SHALL 阻止该定义后续发起新的流程实例
- **AND** 系统 MUST NOT 影响已经启动的流程实例继续办理

### Requirement: 发布审批流定义

系统 SHALL 将项目自有审批流程图转换并发布为 Flowable BPMN process definition。每次发布 SHALL 生成新的项目定义版本，并记录冻结流程图、Flowable deployment ID、process definition ID 和 process definition key。

#### Scenario: 发布有效定义

- **WHEN** 管理员发布通过完整结构校验且包含至少一个审批节点的流程图
- **THEN** 系统 SHALL 生成 BPMN XML并部署到 Flowable
- **AND** 系统 SHALL 保存不可变项目定义版本与 Flowable 定义标识的映射

#### Scenario: 拒绝发布无审批节点定义

- **WHEN** 管理员发布不包含审批节点的流程图
- **THEN** 系统 SHALL 拒绝发布
- **AND** 响应 SHALL 指出审批节点不能为空

### Requirement: 发起审批流程实例

系统 SHALL 允许业务模块通过审批流模块发起审批流程实例，并传入业务类型、业务 ID、标题、发起人和键值型业务上下文。流程实例 SHALL 绑定启动时的审批定义版本；业务上下文键 SHALL 经过服务端校验并作为受控 Flowable 变量使用。

#### Scenario: 发起启用定义的流程

- **WHEN** 业务模块使用已启用且已发布的审批流定义发起流程
- **THEN** 系统 SHALL 启动 Flowable 流程实例
- **AND** 系统 SHALL 保存项目审批实例与业务对象、定义版本和 Flowable process instance ID 的绑定
- **AND** 系统 SHALL 为首个到达的审批任务投递统一待办

#### Scenario: 拒绝使用未发布定义发起流程

- **WHEN** 业务模块使用未发布定义发起流程
- **THEN** 系统 SHALL 拒绝发起
- **AND** 响应 SHALL 指出审批流定义尚未发布

#### Scenario: 运行中实例使用启动版本

- **WHEN** 审批流定义在流程实例启动后再次发布新版本
- **THEN** 已启动实例 SHALL 继续使用启动时绑定的定义版本
- **AND** 系统 MUST NOT 自动迁移运行中实例到新版本

### Requirement: 办理审批任务

系统 SHALL 允许当前统一待办的办理人办理仍处于活动状态且 Flowable IdentityLink 仍包含该用户的审批任务。办理动作 SHALL 至少支持同意和驳回；审批意见、办理人和办理时间 SHALL 写入或读取 Flowable 任务评论与历史，统一待办只保存查询投影状态。

#### Scenario: 同意审批任务

- **WHEN** 当前用户拥有对应待办且仍是 Flowable 活动任务候选人并提交同意动作
- **THEN** 系统 SHALL 记录同意意见并完成 Flowable 当前任务
- **AND** 系统 SHALL 将当前用户投影标记为已办并取消同一来源任务的其他候选人投影
- **AND** 流程继续到下一审批任务时系统 SHALL 投递新的统一待办

#### Scenario: 拒绝非候选人办理

- **WHEN** 当前用户没有对应待办、不是 Flowable 当前候选人或任务已经完成
- **THEN** 系统 SHALL 拒绝办理
- **AND** 响应 SHALL 使用 Spring `ProblemDetail` 错误格式指出无权办理或任务不可办理

#### Scenario: 驳回审批任务

- **WHEN** 当前用户拥有对应待办且仍是 Flowable 活动任务候选人并提交驳回动作
- **THEN** 系统 SHALL 记录驳回意见并终止 Flowable 流程实例
- **AND** 系统 SHALL 保存项目实例最终状态并关闭该来源任务的统一待办投影

### Requirement: 撤回或终止审批流程实例

系统 SHALL 允许发起人或具备管理权限的用户在规则允许时撤回或终止审批流程实例。撤回或终止 SHALL 同步更新项目实例最终状态、记录 Flowable 评论并取消该实例当前活动任务的统一待办。

#### Scenario: 发起人撤回流程

- **WHEN** 发起人在流程未结束时提交撤回动作
- **THEN** 系统 SHALL 记录撤回意见并终止 Flowable 流程实例
- **AND** 系统 SHALL 将项目审批实例标记为已撤回
- **AND** 系统 SHALL 取消该实例当前活动任务的统一待办

#### Scenario: 拒绝撤回已结束流程

- **WHEN** 发起人撤回已通过、已驳回或已终止的流程实例
- **THEN** 系统 SHALL 拒绝撤回
- **AND** 响应 SHALL 指出流程实例已经结束

### Requirement: 查询审批历史和进度

系统 SHALL 支持按项目审批实例查询审批进度、意见和历史节点。服务端 SHALL 通过审批模块的 Flowable 适配端口查询活动/历史任务和评论并转换为项目响应；前端和其他业务模块 MUST NOT 直接读取 Flowable API、实体或原生表。

#### Scenario: 查询流程进度

- **WHEN** 用户查询有权限查看的审批流程实例
- **THEN** 系统 SHALL 返回流程定义版本、当前节点、已完成节点、待办任务和审批意见
- **AND** 审批意见 SHALL 保留动作类型、办理人、内容和时间

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

### Requirement: 可视化设计审批流程

系统 SHALL 为具备审批流管理权限的用户提供独立的可视化流程设计页面。设计器 SHALL 支持开始、审批、排他条件分支和结束节点的添加或内置、拖拽、连线、选择、删除、撤销、重做、缩放、适应画布和属性编辑，并 SHALL 保留节点画布位置。

#### Scenario: 新建可视化流程

- **WHEN** 管理员新建审批流定义
- **THEN** 系统 SHALL 打开独立流程设计页面
- **AND** 画布 SHALL 自动包含唯一开始节点和唯一结束节点
- **AND** 管理员 SHALL 能添加审批和条件分支节点并通过连线组成流程

#### Scenario: 编辑节点和连线属性

- **WHEN** 管理员在画布中选择审批节点或条件分支出线
- **THEN** 右侧属性区域 SHALL 展示对应业务属性
- **AND** 修改属性 SHALL 同步更新画布和待保存草稿

#### Scenario: 保留未保存修改

- **WHEN** 保存草稿失败
- **THEN** 设计器 SHALL 保留当前本地流程图和属性修改
- **AND** 页面 SHALL 显示可执行的失败信息并允许重新保存

#### Scenario: 提示离开未保存草稿

- **WHEN** 管理员在流程图已修改但尚未保存时离开设计页面
- **THEN** 系统 SHALL 提示存在未保存修改
- **AND** 管理员 SHALL 能取消离开并继续编辑

### Requirement: 校验可执行流程图

系统 SHALL 在保存时校验基础字段和图数据格式，并 SHALL 在发布时校验完整可执行结构。可执行流程图 SHALL 恰好包含一个开始和一个结束节点，所有节点编码唯一，所有节点均从开始可达且可到达结束，流程无环，审批节点候选用户有效；排他网关 SHALL 至少有两条出线、恰好一条默认分支，其他出线 SHALL 使用受控条件键、运算符和值。

#### Scenario: 拒绝发布断开的流程图

- **WHEN** 管理员发布包含孤立节点或无法到达结束节点的流程图
- **THEN** 系统 SHALL 拒绝发布
- **AND** 响应 SHALL 指出相关节点或连线

#### Scenario: 拒绝非法条件分支

- **WHEN** 排他网关缺少默认分支、存在多个默认分支或非默认出线条件不完整
- **THEN** 系统 SHALL 拒绝发布
- **AND** 系统 SHALL NOT 把客户端文本作为 Flowable 表达式执行

#### Scenario: 定位设计器校验问题

- **WHEN** 发布前校验发现图结构或属性问题
- **THEN** 设计器 SHALL 展示问题列表
- **AND** 管理员选择问题后 SHALL 定位对应节点或连线

### Requirement: 执行受控条件分支

系统 SHALL 只允许条件分支使用业务上下文中的受控字段和 `EQUALS`、`NOT_EQUALS`、`IN` 运算符。服务端 SHALL 根据结构化条件生成 Flowable 条件表达式，并 MUST NOT 接受客户端提交的脚本、SpEL、UEL 或 BPMN XML。

#### Scenario: 命中条件分支

- **WHEN** 流程到达排他网关且业务上下文字段满足一条非默认分支
- **THEN** Flowable SHALL 沿命中的分支继续执行
- **AND** 系统 SHALL 为该分支到达的审批任务投递统一待办

#### Scenario: 使用默认分支

- **WHEN** 流程到达排他网关且业务上下文不满足任何非默认分支
- **THEN** Flowable SHALL 沿唯一默认分支继续执行

#### Scenario: 拒绝非法上下文键

- **WHEN** 发起请求提交不符合受控编码格式的业务上下文键
- **THEN** 系统 SHALL 拒绝发起
- **AND** 响应 SHALL 使用 Spring `ProblemDetail` 指出非法字段

### Requirement: 查询审批定义版本

系统 SHALL 允许具备审批流管理权限的用户按定义查询已发布版本，结果 SHALL 使用 cursor 分页并按版本号和 ID 倒序返回。版本详情 SHALL 包含冻结流程图和发布时间，但 MUST NOT 允许修改。

#### Scenario: 查看已发布版本

- **WHEN** 管理员查询某审批定义的版本列表
- **THEN** 系统 SHALL 返回该定义的不可变发布版本
- **AND** 每个版本 SHALL 保留发布时的节点、连线、业务属性和画布布局
