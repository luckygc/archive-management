# login-authentication Specification

## Purpose

提供 PC 端账号密码登录、登录前安全验证、用户可选的 TOTP 二次验证、基于服务端会话的认证状态保持，以及当前主体查询和退出登录能力。
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

系统 SHALL 使用 Spring Security 表单登录处理账号密码认证，并在密码认证成功后根据用户选择进入直接登录或 TOTP 二次验证分支。

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
- **AND** 该用户没有有效 TOTP 凭据
- **WHEN** 系统处理登录请求
- **THEN** 系统 SHALL 保存 Spring Security 上下文到服务端会话
- **AND** 响应状态 SHALL 为 `200 OK`
- **AND** 响应体 SHALL 为创建出的登录会话资源
- **AND** 登录会话资源 SHALL 包含 `sessionId`、`username`、`displayName`、`roles`、`client`、`request` 和会话时间信息

#### Scenario: 已启用 TOTP 时进入二次验证

- **GIVEN** 客户端提交有效的 `powToken`
- **AND** 用户名和密码认证通过
- **AND** 该用户存在有效 TOTP 凭据
- **WHEN** 系统处理登录请求
- **THEN** 系统 SHALL 创建一条最多 5 分钟有效的 TOTP 登录挑战
- **AND** 响应状态 SHALL 为 `202 Accepted`
- **AND** 响应 SHALL 包含高熵 `challengeToken` 和 `expiresAt`
- **AND** 响应 SHALL 设置 `Cache-Control: no-store`
- **AND** 系统 SHALL NOT 在该阶段保存 SecurityContext 或创建已认证会话
- **AND** 系统 SHALL NOT 在该阶段执行登录成功审计
- **AND** 挑战记录 SHALL NOT 保存密码、CAP token 或 TOTP 验证码

#### Scenario: 完成 TOTP 二次验证

- **GIVEN** 客户端持有有效且未消费的 TOTP 登录挑战
- **WHEN** 客户端请求固定端点 `POST /api/v1/login-session-challenges:verifyTotp` 并在 JSON 请求体提交有效的 `challengeToken` 与 6 位 TOTP 验证码
- **THEN** 系统 SHALL 原子消费挑战和验证码时间步
- **AND** 系统 SHALL 对 fresh SecurityContext 显式执行 SessionAuthenticationStrategy 后保存上下文并创建 Spring Security 服务端会话
- **AND** 系统 SHALL 轮换客户端已有的匿名会话 ID
- **AND** 响应状态 SHALL 为 `200 OK`
- **AND** 响应体 SHALL 为创建出的登录会话资源
- **AND** 系统 SHALL 清除该账号的失败限制状态并记录登录成功

#### Scenario: TOTP 二次验证失败

- **WHEN** 挑战不存在、过期、已消费、超过 5 次失败，或 TOTP 验证码缺失、格式错误、无效或重放
- **THEN** 系统 SHALL 拒绝创建认证会话
- **AND** 响应状态 SHALL 为 `401 Unauthorized`
- **AND** 错误 SHALL 使用不暴露账号或 TOTP 状态的统一凭证错误
- **AND** 有效挑战上的验证码失败 SHALL 同时增加挑战失败次数和账号维度失败计数
- **AND** 失败计数 SHALL 在返回错误时持久提交而不因异常映射回滚
- **AND** 挑战达到失败上限后 SHALL 失效

#### Scenario: 二次验证期间账号或凭据被停用

- **GIVEN** 客户端持有尚未过期的 TOTP 登录挑战
- **WHEN** 目标用户被停用、账号进入登录限制或 TOTP 凭据被清除
- **THEN** 系统 SHALL 拒绝二次验证并使挑战失效
- **AND** 系统 SHALL NOT 按第一阶段结果创建会话
- **AND** 用户停用、密码重置或 TOTP 清除 SHALL 主动使该用户所有未完成挑战和 pending enrollment 失效

#### Scenario: 匿名客户端可以完成二次验证

