# 能力规格：机器身份

构建命令、代码风格、测试策略和工程边界见[项目总规格](../SPEC.md)。浏览器会话登录见[登录与认证](SPEC-登录与认证.md)。功能权限点见[功能权限](SPEC-功能权限.md)。档案可见范围见[档案数据范围](SPEC-档案数据范围.md)。

## 目标

提供可由管理员管理的服务账号、只保存哈希的受限访问令牌，以及令牌调用审计，使后续只读调用方可以通过现有 HTTP API 访问系统。令牌认证与浏览器会话认证并存。

## 验收要求

### 要求：服务账号

系统 SHALL 允许具备 `authentication:service-account:manage` 的管理员创建、更新、停用和软删除服务账号。

#### 场景：创建服务账号

- **WHEN** 管理员提交名称、可选说明、负责人用户和角色
- **THEN** 系统 SHALL 创建状态为 `ACTIVE` 的服务账号
- **AND** 名称在未删除账号中 SHALL 唯一
- **AND** 负责人只表示责任人，系统 SHALL NOT 把负责人的功能权限、用户数据范围或部门数据范围授予该服务账号

#### 场景：停用与软删除

- **WHEN** 管理员把服务账号更新为 `DISABLED`
- **THEN** 该账号已签发且未吊销的令牌在认证时 SHALL 失败，失败原因记为 `DISABLED`
- **AND** 系统 SHALL NOT 因此自动吊销令牌
- **WHEN** 管理员删除服务账号
- **THEN** 系统 SHALL 软删除该账号、清除角色绑定，并吊销其仍有效的令牌
- **AND** 之后按标识读取该账号 SHALL 返回不存在

### 要求：访问令牌

系统 SHALL 为服务账号和个人用户签发可过期、可吊销的 Bearer 访问令牌。明文只在创建响应中返回一次，库中只保存令牌前缀和 SHA-256 哈希。

#### 场景：签发服务账号令牌

- **GIVEN** 调用方具备 `authentication:access-token:manage`
- **WHEN** 管理员为启用或停用但未删除的服务账号提交名称和未来的过期时间
- **THEN** 响应状态 SHALL 为 `201 Created`
- **AND** 响应 SHALL 包含一次明文 `secret`，并设置 `Cache-Control: no-store`
- **AND** 未提交 scope 时，系统 SHALL 使用该服务账号已有权限与 `archive:item:read` 的交集
- **AND** 该交集为空时 SHALL 拒绝，并要求显式提交 scope
- **AND** 显式 scope SHALL 是该服务账号当前角色权限的子集，而不是操作者权限的子集

#### 场景：签发个人访问令牌

- **WHEN** 已认证人类用户调用 `POST /me/access-tokens`
- **THEN** 有效权限 SHALL 为该用户当前权限与提交 scope 的交集
- **AND** 未提交 scope 时默认规则与服务账号令牌相同
- **AND** 服务账号令牌 SHALL NOT 签发个人访问令牌

#### 场景：Bearer 认证

- **WHEN** 请求携带 `Authorization: Bearer <令牌>`
- **THEN** 系统 SHALL 只按令牌认证，SHALL NOT 回退到同请求中的会话 cookie
- **AND** 该请求 SHALL NOT 创建或写入 HTTP 会话，也 SHALL NOT 要求 CSRF 或请求签名
- **AND** 不携带 Bearer 的浏览器登录 SHALL 继续使用现有会话、CSRF 和可选签名
- **AND** 无效、过期、已吊销、主体停用或超过限流的令牌 SHALL 分别以 `401` 或 `429` 失败

#### 场景：scope 不放宽数据范围

- **WHEN** 服务账号令牌访问受数据范围保护的档案接口
- **THEN** 功能权限 SHALL 为角色权限与令牌 scope 的交集
- **AND** 数据范围 SHALL 只合并该服务账号已绑定且启用的角色范围
- **AND** 启用的内置超级管理员角色 SHALL 解析为全部档案数据
- **AND** 令牌 scope 未覆盖全部稳定权限点时，系统 SHALL NOT 把该调用方当作超级管理员

#### 场景：吊销

- **WHEN** 具备令牌管理权限的管理员，或个人令牌所属的人类用户，吊销令牌
- **THEN** 系统 SHALL 记录吊销时间
- **AND** 重复吊销 SHALL 成功且不改变已有吊销时间

### 要求：调用审计

系统 SHALL 记录每次令牌认证请求，包括认证失败。

#### 场景：成功与失败都留下审计

- **WHEN** 令牌请求完成或在认证阶段被拒绝
- **THEN** 系统 SHALL 记录调用方类型、服务账号、令牌标识、代表用户、方法、不含查询串的路径、状态码、耗时、来源 IP、traceId 和发生时间
- **AND** 认证失败 SHALL 记录 `INVALID`、`EXPIRED`、`REVOKED`、`DISABLED` 或 `RATE_LIMITED`
- **AND** 系统 SHALL NOT 记录请求体、令牌明文、令牌哈希或文件正文
- **AND** 具备 `authentication:api-call:read` 的管理员 SHALL 能按游标分页查询，并可按服务账号或令牌过滤

### 要求：限流

系统 SHALL 支持按令牌的进程内固定窗口限流，并允许通过 `archive.security.access-token.rate-limit.enabled` 关闭。

#### 场景：超过每分钟限额

- **WHEN** 同一令牌在一分钟窗口内的已匹配请求超过配置限额
- **THEN** 系统 SHALL 返回 `429`、`Retry-After: 60` 和资源耗尽问题
- **AND** 审计失败原因 SHALL 为 `RATE_LIMITED`
- **AND** 关闭限流后 SHALL 不再拒绝

## 管理端

具备对应权限的管理员可以在服务账号页面维护账号、绑定角色、签发和吊销令牌，并在创建后一次性复制明文。调用审计页面提供分页查询。个人访问令牌目前只提供 HTTP API，管理端不提供自助签发页面。
