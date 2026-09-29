# API 使用与规格索引

本文提供当前 HTTP API 的使用入口和合同路由，不复制分页、错误、DTO、ID 或业务字段的完整规则。

## 真相源

| 内容 | Owner |
| --- | --- |
| 项目自有 API 的设计和路径、响应、DTO、分页、过滤、排序、ID、异步任务及 ProblemDetail 合同 | [`api-contract`](../specs/SPEC-项目API合同.md) |
| API 风格的选型理由 | [ADR-0001](adr/0001-project-api-style.md) |
| 业务字段、状态机、权限边界和验收场景 | [`specs/`](../specs/) 下对应业务规格 |
| 当前默认端口、Session cookie、CORS 和请求签名配置 | [`application.yaml`](../server/src/main/resources/application.yaml) |
| 认证、授权、数据范围和公开入口的安全指引 | [`security.md`](security.md) |

项目自有 API 直接以资源路径开始，例如 `/archive-items`，默认由 Spring Boot 主应用提供；新接口按 `api-contract` 中选定的外部规范设计，存量接口在迁移前使用其中单列的兼容合同。登录态保存在服务端 HTTP Session，浏览器使用配置的 session cookie 关联会话。除业务明确声明的公开入口和预检请求外，客户端应按服务端认证、授权、CSRF、CORS 和可选请求签名要求访问。

后端运行时在 `/v3/api-docs` 提供 OpenAPI JSON，需使用已认证的会话访问。分页接口在文档中使用 `limit`、`cursor` 查询参数；具体业务字段、可排序字段和权限仍以对应业务规格为准。

调用失败时，客户端按 `api-contract` 定义的 ProblemDetail 处理；新接口用 `type` 识别问题类型，存量接口可保留 `traceId` 用于排障。不要依赖异常类名、HTML 错误页或自由文本推断错误类型。集合、分页和异步任务同样只按 `api-contract` 消费，不根据实现框架类型猜测合同。

搜索投影重建任务已支持断线后找回：受理响应体是操作监视资源，响应头的 `Operation-Location` 是绝对监视地址，客户端可按 `Retry-After` 轮询；重新登录后可通过 `GET /operations` 查找自己发起且仍有权限查看的任务，再通过 `GET /operations/{id}` 读取状态。当前操作类型、分页限额和结果保留见[档案记录搜索规格](../specs/SPEC-档案记录搜索.md)。本地 Vite 开发服务器会将 `/operations` 转发到 Spring Boot。

## 业务规格索引

### 身份、权限和组织

- [登录与认证](../specs/SPEC-登录与认证.md)
- [功能权限](../specs/SPEC-功能权限.md)
- [组织部门](../specs/SPEC-组织部门.md)
- [档案数据范围](../specs/SPEC-档案数据范围.md)

### 档案元数据与记录

- [分类与全宗可用范围](../specs/SPEC-档案分类与全宗范围.md)
- [档案元数据](../specs/SPEC-档案元数据.md)
- [档案记录搜索](../specs/SPEC-档案记录搜索.md)
- [档案记录路由](../specs/SPEC-档案记录路由.md)
- [档案导入导出](../specs/SPEC-档案导入导出.md)

### 运行时规则

- [运行时规则引擎](../specs/SPEC-运行时规则引擎.md)

### 文件与流程

- [文件存储](../specs/SPEC-文件存储.md)
- [归档接收](../specs/SPEC-归档接收.md)

稳定规格与进行中变更见[能力规格索引](../specs/README.md)和[任务索引](../tasks/README.md)。规格尚未覆盖的接口不能仅凭本文成为稳定合同，应先补齐或澄清对应验收要求。

## 第三方协议边界

第三方固定协议只作为适配层例外，不反向改变项目自有 API 风格。

## 变更流程

修改项目自有 API 时：

1. 先查 `api-contract`、对应业务规格及合同引用的官方规范，再更新规格以明确资源、操作、字段、权限和验收场景；破坏性接口迁移还须说明调用方切换和兼容边界。
2. 同步修改 Controller、Request/Response 类型、前端类型和 API client。
3. 运行 `mise run governance-check`，并执行与前后端改动范围匹配的检查和测试任务。

当前实现清单以源码和测试为证据，本文不维护逐 Controller 路径快照。