- **WHEN** 未认证客户端请求固定的 TOTP challenge 验证端点
- **THEN** Spring Security SHALL 仅对该端点允许匿名访问
- **AND** 请求 SHALL 继续受到 SPA CSRF 保护
- **AND** 可选 API 请求签名过滤器 SHALL 不要求该匿名请求提供已登录态签名

#### Scenario: 已认证主体不得再次发起登录

- **GIVEN** 客户端已经持有已认证会话
- **WHEN** 客户端再次提交 `POST /api/v1/login-sessions`
- **THEN** 系统 SHALL 拒绝该请求
- **AND** 系统 SHALL NOT 将既有主体与另一账号的认证尝试混合

#### Scenario: 登录失败

- **GIVEN** 客户端提交有效的 `powToken`
- **WHEN** 用户名或密码认证失败
- **THEN** 系统 SHALL 返回 `401 Unauthorized`
- **AND** 响应体 SHALL 为文本 `账号或凭证错误`
- **AND** 系统 SHALL NOT 暴露账号是否存在或是否启用 TOTP
- **AND** 已提交的 `powToken` SHALL 被消费
- **AND** 失败 SHALL 进入既有账号维度登录失败限制

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
- **AND** 当前主体 JSON SHALL 包含 `sessionId`、`username`、`displayName`、`roles` 和 `totpEnabled`
- **AND** 当前主体 JSON SHALL NOT 包含 TOTP 密钥、密文或已接受时间步

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

PC 端 SHALL 集成账号密码登录、安全验证、可选 TOTP、认证状态初始化和退出登录。

#### Scenario: 登录页提交

- **GIVEN** 用户在 PC 端登录页输入账号和密码
- **WHEN** 用户未完成安全验证就提交登录
- **THEN** PC 端 SHALL 阻止提交
- **AND** PC 端 SHALL 提示用户先完成安全验证

#### Scenario: 切换到 TOTP 二次验证

- **GIVEN** 用户完成账号、密码和 CAP 第一阶段
- **WHEN** 登录接口返回 `202 Accepted` 和 TOTP 登录挑战
- **THEN** PC 端 SHALL 在同一登录容器中切换到独立的 6 位验证码步骤
- **AND** PC 端 SHALL 清除内存中的密码和 CAP token
- **AND** PC 端 SHALL 只在内存中保留 challengeToken 与过期时间
- **AND** PC 端 SHALL NOT 在二次验证成功前初始化认证状态或导航到工作台

#### Scenario: 提交 TOTP 二次验证

- **GIVEN** PC 端持有未过期的 TOTP 登录挑战
- **WHEN** 用户提交 6 位验证码
- **THEN** PC 端 SHALL 请求固定端点 `POST /api/v1/login-session-challenges:verifyTotp` 并在 JSON 请求体提交 challengeToken 与验证码
- **AND** 输入 SHALL 使用数字键盘提示与 `one-time-code` 自动填充语义
- **AND** PC 端 SHALL 由用户明确提交而不是输入满 6 位后自动提交

#### Scenario: 登录页认证成功

- **GIVEN** 用户未启用 TOTP 并完成第一阶段，或用户完成有效 TOTP 二次验证
- **WHEN** 登录接口返回当前用户 JSON
- **THEN** PC 端 SHALL 写入 session store 的当前用户
- **AND** PC 端 SHALL 将认证状态标记为已初始化
- **AND** PC 端 SHALL 跳转到 redirect 查询参数指定路径或首页

#### Scenario: 登录页认证失败

- **WHEN** 账号密码登录请求失败
- **THEN** PC 端 SHALL 展示后端返回的统一错误信息
- **AND** PC 端 SHALL 重置 CAP 安全验证组件
- **AND** PC 端 SHALL 要求用户重新完成安全验证

#### Scenario: 二次验证错误与恢复

- **WHEN** TOTP 验证码错误但挑战仍有效
- **THEN** PC 端 SHALL 保留当前二次验证步骤和 challengeToken
- **AND** PC 端 SHALL 清空验证码并展示可执行错误
- **AND** PC 端 SHALL NOT 要求重复提交密码或 CAP

#### Scenario: 二次验证挑战失效

