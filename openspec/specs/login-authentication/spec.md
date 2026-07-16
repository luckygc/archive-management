# login-authentication Specification

## Purpose

提供 PC 端账号密码登录、登录前安全验证、基于服务端会话的认证状态保持，以及当前主体查询和退出登录能力。

## Requirements

### Requirement: 登录前安全验证

系统 SHALL 在账号密码登录前要求客户端完成一次 CAP 工作量证明安全验证。

#### Scenario: 创建安全验证挑战

- **WHEN** 客户端请求 `POST /api/v1/cap-challenges`
- **THEN** 系统 SHALL 创建一条 `am_authentication_cap_challenge` 挑战记录
- **AND** 响应 SHALL 包含 `challenge`、`token` 和 `expires`
- **AND** `challenge` SHALL 包含挑战数量 `c`、挑战尺寸 `s` 和难度 `d`
- **AND** challenge 默认有效期 SHALL 为 10 分钟

#### Scenario: 安全验证难度固定

- **GIVEN** 系统存在任意登录名的失败状态
- **WHEN** 客户端请求 `POST /api/v1/cap-challenges`
- **THEN** 系统 SHALL 返回默认 CAP challenge 难度
- **AND** 系统 SHALL NOT 基于登录名、失败次数或锁定状态提高 CAP challenge 难度

#### Scenario: 安全验证令牌不绑定登录名

- **GIVEN** 客户端兑换得到有效 CAP token
- **WHEN** 客户端使用该 token 提交账号密码登录
- **THEN** 系统 SHALL 只校验 CAP token 自身是否有效且未被消费
- **AND** 系统 SHALL NOT 校验 CAP token 与本次登录名是否一致

#### Scenario: 兑换安全验证令牌

- **GIVEN** 客户端持有未过期的 challenge token
- **WHEN** 客户端请求 `POST /api/v1/cap-tokens` 并提交 token 与完整 solutions
- **THEN** 系统 SHALL 校验每个 solution 是否匹配 challenge 规则
- **AND** 系统 SHALL 删除已提交的 challenge token
- **AND** 校验成功时 SHALL 创建一条 `am_authentication_cap_token` 令牌记录
- **AND** 响应 SHALL 包含 `success: true`、一次性登录令牌 `token` 和 `expires`
- **AND** 令牌默认有效期 SHALL 为 20 分钟

#### Scenario: 兑换安全验证失败

- **WHEN** 客户端提交空请求体、空 token、缺失 solutions、已过期 token 或错误 solutions
- **THEN** 系统 SHALL 返回 `success: false`
- **AND** 响应 SHALL 包含失败原因 `message`
- **AND** 系统 SHALL 删除本次提交的 challenge token

#### Scenario: 校验安全验证令牌

- **WHEN** 客户端请求 `POST /api/v1/cap-tokens:validate`
- **THEN** 系统 SHALL 按提交的 token 返回 `{ "success": true }` 或 `{ "success": false }`
- **AND** 当 `keepToken` 为 `true` 时，系统 SHALL 只检查令牌有效性，不消费令牌
- **AND** 当 `keepToken` 不为 `true` 时，系统 SHALL 消费一次性令牌

#### Scenario: CAP widget 请求适配

- **WHEN** CAP widget 按内部协议请求 `challenge`、`redeem` 或 `validateToken`
- **THEN** 浏览器端 SHALL 通过 CAP 自定义 fetch 改写到 `/api/v1/cap-challenges`、`/api/v1/cap-tokens` 或 `/api/v1/cap-tokens:validate`
- **AND** 服务端 SHALL NOT 暴露 `/api/v1/cap/challenge`、`/api/v1/cap/redeem` 或 `/api/v1/cap/validateToken`

### Requirement: 账号密码登录

系统 SHALL 使用 Spring Security 表单登录处理账号密码认证。

#### Scenario: 登录请求格式

- **WHEN** 客户端提交登录请求
- **THEN** 请求 SHALL 使用 `POST /api/v1/login-sessions`
- **AND** 请求体 SHALL 使用 `application/x-www-form-urlencoded`
- **AND** 请求参数 SHALL 包含 `username`、`password` 和 `powToken`

