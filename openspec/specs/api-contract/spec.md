# api-contract Specification

## Purpose

定义项目自有 HTTP API 的资源建模、路径、成功响应、错误响应和前端可见 ID 合同，确保新增接口按统一资源语义和可验证响应形态演进。

## Requirements

### Requirement: API 资源建模

项目自有 HTTP API SHALL 按资源导向模型设计。

#### Scenario: 暴露项目自有 API

- **WHEN** 系统新增项目自有 HTTP API
- **THEN** API SHALL 参考 Google Cloud API Design Guide / AIP 进行资源建模
- **AND** 路径 SHALL 在 `/api` 后包含主版本号，例如 `/api/v1`
- **AND** 系统 SHALL NOT 使用 `v1.0`、`v1.1` 或 `v1.4.2` 这类 minor/patch 版本路径

#### Scenario: 暴露标准 CRUD

- **WHEN** API 表达创建、查询、更新或删除资源
- **THEN** API SHALL 优先使用资源路径和 HTTP 方法表达标准操作
- **AND** 系统 SHALL NOT 直接按数据库表、页面按钮或服务方法名暴露接口

#### Scenario: 暴露自定义方法

- **WHEN** 标准方法无法自然表达动作
- **THEN** API SHALL 使用 AIP 风格冒号动作路径
- **AND** 动词 SHALL 使用 lower camelCase
- **AND** 有副作用、消费令牌、改变服务端状态或提交复杂请求体的自定义方法 SHALL 使用 `POST`

### Requirement: Controller 映射路径

Controller SHALL 显式声明完整 URL。

#### Scenario: 声明 Spring MVC 映射

- **WHEN** Controller 方法声明 Spring MVC 映射
- **THEN** 方法上的映射 SHALL 写完整 URL
- **AND** 系统 SHALL NOT 通过类级 `@RequestMapping` 叠加方法级相对路径生成项目自有 API
- **AND** 冒号动作 SHALL NOT 通过类级路径和 `@PostMapping(":action")` 拼接

### Requirement: API 成功响应

项目自有 API 成功响应 SHALL 直接返回资源对象或专用响应对象。

#### Scenario: 返回单个资源或动作结果

- **WHEN** API 创建、查询、更新资源或执行自定义动作成功
- **THEN** 响应 SHALL 直接返回资源对象或专用动作响应对象
- **AND** 系统 SHALL NOT 使用 `Result<T>` 这类统一包装层

#### Scenario: 返回集合

- **WHEN** API 返回资源集合
- **THEN** 响应 SHALL 使用专用集合响应对象
- **AND** 系统 SHALL NOT 直接暴露泛型分页类型
- **AND** 游标分页 SHALL 使用 `pageSize`、`pageToken` 和 `nextPageToken`
- **AND** offset 分页 SHALL 使用 `pageSize`、`pageOffset` 和 `totalSize`

### Requirement: API 错误响应

项目自有 API 错误响应 SHALL 使用 Spring `ProblemDetail` / RFC 9457 口径。

#### Scenario: 返回错误

- **WHEN** API 返回业务错误、校验错误或系统错误
- **THEN** 响应体 SHALL 保留 `type`、`title`、`status`、`detail` 和 `instance` 等标准字段
- **AND** 响应体 SHALL 通过扩展字段承载 `code`、`reason`、`fieldViolations`、`traceId` 和 `path`
- **AND** 字段级校验错误 SHALL 放在顶层 `fieldViolations: [{field, message}]`
- **AND** 前端 SHALL NOT 解析纯文本、HTML、异常类名或异常栈作为项目自有 API 错误合同

### Requirement: 前端可见 ID

明确指定的高增长或 JavaScript 精度风险资源 SHALL 避免 number 精度问题。

#### Scenario: 返回明确要求字符串化的数据库 Long 或 BigInt ID

- **WHEN** API 向前端返回已被规格或仓库规则明确要求字符串化的数据库 `Long` 或 `BigInt` ID
- **THEN** 返回字段 SHALL 输出为字符串
- **AND** 实体、Mapper 和 Service 内部 MAY 继续使用 `Long`
- **AND** 路径参数 MAY 接收字符串并在 Service 层解析校验

#### Scenario: 返回普通元数据配置 ID

- **WHEN** API 向前端返回档案分类、字段、布局、唯一规则等普通元数据配置 ID
- **THEN** 返回字段 MAY 继续输出为数字
- **AND** 系统 SHALL NOT 为未明确要求的低增长配置数据强制引入字符串 ID 迁移

#### Scenario: 返回资源主标识

- **WHEN** API 返回稳定资源表示
- **THEN** 资源表示 SHOULD 优先使用 AIP-122/AIP-148 的字符串 `name` 作为主标识

### Requirement: 第三方协议适配

第三方组件或外部协议强制要求的非 AIP 路径 SHALL 在客户端或适配层改写，不作为项目自有服务端 API 暴露。

#### Scenario: 适配 CAP widget 端点

- **WHEN** CAP widget 要求固定回调路径
- **THEN** 浏览器端 SHALL 通过 CAP 自定义 fetch 将 widget 内部请求改写为项目 AIP 风格 API
- **AND** 服务端 SHALL 暴露 `POST /api/v1/auth/cap-challenges`、`POST /api/v1/auth/cap-tokens` 和 `POST /api/v1/auth/cap-tokens:validate`
- **AND** 服务端 SHALL NOT 暴露 `/api/v1/auth/cap/challenge`、`/api/v1/auth/cap/redeem` 或 `/api/v1/auth/cap/validateToken`
- **AND** 服务端错误响应仍 SHALL 使用项目统一 `ProblemDetail` 结构
- **AND** 浏览器端 MAY 将 `ProblemDetail` 转换为 cap-widget 可识别的错误 JSON
