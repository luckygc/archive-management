# login-authentication Specification

## Purpose

提供 PC 端账号密码登录、登录前安全验证、基于服务端会话的认证状态保持，以及当前登录用户查询和退出登录能力。

## Requirements

### Requirement: 登录前安全验证

系统 SHALL 在账号密码登录前要求客户端完成一次 CAP 工作量证明安全验证。

#### Scenario: 创建安全验证挑战

- **WHEN** 客户端请求 `POST /api/auth/cap/challenge`
- **THEN** 系统 SHALL 创建一条 `am_cap_challenge` 挑战记录
- **AND** 响应 SHALL 包含 `challenge`、`token` 和 `expires`
- **AND** `challenge` SHALL 包含挑战数量 `c`、挑战尺寸 `s` 和难度 `d`
- **AND** challenge 默认有效期 SHALL 为 10 分钟

#### Scenario: 兑换安全验证令牌

- **GIVEN** 客户端持有未过期的 challenge token
- **WHEN** 客户端请求 `POST /api/auth/cap/redeem` 并提交 token 与完整 solutions
- **THEN** 系统 SHALL 校验每个 solution 是否匹配 challenge 规则
- **AND** 系统 SHALL 删除已提交的 challenge token
- **AND** 校验成功时 SHALL 创建一条 `am_cap_token` 令牌记录
- **AND** 响应 SHALL 包含 `success: true`、一次性登录令牌 `token` 和 `expires`
- **AND** 令牌默认有效期 SHALL 为 20 分钟

#### Scenario: 兑换安全验证失败

- **WHEN** 客户端提交空请求体、空 token、缺失 solutions、已过期 token 或错误 solutions
- **THEN** 系统 SHALL 返回 `success: false`
- **AND** 响应 SHALL 包含失败原因 `message`
- **AND** 系统 SHALL 删除本次提交的 challenge token

#### Scenario: 校验安全验证令牌

- **WHEN** 客户端请求 `POST /api/auth/cap/validateToken`
- **THEN** 系统 SHALL 按提交的 token 返回 `{ "success": true }` 或 `{ "success": false }`
- **AND** 当 `keepToken` 为 `true` 时，系统 SHALL 只检查令牌有效性，不消费令牌
- **AND** 当 `keepToken` 不为 `true` 时，系统 SHALL 消费一次性令牌

### Requirement: 账号密码登录

系统 SHALL 使用 Spring Security 表单登录处理账号密码认证。

#### Scenario: 登录请求格式

- **WHEN** 客户端提交登录请求
- **THEN** 请求 SHALL 使用 `POST /api/auth/login`
- **AND** 请求体 SHALL 使用 `application/x-www-form-urlencoded`
- **AND** 请求参数 SHALL 包含 `username`、`password` 和 `powToken`

#### Scenario: 登录前消费安全验证令牌

- **GIVEN** 客户端提交 `POST /api/auth/login`
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
- **AND** 响应体 SHALL 为当前用户 JSON
- **AND** 当前用户 JSON SHALL 包含 `username`、`displayName` 和 `roles`

#### Scenario: 登录失败

- **GIVEN** 客户端提交有效的 `powToken`
- **WHEN** 用户名或密码认证失败
- **THEN** 系统 SHALL 返回 `401 Unauthorized`
- **AND** 响应体 SHALL 为文本 `账号或密码错误`
- **AND** 已提交的 `powToken` SHALL 被消费

### Requirement: 用户认证数据

系统 SHALL 从本地数据库加载用户身份和角色。

#### Scenario: 加载启用用户

- **WHEN** 系统按用户名加载用户
- **THEN** 系统 SHALL 从 `am_user` 读取用户账号、密码密文、显示名称和启用状态
- **AND** 系统 SHALL 只允许启用用户通过认证

#### Scenario: 加载用户角色

- **WHEN** 系统构造登录用户权限
- **THEN** 系统 SHALL 从 `am_user_role` 读取用户角色编码
- **AND** 写入 Spring Security 权限时 SHALL 自动添加 `ROLE_` 前缀
- **AND** 对外返回当前用户时 SHALL 去除 `ROLE_` 前缀

#### Scenario: 初始管理员账号

- **WHEN** 数据库迁移初始化认证数据
- **THEN** 系统 SHALL 创建默认用户 `admin`
- **AND** 默认用户显示名称 SHALL 为 `系统管理员`
- **AND** 默认用户 SHALL 具有 `ADMIN` 角色

### Requirement: 当前用户查询

系统 SHALL 提供当前登录用户查询接口。

#### Scenario: 查询当前用户

- **GIVEN** 客户端已登录
- **WHEN** 客户端请求 `GET /api/auth/me`
- **THEN** 系统 SHALL 返回当前用户 JSON
- **AND** 当前用户 JSON SHALL 包含 `username`、`displayName` 和 `roles`

#### Scenario: 未登录访问 API

- **GIVEN** 客户端未登录
- **WHEN** 客户端访问受保护的 `/api/**` 接口
- **THEN** 系统 SHALL 返回 `401 Unauthorized`

### Requirement: 退出登录

系统 SHALL 提供退出登录接口并清理服务端认证状态。

#### Scenario: 退出当前会话

- **GIVEN** 客户端已登录
- **WHEN** 客户端请求 `POST /api/auth/logout`
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