- **WHEN** TOTP 登录挑战过期、超过失败次数或被服务端判定失效
- **THEN** PC 端 SHALL 丢弃 challengeToken 和验证码
- **AND** PC 端 SHALL 返回账号密码步骤并重置 CAP

#### Scenario: 前端认证请求携带会话凭证

- **WHEN** PC 端调用认证接口或业务 API
- **THEN** 请求 SHALL 携带浏览器会话凭证

### Requirement: 可选 TOTP 凭据生命周期

系统 SHALL 允许已认证用户自愿启用或停用自己的 TOTP 凭据，未启用用户 SHALL 保持原账号密码与 CAP 登录路径。

#### Scenario: 准备 TOTP enrollment

- **GIVEN** 当前用户尚未启用 TOTP
- **WHEN** 客户端请求 `POST /api/v1/totp-enrollments`
- **THEN** 系统 SHALL 返回一次性展示的 Base32 手工密钥、标准 `otpauth` URI、短时效 enrollment token 和过期时间
- **AND** 数据库 SHALL 只保存 enrollment token 摘要与加密后的待确认密钥
- **AND** 同一用户重新准备 enrollment SHALL 使其先前 pending enrollment 失效
- **AND** 响应 SHALL 设置 `Cache-Control: no-store`
- **AND** 系统 SHALL NOT 因准备 enrollment 创建已启用凭据

#### Scenario: 确认启用 TOTP

- **GIVEN** 当前用户持有未过期且属于自己的 enrollment token
- **WHEN** 客户端请求 `POST /api/v1/totp-credentials` 并提交当前密码和有效 TOTP 验证码
- **THEN** 系统 SHALL 创建该用户唯一的 TOTP 凭据
- **AND** 系统 SHALL 原子消费 pending enrollment，重复或并发确认 SHALL 最多成功一次
- **AND** 系统 SHALL 将首次验证码的时间步保存为 `lastAcceptedStep`
- **AND** 系统 SHALL 从下一次登录开始要求该用户提交 TOTP
- **AND** 响应 SHALL 返回 `totpEnabled: true`
- **AND** 响应 SHALL NOT 返回 TOTP 密钥或密文

#### Scenario: 拒绝无效 enrollment

- **WHEN** enrollment token 过期、损坏、不属于当前用户，或者当前密码、TOTP 验证码无效
- **THEN** 系统 SHALL 拒绝创建 TOTP 凭据
- **AND** 系统 SHALL NOT 将该用户标记为已启用 TOTP

#### Scenario: enrollment token 无法重放

- **GIVEN** enrollment 已被确认、替换、停用、管理员清除或随用户状态变更而失效
- **WHEN** 客户端再次提交原 enrollment token
- **THEN** 系统 SHALL 拒绝创建 TOTP 凭据

#### Scenario: 停用自己的 TOTP

- **GIVEN** 当前用户已经启用 TOTP
- **WHEN** 客户端请求 `POST /api/v1/totp-credentials:disable` 并提交当前密码和有效 TOTP 验证码
- **THEN** 系统 SHALL 删除当前用户的 TOTP 凭据
- **AND** 后续登录 SHALL 不再要求该用户提交 TOTP

#### Scenario: 防止会话窃取者修改 TOTP

- **WHEN** 已认证请求准备之外的启用确认或停用动作
- **THEN** 系统 SHALL 重新校验当前用户密码
- **AND** 停用动作 SHALL 同时校验当前 TOTP 验证码
- **AND** 校验失败 SHALL 进入账号维度失败限制
- **AND** 系统 SHALL 在计算密码或 TOTP 前先断言账号尚未受限

### Requirement: TOTP 密钥和验证码安全

系统 SHALL 使用 RFC 6238 兼容参数验证 TOTP，并保护长期共享密钥、在线尝试和验证码重放边界。

#### Scenario: 生成与保存 TOTP 密钥

- **WHEN** 系统生成 TOTP enrollment
- **THEN** 密钥 SHALL 至少包含 160 bit 安全随机熵并使用 Base32 表达
- **AND** 已确认密钥 SHALL 使用外部配置的 AES-256-GCM 主密钥加密后写入 PostgreSQL
- **AND** 日志、错误、当前主体查询和用户管理查询 SHALL NOT 返回明文密钥、密文或加密主密钥

