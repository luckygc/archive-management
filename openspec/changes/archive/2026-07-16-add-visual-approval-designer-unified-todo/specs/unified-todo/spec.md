## ADDED Requirements

### Requirement: 投递跨业务统一待办

系统 SHALL 允许来源业务通过统一待办模块按来源任务和办理人幂等投递待办。每条待办 SHALL 包含来源类型、来源任务 ID、业务类型、业务 ID、标题、可选节点名称、办理人、状态和站内来源路径；同一来源任务有多个候选人时 SHALL 为每名候选人各保存一条投影。

#### Scenario: 为多个候选人投递待办

- **WHEN** 审批来源任务产生三名实际候选人
- **THEN** 系统 SHALL 为三名候选人分别创建待办投影
- **AND** 三条投影 SHALL 共享相同来源类型和来源任务 ID

#### Scenario: 幂等重复投递

- **WHEN** 来源业务以相同来源类型、来源任务 ID 和办理人重复投递
- **THEN** 系统 SHALL NOT 创建重复待办
- **AND** 当前有效投影 SHALL 保持唯一

#### Scenario: 拒绝外部跳转地址

- **WHEN** 来源业务提交站外 URL 或非绝对站内路径
- **THEN** 系统 SHALL 拒绝投递
- **AND** 响应 SHALL 使用 Spring `ProblemDetail` 指出来源路径非法

### Requirement: 查询我的统一待办和已办

系统 SHALL 只向当前用户返回其本人的统一待办或已办记录。列表 SHALL 使用 cursor 分页，默认按 `createdAt DESC, id DESC` 稳定排序；待办列表 SHALL 只包含 `PENDING`，已办列表 SHALL 只包含当前用户实际完成的 `COMPLETED`，被其他候选人办理或来源取消的 `CANCELLED` SHALL 不进入已办列表。

#### Scenario: 查询我的待办

- **WHEN** 当前用户查询未完成统一待办
- **THEN** 系统 SHALL 返回当前用户状态为 `PENDING` 的跨业务来源记录
- **AND** 每条记录 SHALL 包含可用于进入来源业务的站内路径

#### Scenario: 查询我的已办

- **WHEN** 当前用户查询已办
- **THEN** 系统 SHALL 只返回由当前用户实际完成且状态为 `COMPLETED` 的记录
- **AND** 系统 SHALL NOT 返回因其他候选人办理而取消的记录

#### Scenario: 隔离其他用户待办

- **WHEN** 当前用户查询统一待办或已办
- **THEN** 系统 MUST NOT 返回其他办理人的投影记录

### Requirement: 完成或取消来源待办

系统 SHALL 允许来源业务在真实业务动作成功时完成或取消统一待办。某办理人完成共享候选来源任务时，系统 SHALL 将该办理人投影标记为 `COMPLETED`，将同一来源任务其他 `PENDING` 投影标记为 `CANCELLED`；来源任务撤回、终止或失效时，系统 SHALL 将全部 `PENDING` 投影标记为 `CANCELLED`。

#### Scenario: 一名候选人完成共享任务

- **WHEN** 多名候选人中的一名成功完成来源任务
- **THEN** 系统 SHALL 将其投影标记为 `COMPLETED`
- **AND** 系统 SHALL 将其他候选人的同来源待办标记为 `CANCELLED`

#### Scenario: 来源任务被撤回

- **WHEN** 来源业务撤回仍有待办的任务
- **THEN** 系统 SHALL 将该来源任务全部 `PENDING` 投影标记为 `CANCELLED`
- **AND** 这些投影 SHALL 不再出现在任何用户的待办列表

### Requirement: 来源业务负责最终授权

统一待办 SHALL 只是可重建查询投影，MUST NOT 作为来源任务存在性、状态或权限的最终依据。用户从统一待办进入或提交办理动作时，来源业务 SHALL 重新校验任务状态和当前用户权限；校验失败时 SHALL 拒绝动作并收敛失效投影。

#### Scenario: 投影存在但来源任务已结束

- **WHEN** 用户办理统一待办时来源任务已经结束
- **THEN** 来源业务 SHALL 拒绝重复办理
- **AND** 系统 SHALL 将失效的 `PENDING` 投影取消

#### Scenario: 投影存在但来源权限已变化

- **WHEN** 用户拥有统一待办但来源业务重新校验后已无办理权限
- **THEN** 来源业务 SHALL 拒绝办理
- **AND** 系统 MUST NOT 因统一待办记录存在而放行
