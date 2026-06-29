## Context

当前认证采用 Spring Security 表单登录和 Spring Session JDBC。`SPRING_SESSION` 已保存会话 ID、创建时间、最后访问时间、过期时间和登录主体，并由 Spring Session 负责过期清理和会话失效。项目需要在此基础上提供登录会话管理、踢下线和认证审计能力。

内网部署场景下 IP 归属地解析价值有限，且容易被代理、NAT 和内网地址影响。本次只保存请求 IP、Host、Forwarded 相关头和原始 User-Agent，并做后端轻量 User-Agent 摘要解析。原始 User-Agent 保留为排障真相源，解析结果只用于页面展示。

## Goals / Non-Goals

**Goals:**

- 以 Spring Session 当前有效会话作为“在线登录会话”的唯一真相源。
- 支持管理员查看登录会话并踢下线指定 session。
- 记录登录成功、登录失败、主动退出和管理员踢下线审计事件，审计日志不自动清理。
- 对连续登录失败进行指数退避限制，限制状态作为短生命周期数据清理。
- 将 CAP challenge/token 过期清理接入统一短生命周期数据清理作业，避免创建、兑换、校验请求每次触发清理。
- 保存客户端上下文，包括 IP、Host、Forwarded、X-Forwarded-For、X-Real-IP、原始 User-Agent、浏览器、操作系统和设备类型。
- PC 端页面文案使用“登录会话”和“认证审计”。

**Non-Goals:**

- 不新增 `am_authentication_login_session` 或其他项目自有在线会话表。
- 不清理认证审计日志。
- 不做 IP 归属地解析、设备唯一指纹、登录地点风控或异常登录检测。
- 不把 User-Agent 解析结果作为权限、安全判断或唯一设备标识。

## Decisions

### Spring Session 是在线真相源

登录会话列表直接从 Spring Session 读取当前有效 session。`LAST_ACCESS_TIME`、`EXPIRY_TIME` 和 `PRINCIPAL_NAME` 使用 Spring Session JDBC 表字段；登录时采集的客户端摘要保存到 Spring Session attribute 中，用于列表展示。

备选方案是新增 `am_authentication_login_session` 保存会话元数据。该方案会引入双状态源：Spring Session 删除后项目表仍可能残留在线状态，需要额外同步。当前需求只需要展示当前在线会话和踢下线，因此不引入该表。

### 认证审计表记录认证事件

新增 `am_authentication_event` 作为认证审计流水表，记录 `login_success`、`login_failure`、`logout`、`kickout` 四类事件。踢下线时先读取目标 session 快照写入审计日志，再删除 Spring Session。

日志是审计真相源，即使 session 过期、退出或被踢下线，历史事件仍可查询。

### 登录失败限制单独建模

登录失败限制不是审计日志的派生查询，而是独立短生命周期状态。新增 `am_login_failure_limit` 记录登录名、当前窗口内失败次数、连续触发限制次数、锁定截止时间、最近失败时间和清理截止时间。

表单登录在校验 CAP 之前先检查该登录名是否处于锁定期。锁定期内直接拒绝登录，不消费 CAP token，也不执行密码校验。登录失败后更新失败限制状态；达到阈值时按指数退避计算锁定时间。登录成功后清除该登录名的限制状态。

默认策略为 10 分钟内失败 5 次触发限制，首次锁定 5 分钟，后续按 3 倍指数退避增长，最大锁定 24 小时。限制状态在最后失败或锁定结束后一段时间由统一清理作业删除。

### 短生命周期数据统一清理

新增 `ExpiredDataCleaner` 作为业务过期状态清理接口。Quartz 只提供一个统一作业，注入所有 `ExpiredDataCleaner` 实现并逐个调用；某个实现失败不影响其他实现继续执行。

CAP challenge/token 和登录失败限制接入该接口。认证审计日志和 Spring Session 不接入：审计日志是长期审计流水，Spring Session 的过期清理由 Spring Session 自身负责。

### 客户端信息由后端采集和解析

服务端从当前 HTTP 请求采集请求上下文，写入 Spring Session attribute 和审计日志。后端进行轻量 User-Agent 摘要解析，返回浏览器、操作系统和设备类型。前端不额外引入客户端解析库，避免前后端解析口径分裂。

### API 资源命名

登录会话作为可列举资源使用 `GET /api/v1/login-sessions`。踢下线是对单个登录会话的状态变更动作，使用 `DELETE /api/v1/login-sessions/{session}`。认证审计日志使用 `GET /api/v1/authentication-events`。

## Risks / Trade-offs

- User-Agent 轻量解析不完整 -> 保留原始 User-Agent，页面展示解析摘要，后续需要更高精度时再替换为成熟解析库。
- Spring Session attribute 不适合复杂数据库筛选 -> 登录会话页面只做当前在线会话展示；复杂历史筛选走认证事件审计表。
- 登录限制只按用户名统计 -> 内网代理和 NAT 场景下不按 IP 限制，避免同一出口互相影响。
- 踢下线删除 session 后无法再读取 session 内容 -> 踢下线流程必须先读取目标 session 快照并写审计日志，再删除 session。
- 代理环境下真实 IP 可能不可靠 -> 同时保存 remote address、X-Forwarded-For、X-Real-IP 和 Forwarded，由部署侧决定可信代理策略。

## Migration Plan

- Flyway 新增 `am_authentication_event` 表和必要索引。
- 发布后新认证事件开始写入日志；历史登录不会补录。
- 回滚时删除新增代码即可停止写入；已创建审计表保留不影响 Spring Session 认证流程。

## Open Questions

- 暂无。当前实现按“Spring Session 管在线、认证日志管审计”推进。