#### Scenario: 登录前消费安全验证令牌

- **GIVEN** 客户端提交 `POST /api/v1/login-sessions`
- **WHEN** `powToken` 为空、格式错误、已过期或不存在
- **THEN** 系统 SHALL 拒绝登录
- **AND** 响应状态 SHALL 为 `401 Unauthorized`
- **AND** 响应体 SHALL 为文本错误信息

#### Scenario: 登录成功

- **GIVEN** 客户端提交有效的 `powToken`
- **AND** 用户名和密码认证通过
- **WHEN** 系统处理登录请求
- **THEN** 系统 SHALL 保存 Spring Security 上下文到服务端会话
- **AND** 响应状态 SHALL 为 `200 OK`
- **AND** 响应体 SHALL 为创建出的登录会话资源
- **AND** 登录会话资源 SHALL 包含 `sessionId`、`username`、`displayName`、`roles`、`client`、`request` 和会话时间信息

#### Scenario: 登录失败

- **GIVEN** 客户端提交有效的 `powToken`
- **WHEN** 用户名或密码认证失败
- **THEN** 系统 SHALL 返回 `401 Unauthorized`
- **AND** 响应体 SHALL 为文本 `账号或密码错误`
- **AND** 已提交的 `powToken` SHALL 被消费

### Requirement: 登录失败限制

系统 SHALL 按登录名维护登录失败状态，并在连续失败达到阈值后临时禁止该登录名继续登录；CAP 仅用于提高机器暴力破解成本，不承载账号维度风控。

#### Scenario: 记录登录失败状态

- **WHEN** 用户通过 `POST /api/v1/login-sessions` 登录失败
- **THEN** 系统 SHALL 按提交的登录名记录失败状态
- **AND** 登录成功后系统 SHALL 清除该登录名的失败风险状态

#### Scenario: 并发记录登录失败状态

- **WHEN** 同一登录名的多次失败请求并发到达
- **THEN** 系统 SHALL 对该登录名的失败状态执行原子创建或加锁更新
- **AND** 系统 SHALL NOT 因并发插入导致失败次数丢失或请求异常

#### Scenario: 连续失败后临时禁止登录

- **GIVEN** 某登录名在失败窗口内连续失败达到阈值
- **WHEN** 客户端继续使用该登录名请求 `POST /api/v1/login-sessions`
- **THEN** 系统 SHALL 拒绝登录
- **AND** 响应状态 SHALL 为 `429 Too Many Requests`
- **AND** 响应体 SHALL 包含可再次登录时间

#### Scenario: 登录禁止时间指数递增并封顶

- **GIVEN** 某登录名多次达到登录失败阈值
- **WHEN** 系统计算下一次登录禁止时间
- **THEN** 系统 SHALL 按历史锁定次数指数递增禁止时长
- **AND** 禁止时长 SHALL NOT 超过 30 分钟

#### Scenario: 管理员重置登录失败状态

- **GIVEN** 管理员拥有登录会话管理权限
- **WHEN** 管理员请求 `POST /api/v1/login-failure-limits/{username}:reset`
- **THEN** 系统 SHALL 清除 `{username}` 对应的登录失败状态
- **AND** 响应状态 SHALL 为 `204 No Content`

#### Scenario: 无权限禁止重置登录失败状态

- **WHEN** 未拥有登录会话管理权限的用户请求 `POST /api/v1/login-failure-limits/{username}:reset`
- **THEN** 系统 SHALL 拒绝请求
- **AND** 响应状态 SHALL 为 `403 Forbidden`

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

### Requirement: 用户认证数据

系统 SHALL 从本地数据库加载用户身份和角色。

#### Scenario: 加载启用用户

- **WHEN** 系统按用户名加载用户
- **THEN** 系统 SHALL 从 `am_authentication_user` 读取用户账号、密码密文、显示名称和启用状态
- **AND** 系统 SHALL 只允许启用用户通过认证

#### Scenario: 加载用户角色

