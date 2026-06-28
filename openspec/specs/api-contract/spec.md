# api-contract Specification

## Purpose

定义项目自有 HTTP API 的资源建模、路径、成功响应、分页、动作扩展、错误响应和 ID 合同，确保新增接口按统一资源语义和可验证响应形态演进。

## Requirements

### Requirement: API 资源建模

项目自有 HTTP API SHALL 以 Zalando RESTful API Guidelines 作为主体 REST 规范，并仅引入 Google AIP-136 custom method 作为复杂业务动作扩展。

#### Scenario: 暴露项目自有 API

- **WHEN** 系统新增项目自有 HTTP API
- **THEN** API SHALL 参考 Zalando RESTful API Guidelines 进行资源建模、HTTP 方法、JSON 响应、分页、兼容性和错误模型设计
- **AND** 路径 SHALL 在 `/api` 后包含主版本号，例如 `/api/v1`
- **AND** 系统 SHALL NOT 使用 `v1.0`、`v1.1` 或 `v1.4.2` 这类 minor/patch 版本路径

#### Scenario: 暴露标准 CRUD

- **WHEN** API 表达创建、查询、更新或删除资源
- **THEN** API SHALL 优先使用资源路径和 HTTP 方法表达标准操作
- **AND** 系统 SHALL NOT 直接按数据库表、页面按钮或服务方法名暴露接口

#### Scenario: 暴露自定义方法

- **WHEN** 标准方法无法自然表达动作
- **THEN** API SHALL 使用 Google AIP-136 风格冒号动作路径
- **AND** 动词 SHALL 使用 lower camelCase
- **AND** 有副作用、消费令牌、改变服务端状态或提交复杂请求体的自定义方法 SHALL 使用 `POST`
- **AND** 系统 SHALL 使用 `POST /api/v1/{resources}/{resourceId}:action` 表达单资源动作
- **AND** 系统 SHALL 使用 `POST /api/v1/{resources}:batchAction` 表达批量动作
- **AND** 系统 MAY 使用 `POST /api/v1/{resources}:search` 表达请求体复杂或查询条件较长的高级查询
- **AND** 系统 SHALL NOT 使用 `/lock`、`/_lock`、`/validate_token` 或 `/validateToken` 这类额外动作路径段

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
- **THEN** 响应 SHALL 使用项目自有集合或分页响应对象
- **AND** 小规模、不需要分页的集合 SHALL 使用 `CollectionResponse<T>`
- **AND** offset 分页集合 SHALL 使用 `OffsetPageResponse<T>`
- **AND** 键集分页集合 SHALL 使用 `CursorPageResponse<T>`
- **AND** 三种响应对象 SHALL 以并列 record 表达，不通过继承、多态类型信息或框架分页父类表达 JSON 合同
- **AND** 系统 SHALL NOT 为每个资源分别设计 `archives`、`tasks`、`users` 这类资源复数字段响应对象
- **AND** 系统 SHALL NOT 直接暴露框架或持久化层的分页类型
- **AND** 系统 SHALL NOT 将 Jakarta Data、Hibernate、MyBatis 或其他持久化入口返回的分页对象直接序列化为 HTTP 响应
- **AND** 集合响应的列表字段 SHALL 固定使用 `items`
- **AND** 系统 SHALL 提供不带 `total` 的默认分页响应版本
- **AND** 系统 SHALL 提供带 `total` 的 offset 分页响应版本

### Requirement: API 分页

可增长集合 API SHALL 使用项目统一分页响应，并按查询场景选择 offset 分页或键集分页。

#### Scenario: 请求 offset 分页集合

- **WHEN** API 返回规模可控、排序稳定且客户端需要页码跳转或总数的集合
- **THEN** 请求 SHALL 支持 `limit` 和 `offset`
- **AND** 服务端 SHALL 校验 `limit` 上限
- **AND** 服务端 SHALL 为分页查询定义稳定排序
- **AND** 排序字段 SHALL 使用 API 字段名，并在进入 SQL 前通过白名单映射为数据库列
- **AND** 服务端 SHALL 追加唯一且稳定的兜底排序字段，例如 `id`

