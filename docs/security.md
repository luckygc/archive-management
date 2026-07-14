# 安全文档

本文说明当前项目的认证、会话、权限、数据范围、请求签名和审计边界。安全能力以服务端实现为准，前端状态只改善体验，不能替代服务端校验。

## 认证入口

公开认证相关接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/v1/cap-challenges` | 创建登录前安全验证 challenge |
| `POST` | `/api/v1/cap-tokens` | 兑换 CAP token |
| `POST` | `/api/v1/cap-tokens:validate` | 校验 CAP token |
| `POST` | `/api/v1/login-sessions` | 账号密码登录 |

登录成功后，Spring Security 将认证状态写入服务端 HTTP Session。项目显式使用 `HttpSessionSecurityContextRepository` 保存 SecurityContext，并由 Spring Session JDBC 持久化会话；浏览器只通过名为 `am_session` 的 session cookie 关联服务端会话。

当前主体查询：

```http
GET /api/v1/me
```

退出或踢下线：

```http
DELETE /api/v1/login-sessions/{session}
```

## 会话

默认配置：

```yaml
spring:
    session:
        timeout: 30m
        jdbc:
            initialize-schema: never
            table-name: SPRING_SESSION

server:
    servlet:
        session:
            cookie:
                name: am_session
```

会话表由 Flyway 管理，不由 Spring Session 自动建表。PC 前端收到 401 后会清理本地当前用户状态并跳转登录页。

## 登录失败限制

登录失败限制用于抑制暴力尝试。管理员可通过认证审计和失败限制重置接口处理误锁定：

```http
POST /api/v1/login-failure-limits/{username}:reset
```

认证审计接口：

```http
GET /api/v1/authentication-events
```

## 功能权限

功能权限由后端权限点和角色绑定控制。

| 接口 | 说明 |
| --- | --- |
| `GET /api/v1/authorization-permissions` | 查询系统权限点 |
| `GET /api/v1/me/permissions` | 查询当前用户权限点 |
| `GET /api/v1/authorization-roles` | 查询角色目录；角色、用户、功能权限或数据范围管理员可读 |
| `GET /api/v1/authentication-users` | 查询用户管理列表；仅用户管理员可读 |
| `GET /api/v1/authentication-user-options` | 查询数据范围授权用户选项；仅数据范围管理员可读，只返回 ID、用户名和显示名 |
| `GET /api/v1/authorization-roles/{role}/permissions` | 查询角色权限 |
| `PUT /api/v1/authorization-roles/{role}/permissions` | 覆盖角色权限 |

授权管理是能力聚合页：具备 `authorization:permission:manage` 时只显示并请求角色功能权限，具备 `archive:data-scope:manage` 时只显示并请求角色、最小用户选项、部门数据范围。用户选项不暴露邮箱、手机、部门、启用状态和创建时间；角色/用户详情以及创建、修改、删除、分配角色、覆盖功能权限和覆盖数据范围仍要求对应精确管理权限。

前端可以根据权限隐藏或禁用操作。权限摘要是不可由页面改写的深度只读快照，最新刷新成功后才原子替换。快照有效期为五分钟；AppShell 每 60 秒以及窗口 focus、页面 visible 时检查，并在最后 60 秒内预刷新。有效期内失败保留上一成功版本，到期仍失败则权限判断失败关闭、停止渲染受保护内容并显示可重试的“权限校验失败”，不会误报为 403。自动触发共享单一在途请求，快速失败按 60 秒节流，卸载后停止调度。撤权时立即隐藏无权缓存内容；导航失败时保留页签并显示内联 403，离开该路由后清理页签和缓存。所有写入、删除、导出、下载和管理操作仍必须由后端重新校验权限。

## 数据范围

数据范围用于约束用户可见和可操作的档案集合。当前支持按角色、用户和组织部门绑定档案数据范围。

| 接口 | 说明 |
| --- | --- |
| `GET /api/v1/archive-data-scopes` | 查询数据范围 |
| `POST /api/v1/archive-data-scopes` | 创建数据范围 |
| `PUT /api/v1/archive-data-scopes/{archiveDataScope}` | 更新数据范围 |
| `GET /api/v1/authorization-roles/{role}/archive-data-scopes` | 查询角色数据范围 |
| `PUT /api/v1/authorization-roles/{role}/archive-data-scopes` | 覆盖角色数据范围 |
| `GET /api/v1/authorization-users/{user}/archive-data-scopes` | 查询用户数据范围 |
| `PUT /api/v1/authorization-users/{user}/archive-data-scopes` | 覆盖用户数据范围 |
| `GET /api/v1/organization-departments/{organizationDepartment}/archive-data-scopes` | 查询部门数据范围 |
| `PUT /api/v1/organization-departments/{organizationDepartment}/archive-data-scopes` | 覆盖部门数据范围 |

档案查询、管理查询、详情、导出和电子文件访问都应共享服务端数据范围判断。不要用前端筛选替代数据权限。

## CORS

默认本地允许：

```yaml
archive:
    security:
        cors:
            allowed-origins:
                - http://localhost:5173
                - http://127.0.0.1:5173
            allow-credentials: true
```

生产环境必须替换为实际可信前端 Origin。允许凭证时，不应使用宽泛 Origin。

## CSRF

Spring Security 使用 SPA CSRF 配置。CAP 相关接口被排除：

- `/api/v1/cap-challenges`
- `/api/v1/cap-tokens`
- `/api/v1/cap-tokens:validate`

前端请求需要按 Spring Security 约定携带 CSRF token。CORS 配置中已允许 `X-XSRF-TOKEN`。

## 请求签名

请求签名用于保护 `/api/**` 请求，默认关闭：

```yaml
archive:
    security:
        request-signature:
            enabled: false
            secret:
            clock-skew: 5m
```

开启后，除 OPTIONS 预检外，请求需要携带：

- `X-AM-Timestamp`
- `X-AM-Nonce`
- `X-AM-Signature`

nonce 使用 Spring Cache 保存，默认缓存名为 `archive.security.request-signature.nonce`，默认 Caffeine 过期时间为 10 分钟。签名密钥长度至少 32 个字符，必须通过外部安全配置提供。

## 公开下载

文件短链有两类下载入口：

| 路径 | 认证要求 |
| --- | --- |
| `/api/v1/file-links/{code}:download` | 需要登录 |
| `/api/v1/public-file-links/{code}:download` | 公开下载 |

公开下载路径只应用于业务明确允许公开访问的短链。短链过期时间由服务端生成和校验。

## Actuator

Actuator 路径为 `/actuator/**`，默认暴露端点由配置控制。访问需要具备配置的监控角色：

```yaml
archive:
    security:
        authorization:
            actuator-role-name: 系统监控
```

生产环境应通过网关、网络策略或安全组限制访问来源。

## 密码和密钥

- 用户密码使用 Spring Security `DelegatingPasswordEncoder`。
- 管理员初始化密码、对象存储密钥和请求签名密钥不提交到 Git。
- `application-local.yaml` 仅用于本机差异配置，不进入构建产物。

## 审计

当前审计重点：

- 登录、退出、踢下线和失败限制事件。
- 档案条目审计。
- 文件下载和短链访问相关记录。
- 规则执行追踪。

排障时保留 `traceId`、用户、请求路径和业务对象 ID。
