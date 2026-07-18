# API 使用与规格索引

本文提供当前 HTTP API 的使用入口和合同路由，不复制分页、错误、DTO、ID 或业务字段的完整规则。

## 真相源

| 内容 | Owner |
| --- | --- |
| 资源建模、URL、HTTP 方法、成功响应、DTO 命名、分页、过滤、排序、ID、异步任务和 ProblemDetail | [`api-contract`](../openspec/specs/api-contract/spec.md) |
| 业务字段、状态机、权限边界和验收场景 | [`openspec/specs/`](../openspec/specs/) 下对应业务规格 |
| 当前默认端口、Session cookie、CORS 和请求签名配置 | [`application.yaml`](../server/src/main/resources/application.yaml) |
| 认证、授权、数据范围和公开入口的安全指引 | [`security.md`](security.md) |

项目自有 API 使用 `/api/v1` 前缀，默认由 Spring Boot 主应用提供。登录态保存在服务端 HTTP Session，浏览器使用配置的 session cookie 关联会话。除业务明确声明的公开入口和预检请求外，客户端应按服务端认证、授权、CSRF、CORS 和可选请求签名要求访问。

调用失败时，客户端按 `api-contract` 定义的 ProblemDetail 稳定字段处理，并保留 `traceId` 用于排障；不要依赖异常类名、HTML 错误页或自由文本推断错误类型。集合、分页和异步任务同样只按 `api-contract` 消费，不根据实现框架类型猜测合同。

## 业务规格索引

### 身份、权限和组织

- [登录与认证](../openspec/specs/login-authentication/spec.md)
- [功能权限](../openspec/specs/authorization-permissions/spec.md)
- [组织部门](../openspec/specs/organization-departments/spec.md)
- [档案数据范围](../openspec/specs/archive-data-scope/spec.md)

### 档案元数据与记录

- [分类方案](../openspec/specs/archive-classification-scheme/spec.md)
- [档案元数据](../openspec/specs/archive-metadata/spec.md)
- [档案记录搜索](../openspec/specs/archive-record-search/spec.md)
- [档案记录路由](../openspec/specs/archive-record-routing/spec.md)
- [档案导入导出](../openspec/specs/archive-import-export/spec.md)

### 治理与运行时规则

- [档案治理方案](../openspec/specs/archive-governance-scheme/spec.md)
- [治理工作台](../openspec/specs/archive-governance-workbench/spec.md)
- [运行时规则引擎](../openspec/specs/archive-local-rule-engine/spec.md)
- [运行时配置迁移与恢复](../openspec/specs/archive-runtime-check-portability/spec.md)

### 文件与流程

- [文件存储](../openspec/specs/file-storage/spec.md)
- [归档接收](../openspec/specs/intake/spec.md)
- [文件预览服务](../openspec/specs/file-preview-service/spec.md)

OpenSpec 总览与活动 change 状态见 [`openspec/README.md`](../openspec/README.md)。规格尚未覆盖的接口不能仅凭本文成为稳定合同，应先补齐或澄清对应 OpenSpec。

## 第三方和独立服务边界

CAP 等第三方固定协议只作为适配层例外，不反向改变项目自有 API 风格。

文件预览服务是独立运行面，不使用主应用 `/api/v1` 前缀。其接口、默认监听和运行方式以 [文件预览规格](../openspec/specs/file-preview-service/spec.md) 与 [`preview/README.md`](../preview/README.md) 为准。

## 变更流程

修改项目自有 API 时：

1. 先更新 `api-contract` 或对应业务规格，明确资源、动作、字段、权限和验收场景。
2. 同步修改 Controller、Request/Response 类型、前端类型和 API client。
3. 运行 `task governance-check`，并执行与前后端改动范围匹配的检查和测试任务。

当前实现清单以源码和测试为证据，本文不维护逐 Controller 路径快照。
