# API 使用与规格索引

本文提供当前 HTTP API 的使用入口和合同路由，不复制分页、错误、DTO、ID 或业务字段的完整规则。

## 真相源

| 内容 | Owner |
| --- | --- |
| 资源建模、URL、HTTP 方法、成功响应、DTO 命名、分页、过滤、排序、ID、异步任务和 ProblemDetail | [`api-contract`](../specs/SPEC-项目API合同.md) |
| 业务字段、状态机、权限边界和验收场景 | [`specs/`](../specs/) 下对应业务规格 |
| 当前默认端口、Session cookie、CORS 和请求签名配置 | [`application.yaml`](../backend/archive-server/src/main/resources/application.yaml) |
| 认证、授权、数据范围和公开入口的安全指引 | [`security.md`](security.md) |

项目自有 API 使用 `/api/v1` 前缀，默认由 Spring Boot 主应用提供。登录态保存在服务端 HTTP Session，浏览器使用配置的 session cookie 关联会话。除业务明确声明的公开入口和预检请求外，客户端应按服务端认证、授权、CSRF、CORS 和可选请求签名要求访问。

调用失败时，客户端按 `api-contract` 定义的 ProblemDetail 稳定字段处理，并保留 `traceId` 用于排障；不要依赖异常类名、HTML 错误页或自由文本推断错误类型。集合、分页和异步任务同样只按 `api-contract` 消费，不根据实现框架类型猜测合同。

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

CAP 等第三方固定协议只作为适配层例外，不反向改变项目自有 API 风格。

## 变更流程

修改项目自有 API 时：

1. 先更新 `api-contract` 或对应业务规格，明确资源、动作、字段、权限和验收场景。
2. 同步修改 Controller、Request/Response 类型、前端类型和 API client。
3. 运行 `mise run governance-check`，并执行与前后端改动范围匹配的检查和测试任务。

当前实现清单以源码和测试为证据，本文不维护逐 Controller 路径快照。