#### Scenario: 验证时间窗口

- **WHEN** 系统验证 6 位 TOTP 验证码
- **THEN** 系统 SHALL 使用 30 秒时间步
- **AND** 系统 SHALL 只接受当前时间步及其前后各一个时间步
- **AND** 系统 SHALL 要求部署主机保持时间同步

#### Scenario: 拒绝验证码重放

- **GIVEN** 某一时间步的验证码已经成功用于该凭据
- **WHEN** 相同或更早时间步的验证码再次提交
- **THEN** 系统 SHALL 拒绝验证
- **AND** 并发提交同一验证码 SHALL 最多允许一个请求成功

#### Scenario: 缺少或损坏主密钥

- **WHEN** TOTP 加密主密钥未配置、错误或无法解密已有凭据
- **THEN** 系统 SHALL 禁止准备或确认新的 TOTP 凭据
- **AND** 已启用 TOTP 用户 SHALL 无法绕过 TOTP 完成登录
- **AND** 系统 SHALL 保留管理员清除目标凭据的恢复路径
- **AND** 凭据查询、配置或解密异常 SHALL NOT 被视为未启用 TOTP

### Requirement: TOTP 安全事件审计

系统 SHALL 记录 TOTP 启用、用户停用和管理员清除事件，不记录任何 TOTP 密钥或验证码。

#### Scenario: 记录 TOTP 凭据变更

- **WHEN** 用户成功启用、停用 TOTP，或管理员成功清除目标用户 TOTP
- **THEN** 系统 SHALL 写入包含事件类型、操作人、目标用户和客户端上下文的认证审计记录
- **AND** 审计记录 SHALL NOT 包含 TOTP 密钥、密文、验证码或 enrollment token
- **AND** 应用日志 SHALL NOT 记录 challenge token、enrollment token、验证码、密文或包含这些值的 URI

### Requirement: Cap 最新稳定客户端兼容

PC 端 SHALL 使用当前核对过的 Cap 最新稳定 Widget 与 WASM，并继续通过项目自有 REST 适配完成登录前安全验证。

#### Scenario: 当前没有更高稳定版本

- **GIVEN** 上游最新稳定版本为 `cap-widget 0.1.56` 和 `@cap.js/wasm 0.0.7`
- **WHEN** 系统交付本次认证变更
- **THEN** 前端 SHALL 保持这两个版本及锁文件一致
- **AND** 系统 SHALL 保留上游尚未吸收的 worker 生命周期竞态修复
- **AND** 系统 SHALL NOT 因 TOTP 启用而跳过 CAP

### Requirement: PC 端账号安全设置

PC 端 SHALL 为所有已认证用户提供不依赖业务权限的账号安全入口。

#### Scenario: 未启用时展示 enrollment

- **GIVEN** 当前用户未启用 TOTP
- **WHEN** 用户进入账号安全页并开始启用
- **THEN** PC 端 SHALL 在本地根据 `otpauth` URI 展示二维码
- **AND** PC 端 SHALL 同时展示可复制的手工密钥和 6 位验证码输入
- **AND** PC 端 SHALL 只在服务端确认成功后将状态显示为已启用

#### Scenario: 已启用时停用

- **GIVEN** 当前用户已启用 TOTP
- **WHEN** 用户在账号安全页选择停用
- **THEN** PC 端 SHALL 要求当前密码和有效 TOTP 验证码
- **AND** PC 端 SHALL 展示提交中、失败和成功反馈
- **AND** 停用成功后 SHALL 刷新当前主体的 `totpEnabled` 状态

#### Scenario: 保护 enrollment 秘密

- **WHEN** 用户离开、刷新或取消尚未确认的 enrollment
- **THEN** PC 端 SHALL 丢弃手工密钥、二维码数据和 enrollment token
- **AND** PC 端 SHALL NOT 将这些数据写入持久化 store、URL、日志或远程二维码服务
