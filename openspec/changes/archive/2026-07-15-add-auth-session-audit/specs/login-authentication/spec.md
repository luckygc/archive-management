## ADDED Requirements

### Requirement: 登录会话管理

系统 SHALL 提供当前有效登录会话查询和管理员踢下线能力，并以 Spring Session 作为在线状态真相源。

#### Scenario: 查询登录会话

- **GIVEN** 管理员已登录
- **WHEN** 客户端请求 `GET /api/v1/login-sessions`
- **THEN** 系统 SHALL 返回当前仍有效的 Spring Session 登录会话列表
- **AND** 响应 SHALL 使用 cursor 分页响应对象
- **AND** 当请求参数 `requestTotal=true` 且未提交 `cursor` 时，响应 SHALL 返回与本次筛选条件一致的 `total`
- **AND** 每条登录会话 SHALL 包含 `sessionId`、`username`、`displayName`、`roles`、`creationTime`、`lastAccessTime`、`expiresAt`、`current`、`client` 和 `request` 字段
- **AND** `client` SHALL 包含原始 `userAgent`、浏览器、操作系统和设备类型摘要
- **AND** `request` SHALL 包含登录时采集的 IP、Host、Forwarded、X-Forwarded-For 和 X-Real-IP 信息

#### Scenario: 踢下线登录会话

- **GIVEN** 管理员已登录
- **AND** 目标登录会话存在且不是当前管理员自己的会话
- **WHEN** 客户端请求 `DELETE /api/v1/login-sessions/{session}`
- **THEN** 系统 SHALL 在认证审计日志中记录 `kickout` 事件
- **AND** 审计日志 SHALL 记录操作人、目标用户名、目标 session ID 和目标会话客户端快照
- **AND** 系统 SHALL 删除目标 Spring Session
- **AND** 响应状态 SHALL 为 `204 No Content`

#### Scenario: 禁止踢出当前会话

- **GIVEN** 管理员已登录
- **WHEN** 客户端请求 `DELETE /api/v1/login-sessions/{session}` 且 `{session}` 是当前请求会话
- **THEN** 系统 SHALL 拒绝请求
- **AND** 响应 SHALL 使用 ProblemDetail 错误

### Requirement: 认证审计日志

系统 SHALL 记录登录成功、登录失败、主动退出和管理员踢下线认证事件，并将认证审计日志作为长期审计流水保留。

#### Scenario: 记录登录成功

- **WHEN** 用户通过 `POST /api/v1/login-sessions` 成功登录
- **THEN** 系统 SHALL 写入一条 `login_success` 认证审计日志
- **AND** 日志 SHALL 包含用户 ID、用户名、显示名称、session ID、请求 IP、Host、Forwarded、X-Forwarded-For、X-Real-IP、原始 User-Agent 和客户端摘要
- **AND** 系统 SHALL 将登录时客户端上下文保存到当前 Spring Session 属性

#### Scenario: 记录登录失败

- **WHEN** 用户通过 `POST /api/v1/login-sessions` 登录失败
- **THEN** 系统 SHALL 写入一条 `login_failure` 认证审计日志
- **AND** 日志 SHALL 包含提交的用户名、失败原因、请求 IP、Host、Forwarded、X-Forwarded-For、X-Real-IP、原始 User-Agent 和客户端摘要
- **AND** 系统 SHALL NOT 创建登录会话

#### Scenario: 记录主动退出

- **GIVEN** 用户已登录
- **WHEN** 客户端请求 `DELETE /api/v1/login-sessions/{session}` 且 `{session}` 是当前请求会话
- **THEN** 系统 SHALL 写入一条 `logout` 认证审计日志
- **AND** 日志 SHALL 包含当前用户、当前 session ID 和客户端上下文
- **AND** 系统 SHALL 使当前 HTTP session 失效

#### Scenario: 查询认证审计日志

- **GIVEN** 管理员已登录
- **WHEN** 客户端请求 `GET /api/v1/authentication-events`
- **THEN** 系统 SHALL 返回认证审计日志列表
- **AND** 响应 SHALL 使用 cursor 分页响应对象
- **AND** 当请求参数 `requestTotal=true` 且未提交 `cursor` 时，响应 SHALL 返回与本次筛选条件一致的 `total`
- **AND** 请求 SHALL 支持按事件类型、用户名、关键字和时间范围筛选
- **AND** 日志 SHALL 按发生时间倒序、ID 倒序稳定排序

### Requirement: 登录失败限制

系统 SHALL 对同一登录名在时间窗口内连续登录失败进行状态记录，达到阈值后临时禁止该登录名继续登录；CAP 难度 SHALL 保持固定，不按登录名失败状态动态提高。

#### Scenario: 失败次数触发临时禁止登录

- **GIVEN** 同一登录名在失败窗口内连续失败达到阈值
- **WHEN** 客户端继续使用该登录名请求 `POST /api/v1/login-sessions`
- **THEN** 系统 SHALL 返回 `429 Too Many Requests`
- **AND** 响应体 SHALL 包含可再次登录时间
- **AND** 后续 CAP challenge SHALL 继续使用默认难度

#### Scenario: 登录禁止时间指数递增并封顶

- **GIVEN** 同一登录名多次达到失败阈值
- **WHEN** 系统计算下一次登录禁止时间
- **THEN** 登录禁止时间 SHALL 按历史锁定次数指数递增
- **AND** 登录禁止时间 SHALL NOT 超过 30 分钟

#### Scenario: 管理员重置登录失败状态

- **GIVEN** 管理员拥有登录会话管理权限
- **WHEN** 管理员请求 `POST /api/v1/login-failure-limits/{username}:reset`
- **THEN** 系统 SHALL 清除该登录名的失败状态
- **AND** 响应状态 SHALL 为 `204 No Content`

#### Scenario: 登录成功清除失败限制

- **GIVEN** 登录名存在历史失败限制状态但当前未处于锁定期
- **WHEN** 用户通过 `POST /api/v1/login-sessions` 成功登录
- **THEN** 系统 SHALL 清除该登录名的失败风险状态

### Requirement: 短生命周期数据统一清理

系统 SHALL 通过统一清理接口和 Quartz 作业清理短生命周期状态数据。

#### Scenario: 清理 CAP 与登录失败限制过期状态

- **GIVEN** 系统存在过期 CAP challenge、过期 CAP token 和已过清理时间的登录失败限制状态
- **WHEN** Quartz 触发短生命周期数据清理作业
- **THEN** 系统 SHALL 调用所有短生命周期数据清理实现
- **AND** CAP challenge、CAP token 和登录失败限制过期状态 SHALL 被删除
- **AND** 认证审计日志 SHALL NOT 被删除

#### Scenario: CAP 查询流程不顺手清理

- **WHEN** 客户端创建、兑换或校验 CAP
- **THEN** 系统 SHALL NOT 在该请求流程中顺手清理全部过期 CAP 数据