#### Scenario: 返回 offset 分页集合

- **WHEN** API 返回 offset 分页结果
- **THEN** 响应 SHALL 使用统一 page object
- **AND** 响应 SHALL 包含 `items`
- **AND** 响应 SHALL 包含 `limit`
- **AND** 响应 SHALL 包含 `offset`
- **AND** 响应 SHALL 包含 `total`
- **AND** 服务端 SHALL 将 `total` 作为单独 count 查询执行
- **AND** 服务端 SHALL NOT 将 count 查询隐藏在持久化框架分页对象序列化过程中

#### Scenario: 请求键集分页集合

- **WHEN** API 返回大表、复杂查询、实时变化较多或不适合执行 count 的集合
- **THEN** 请求 SHALL 支持 `limit` 和不透明 `cursor`
- **AND** 默认 `limit` SHALL 为 `100`
- **AND** 通用前端分页大小 SHOULD 提供 `100`、`200`、`500`、`1000`
- **AND** 服务端 SHALL 校验 `limit` 上限，通用接口默认上限 SHALL 为 `1000`
- **AND** 需要更小或更大的特殊接口 SHALL 在对应业务规格中单独声明
- **AND** 服务端 SHALL 为分页查询定义稳定排序
- **AND** 排序字段 SHALL 使用 API 字段名，并在进入 SQL 前通过白名单映射为数据库列
- **AND** 服务端 SHALL 追加唯一且稳定的兜底排序字段，例如 `id`
- **AND** 客户端 SHALL NOT 解析、修改或构造 `cursor`
- **AND** 客户端 SHALL 在翻页请求中继续提交首次查询时相同的筛选、搜索、排序和分页大小参数，只替换 `cursor`
- **AND** 客户端需要变更筛选、搜索、排序或分页大小时，SHALL 重新发起一次不带旧 `cursor` 的查询

#### Scenario: 用户自定义排序

- **WHEN** 客户端提交 `orderBy`
- **THEN** `orderBy` SHALL 使用 API 字段名和 `ASC` / `DESC` 方向
- **AND** 服务端 SHALL 通过白名单将 API 字段名映射为数据库列或安全表达式
- **AND** 服务端 SHALL 按客户端提交顺序优先应用用户自定义排序
- **AND** 服务端 SHALL 在用户自定义排序之后追加 `createdAt DESC` 和 `id DESC` 作为稳定兜底排序
- **AND** `createdAt DESC` SHALL 作为默认时间序兜底
- **AND** `id DESC` SHALL 保证排序键唯一
- **AND** 如果用户自定义排序已包含某个兜底字段，服务端 SHALL NOT 重复追加同一字段
- **AND** cursor 查询指纹 SHALL 包含完整排序列表
- **AND** 当用户修改排序时，客户端 SHALL 清空旧 `cursor` 并重新搜索
- **AND** 服务端 SHALL 拒绝未进入白名单的排序字段，并返回 ProblemDetail 错误

#### Scenario: 用户修改分页大小

- **WHEN** 用户在当前结果列表中修改分页大小
- **THEN** 客户端 SHALL 使用当前已提交查询状态重新发起一次不带旧 `cursor` 的查询
- **AND** 新查询 SHALL 从第一页开始
- **AND** 客户端 SHALL 丢弃旧的 `self`、`prev` 和 `next` token
- **AND** 客户端 SHALL NOT 使用旧 cursor 请求新分页大小的上一页或下一页

#### Scenario: 用户编辑搜索条件但尚未提交