- **WHEN** 系统构造登录用户权限
- **THEN** 系统 SHALL 从 `am_authorization_user_role_rel` 和 `am_authorization_role` 读取用户角色名称
- **AND** 写入 Spring Security 权限时 SHALL 自动添加 `ROLE_` 前缀
- **AND** 对外返回当前用户时 SHALL 去除 `ROLE_` 前缀

#### Scenario: 显式初始化管理员账号

- **GIVEN** 配置 `archive.authentication.bootstrap-admin.enabled` 为 `true`
- **AND** 配置提供非空管理员账号、密码和显示名称
- **WHEN** 应用启动且管理员账号不存在
- **THEN** 系统 SHALL 创建该管理员用户
- **AND** 系统 SHALL 使用 `PasswordEncoder` 保存密码密文
- **AND** 该管理员用户 SHALL 具有 `系统管理员` 和 `系统监控` 角色

#### Scenario: 不创建固定默认管理员

- **GIVEN** 配置 `archive.authentication.bootstrap-admin.enabled` 不为 `true`
- **WHEN** 应用启动
- **THEN** 系统 SHALL NOT 创建固定账号密码的默认管理员

### Requirement: 用户管理

认证用户管理 SHALL 支持用户所属部门。

#### Scenario: 创建或更新用户所属部门

- **WHEN** 管理员为用户设置所属部门
- **THEN** 系统 SHALL 校验部门存在且启用
- **AND** 用户可以没有所属部门

#### Scenario: 返回用户所属部门

- **WHEN** 客户端查询用户列表或详情
- **THEN** 响应 SHALL 包含 `departmentId`
- **AND** 响应 SHALL 包含 `departmentCode` 和 `departmentName` 供界面展示

### Requirement: 当前主体查询

系统 SHALL 提供当前主体查询接口。

#### Scenario: 查询当前用户

- **GIVEN** 客户端已登录
- **WHEN** 客户端请求 `GET /api/v1/me`
- **THEN** 系统 SHALL 返回当前主体 JSON
- **AND** 当前主体 JSON SHALL 包含 `sessionId`、`username`、`displayName` 和 `roles`

#### Scenario: 未登录访问 API

- **GIVEN** 客户端未登录
- **WHEN** 客户端访问受保护的 `/api/**` 接口
- **THEN** 系统 SHALL 返回 `401 Unauthorized`

### Requirement: 退出登录

系统 SHALL 提供退出登录接口并清理服务端认证状态。

#### Scenario: 退出当前会话

- **GIVEN** 客户端已登录
- **WHEN** 客户端请求 `DELETE /api/v1/login-sessions/{session}` 且 `{session}` 为当前会话 ID
- **THEN** 系统 SHALL 清理当前 SecurityContext
- **AND** 系统 SHALL 使当前 HTTP session 失效
- **AND** 响应状态 SHALL 为 `204 No Content`

### Requirement: PC 端登录集成

PC 端 SHALL 集成账号密码登录、安全验证、认证状态初始化和退出登录。

#### Scenario: 登录页提交

- **GIVEN** 用户在 PC 端登录页输入账号和密码
- **WHEN** 用户未完成安全验证就提交登录
- **THEN** PC 端 SHALL 阻止提交
- **AND** PC 端 SHALL 提示用户先完成安全验证

#### Scenario: 登录页认证成功

- **GIVEN** 用户完成安全验证并提交正确账号密码
- **WHEN** 登录接口返回当前用户 JSON
- **THEN** PC 端 SHALL 写入 session store 的当前用户
- **AND** PC 端 SHALL 将认证状态标记为已初始化
- **AND** PC 端 SHALL 跳转到 redirect 查询参数指定路径或首页

#### Scenario: 登录页认证失败

- **WHEN** 登录请求失败
- **THEN** PC 端 SHALL 展示后端返回的错误信息
- **AND** PC 端 SHALL 重置 CAP 安全验证组件
- **AND** PC 端 SHALL 要求用户重新完成安全验证

#### Scenario: 前端认证请求携带会话凭证

- **WHEN** PC 端调用认证接口或业务 API
- **THEN** 请求 SHALL 携带浏览器会话凭证
