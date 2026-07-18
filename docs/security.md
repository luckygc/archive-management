# 安全架构与配置指引

本文记录认证、会话、授权、数据范围、跨域、请求签名、公开下载和审计的稳定安全边界。安全能力以服务端合同与实现为准；前端状态、路由和按钮可见性只改善体验，不能替代服务端校验。

## 真相源

- 当前默认值和可覆盖配置：[`application.yaml`](../backend/archive-server/src/main/resources/application.yaml)
- 通用 HTTP、错误和 ID 合同：[`api-contract`](../openspec/specs/api-contract/spec.md)
- 登录、会话和认证验收：[`login-authentication`](../openspec/specs/login-authentication/spec.md)
- 功能权限：[`authorization-permissions`](../openspec/specs/authorization-permissions/spec.md)
- 档案数据范围：[`archive-data-scope`](../openspec/specs/archive-data-scope/spec.md)
- 文件与短链：[`file-storage`](../openspec/specs/file-storage/spec.md)
- API 使用入口：[`api.md`](api.md)

具体接口清单、字段、状态机和验收场景由上述规格承担，本文不维护页面或 Controller 快照。

## 威胁边界

- 浏览器、前端状态、请求参数、上传文件和任何客户端声明均不可信。
- Spring Boot 主应用负责认证、功能授权、数据范围、参数校验和业务不变量；数据库约束继续守住持久化不变量。
- PostgreSQL、外部 S3 兼容对象存储、预览服务和部署平台是独立运行边界，使用最小权限账号和受控网络访问。
- CORS 不是认证机制；允许跨域不代表允许业务访问。
- 公开下载、Actuator、管理员初始化和请求签名密钥是高风险入口，必须由部署环境显式收敛。

## 认证与会话

登录前安全验证和账号密码登录按 `login-authentication` 执行。登录成功后，Spring Security 把认证状态保存在服务端 HTTP Session，Spring Session JDBC 持久化会话；浏览器只持有配置的 session cookie。

会话超时、cookie 名称和 JDBC 表配置以 `application.yaml` 为准。Spring Session 表由 Flyway 管理，不由运行期自动建表。退出、踢下线、失败限制和认证审计属于认证业务合同，不在本文复制接口表。

部署时确认：

- cookie 只在预期域和路径发送，并按 HTTPS 部署策略设置安全属性。
- 代理或网关不记录密码、CAP token、session cookie、CSRF token 或签名密钥。
- 管理员初始化只在受控窗口启用，完成后立即关闭。

## 功能权限与数据范围

服务端对读取、创建、修改、删除、锁定、导入导出、文件访问和管理操作校验精确权限；涉及档案数据时同时应用用户数据范围。角色、用户和组织部门绑定的范围合同以 `archive-data-scope` 为准。

前端可以隐藏或禁用无权操作，但所有写入和敏感读取仍由后端重新校验。当前 Spring Security 过滤链遇到未认证的项目 API 请求时，通过 `HttpStatusEntryPoint` 返回 `401 Unauthorized` 状态，不承诺 ProblemDetail 响应体。项目自有 API 错误的目标合同以 [`api-contract`](../openspec/specs/api-contract/spec.md) 为准；若要让该入口返回统一响应体，必须另行提出实现变更并补充测试。其他权限与数据范围错误避免暴露异常类名、堆栈、SQL、内部拓扑或非必要实现细节，具体状态码和业务语义以对应 OpenSpec 为准。

## CORS 与 CSRF

当前本地 CORS 默认值在 `application.yaml`。生产环境必须把 `archive.security.cors.allowed-origins` 替换为实际可信前端 Origin；允许凭证时不得使用宽泛 Origin，并只开放业务需要的方法、请求头和响应头。

Spring Security 使用 SPA CSRF 保护。前端按框架约定读取并回传 CSRF token；只有认证规格明确的登录前安全验证适配入口可以排除。CORS 预检和 CSRF 豁免应保持最小，不因开发便利扩大到全部 `/api/**`。

## 请求签名

`archive.security.request-signature.enabled` 默认关闭。部署环境按威胁模型决定是否开启。开启后，请求签名覆盖项目 `/api/**` 请求，但以下稳定类别不进入签名校验：

- OPTIONS 预检请求。
- 登录和 CAP 安全验证所需的引导类公开请求。
- 通过登录文件短链或公开文件短链执行的 GET 下载请求。

其余 `/api/**` 请求携带：

- `X-AM-Timestamp`
- `X-AM-Nonce`
- `X-AM-Signature`

签名密钥至少 32 个字符，只通过 Secret 提供。时间偏移、nonce 缓存和过期策略以 `application.yaml` 为准；nonce 通过 Spring Cache 防重放。集群部署时必须使用满足共享一致性要求的 `CacheManager`，不能让不同节点各自接受同一 nonce。

## 文件、上传与公开下载

文件内容只进入外部 S3 兼容对象存储，业务通过服务端 `FileStorageService` 访问。对象 key、bucket、短链状态和业务对象权限由服务端校验，客户端不能直接指定未验证的存储位置。

登录下载与公开下载是不同信任边界。公开短链只用于业务明确允许公开的对象，并由服务端校验随机性、有效期、目标状态和撤销条件。网关、对象存储策略和日志不得把公开入口扩大为 bucket 级匿名读取。

上传链路至少限制请求大小、文件数量和内容处理资源；需要预览或格式探测时，把文件视为不可信输入，在独立预览服务边界内限制超时、内存、外部工具和输出。

## Actuator 与运行面

Actuator 的基础路径、端点暴露和监控角色以 `application.yaml` 为准。生产环境通过角色、网关、网络策略或安全组限制访问来源，不向公网暴露全部端点，也不在健康和环境输出中泄露密钥、连接串或内部拓扑。

预览服务是独立运行面，应单独限制网络来源、上传大小和外部转换工具权限。PostgreSQL 与 S3 服务只允许应用和受控运维入口访问。

## 密码、密钥与本机配置

- 用户密码由 Spring Security `DelegatingPasswordEncoder` 处理，不记录明文或可逆密文。
- 数据库密码、S3 凭证、管理员初始化密码和请求签名密钥不提交到 Git。
- `application-local.yaml` 只用于本机覆盖，不是交付配置或 Secret 管理方案。
- 生产 Secret 应支持最小权限、轮换、撤销和审计；轮换前验证旧新凭证切换与回滚路径。

## 审计与排障

认证事件、权限变更、档案关键操作、文件访问、短链和规则执行应保留可追踪审计。排障至少关联 `traceId`、认证主体、请求路径、业务对象 ID、结果和时间；日志避免记录密码、cookie、token、签名原文、文件正文或客户敏感字段。

安全配置变更后运行 `task governance-check` 和与改动范围匹配的后端测试，并在预生产验证认证失败、权限不足、数据范围、CSRF、CORS、签名重放、短链过期和 Actuator 隔离路径。