- **WHEN** 用户在搜索表单中输入新的关键字、筛选条件、排序或分页大小但尚未点击搜索、回车提交或触发明确搜索动作
- **THEN** 客户端 SHALL 只更新搜索表单草稿状态
- **AND** 当前列表的已提交查询状态 SHALL 保持不变
- **AND** 上一页、下一页、刷新当前页等翻页请求 SHALL 继续使用当前列表的已提交查询状态和对应 `cursor`
- **AND** 客户端 SHALL NOT 将未提交的搜索表单草稿混入带 `cursor` 的翻页请求
- **AND** 用户提交搜索后，客户端 SHALL 用草稿生成新的已提交查询状态，并清空旧 `cursor`
- **AND** 新搜索响应返回前，客户端 MAY 保留旧列表显示，但 SHALL 将旧翻页 token 视为不可继续用于新搜索

#### Scenario: 返回键集分页集合

- **WHEN** API 返回键集分页结果
- **THEN** 响应 SHALL 使用统一 page object
- **AND** 响应 SHALL 包含 `items`
- **AND** `self`、`prev`、`next` 和 `first` 等分页导航字段 SHALL 使用不透明 token，不使用 URL 链接
- **AND** 响应 MAY 包含 `self`
- **AND** 存在上一页时响应 SHALL 包含 `prev` token
- **AND** 存在下一页时响应 SHALL 包含 `next` token
- **AND** 没有上一页或下一页时，`prev` 或 `next` MAY 省略或返回 `null`
- **AND** 响应 MAY 包含 `first` token
- **AND** 大数据量集合 SHOULD NOT 提供 `last`
- **AND** 响应 SHALL NOT 返回 `total`
- **AND** 服务端 SHALL NOT 为键集分页默认执行 count 查询
- **AND** `POST /api/v1/{resources}:search` 返回的分页响应 MAY 包含 `query`，用于回显本次查询条件

#### Scenario: 校验键集分页 cursor

- **WHEN** 客户端提交带 `cursor` 的键集分页请求
- **THEN** 服务端 SHALL 校验 cursor 的签名、版本、方向、边界值和查询指纹
- **AND** 查询指纹 SHALL 覆盖首次请求的筛选、搜索、排序、分页大小和业务范围参数
- **AND** 如果当前请求参数与 cursor 绑定的查询指纹不一致，服务端 SHALL 拒绝请求
- **AND** 服务端 SHALL 返回 `INVALID_ARGUMENT` 或 `FAILED_PRECONDITION` 类 ProblemDetail 错误
- **AND** 服务端 SHALL NOT 使用 cursor 中的客户端可见字段绕过服务端权限、数据权限或字段白名单校验

#### Scenario: 返回总数

- **WHEN** 客户端需要总数
- **THEN** 系统 SHALL 通过 `POST /api/v1/{resources}:count` 或显式请求参数单独表达
- **AND** offset 分页响应 SHALL 返回 `total`
- **AND** 键集分页响应 SHALL NOT 返回 `total`
- **AND** 服务端 SHALL 将总数查询作为单独 count 查询执行，不得让默认列表查询隐式承担 count 成本
- **AND** 大表或复杂查询入口 SHALL 优先使用键集分页，并通过单独 `:count` 方法表达总数需求，避免影响默认列表性能
- **AND** 如果显式返回总数，响应 SHOULD 区分精确总数和估算总数，例如 `totalExact`

### Requirement: 异步任务与 202 响应

长耗时或异步执行的项目自有 API SHALL 参考 Microsoft Azure REST API Guidelines 的 long-running operation 模式，使用 `202 Accepted` 和可轮询任务资源表达。

#### Scenario: 启动异步任务

- **WHEN** API 启动导入、导出、批处理、外部同步、AI/OCR 或其他无法在当前请求内稳定完成的任务
- **THEN** 服务端 SHALL 返回 HTTP `202 Accepted`
- **AND** 响应体 SHALL 使用 `JobAcceptedResponse`
- **AND** 响应体 SHALL 包含 `jobId`、`status` 和 `operationLocation`
- **AND** `operationLocation` SHALL 指向可轮询的任务资源路径，例如 `/api/v1/archive-import-jobs/{jobId}`
- **AND** 服务端 SHOULD 同时返回 `Operation-Location` 响应头
- **AND** 服务端 MAY 返回 `Retry-After` 响应头提示客户端轮询间隔
- **AND** 已同步完成且不产生后台任务的动作 SHALL NOT 伪造 `202 Accepted`

#### Scenario: 查询异步任务状态

- **WHEN** 客户端查询任务资源
- **THEN** 服务端 SHALL 返回 `JobStatusResponse`
- **AND** 响应体 SHALL 包含 `jobId`、`status`、`progress`、`createdAt` 和 `updatedAt`
- **AND** `status` SHALL 至少覆盖 `queued`、`running`、`succeeded`、`failed` 和 `cancelled`
- **AND** 成功完成的任务 MAY 在 `result` 中返回结果摘要或结果资源位置
- **AND** 失败任务 SHALL 返回稳定 `errorCode` 和可展示 `errorMessage`

### Requirement: API 错误响应

项目自有 API 错误响应 SHALL 使用 Spring `ProblemDetail` / RFC 9457 口径。

#### Scenario: 返回错误

- **WHEN** API 返回业务错误、校验错误或系统错误
- **THEN** 响应体 SHALL 保留 `type`、`title`、`status`、`detail` 和 `instance` 等标准字段
- **AND** 响应体 SHALL 通过扩展字段承载 `code`、`reason`、`fieldViolations`、`traceId` 和 `path`
- **AND** 字段级校验错误 SHALL 放在顶层 `fieldViolations: [{field, message}]`
- **AND** 前端 SHALL NOT 解析纯文本、HTML、异常类名或异常栈作为项目自有 API 错误合同

### Requirement: ID 合同

项目自有 API 的 ID SHALL 按当前系统规模保持简单一致。

#### Scenario: 返回项目自有资源 ID

- **WHEN** API 向前端返回项目自有资源 ID
- **THEN** 默认 SHALL 返回 JSON number
- **AND** 后端实体、Mapper、Service 和 Controller 路径参数 MAY 使用 `Long`
- **AND** 前端类型 SHOULD 使用 `number`
- **AND** 系统 SHALL NOT 为尚未达到 JavaScript 安全整数风险的数据规模预先引入 Long 转字符串规则
- **AND** 只有外部协议或明确会超过安全整数范围的资源，才 SHALL 在对应业务规格中单独声明字符串 ID

#### Scenario: 返回资源主标识

- **WHEN** API 返回稳定资源表示
- **THEN** 资源表示 MAY 提供稳定字符串 `name` 作为资源名
- **AND** 是否提供 `name` SHALL 以具体业务规格为准

### Requirement: 第三方协议适配

第三方组件或外部协议强制要求的非项目 REST 路径 SHALL 在客户端或适配层改写，不作为项目自有服务端 API 暴露。

#### Scenario: 适配 CAP widget 端点

- **WHEN** CAP widget 要求固定回调路径
- **THEN** 浏览器端 SHALL 通过 CAP 自定义 fetch 将 widget 内部请求改写为项目 REST API
- **AND** 服务端 SHALL 暴露 `POST /api/v1/auth/cap-challenges`、`POST /api/v1/auth/cap-tokens` 和 `POST /api/v1/auth/cap-tokens:validate`
- **AND** 服务端 SHALL NOT 暴露 `/api/v1/auth/cap/challenge`、`/api/v1/auth/cap/redeem` 或 `/api/v1/auth/cap/validateToken`
- **AND** 服务端错误响应仍 SHALL 使用项目统一 `ProblemDetail` 结构
- **AND** 浏览器端 MAY 将 `ProblemDetail` 转换为 cap-widget 可识别的错误 JSON
