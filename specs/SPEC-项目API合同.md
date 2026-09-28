# 能力规格：项目API合同

构建命令、代码风格、测试策略和工程边界见[项目总规格](../SPEC.md)。

## 目标

定义项目自有 HTTP API 的设计依据，以及路径、响应、分页、错误和 ID 合同。

## 设计依据

项目自有 API 以本合同和对应业务规格为准。以下是截至 2026-09-28 对项目采用的外部规则所作的中文转述和执行约定，覆盖项目选定的 HTTP 线协议；实现者直接按本合同设计和验收，无须为已定规则重新比较外部规范。来源链接用于追溯，合同没有覆盖的新问题才需核对官方资料并补充决策。此处不是外部规范的原文副本，也不代表采纳各规范的全部条款。新接口不另设项目级分页字段、任务状态格式、错误扩展格式或统一数值阈值。本项目选择哪些规则适用、资源字段、权限和业务约束仍是项目决策。已有接口的合同在完成调用方迁移前继续有效，见下文“存量接口兼容合同”；不能把存量字段当作新接口的设计范例。选择理由见 [ADR-0001](../docs/adr/0001-project-api-style.md)。

### 规则来源与取舍

| 主题 | 新接口采用的规则 | 依据与边界 |
| --- | --- | --- |
| URL | 直接从根路径暴露复数资源名，不使用 `/api` 基路径；小写 `kebab-case` 路径段、无空段或尾斜杠，子资源按生命周期嵌套 | [Zalando：URLs](https://opensource.zalando.com/restful-api-guidelines/#urls)。 |
| 接口描述 | 对外提供 OpenAPI 描述，逐端点声明请求、响应、媒体类型及错误；业务字段与权限由对应业务规格确定 | [OpenAPI Specification](https://spec.openapis.org/oas/latest.html)、[Zalando：OpenAPI](https://opensource.zalando.com/restful-api-guidelines/#general-guidelines)。 |
| HTTP 方法与状态 | 按方法语义和实际结果使用 `GET`、`POST`、`PUT`、`DELETE` 与 `201`、`202` 等状态；`PATCH` 使用专门方法语义 | [RFC 9110](https://www.rfc-editor.org/rfc/rfc9110)、[RFC 5789](https://www.rfc-editor.org/rfc/rfc5789)。 |
| 表示与内容协商 | JSON 资源使用 `application/json`；客户端和服务端按 `Accept`、`Content-Type` 处理表示格式，不支持的请求媒体类型返回 `415` | JSON 的选择依据 [Zalando：JSON payload](https://opensource.zalando.com/restful-api-guidelines/#json-payload)；HTTP 媒体类型语义依据 [RFC 9110](https://www.rfc-editor.org/rfc/rfc9110)。上传、下载等非 JSON 表示由具体接口声明。 |
| 冒号动作 | 标准资源方法无法自然表达时，使用 `POST ...:lowerCamelCase`；只读且需要请求体的查询也可用 `POST ...:search` | [Google AIP-136](https://google.aip.dev/136) 明确要求先考虑标准方法，并为只读大请求允许 `POST`。[RFC 10008](https://www.rfc-editor.org/rfc/rfc10008) 已定义更明确的 `QUERY` 方法，但当前项目使用的 [Spring Web 7.0.8 `RequestMethod`](https://docs.spring.io/spring-framework/docs/7.0.8/javadoc-api/org/springframework/web/bind/annotation/RequestMethod.html) 尚未列出 `QUERY`；本项目现阶段选用 AIP-136 方式。 |
| 部分更新 | `PATCH` 使用 `application/merge-patch+json`，完整遵守 JSON Merge Patch 处理规则；更新结果仍受资源 schema、权限和业务不变量校验 | [RFC 5789](https://www.rfc-editor.org/rfc/rfc5789)、[RFC 7396](https://www.rfc-editor.org/rfc/rfc7396)。 |
| 并发更新 | 会发生并发修改且需防止覆盖的资源使用 `ETag` 与 `If-Match`；条件不成立返回 `412`，具体资源是否要求条件请求由接口声明 | [RFC 9110：Conditional Requests](https://www.rfc-editor.org/rfc/rfc9110)、[Zalando：HTTP headers](https://opensource.zalando.com/restful-api-guidelines/#http-headers)。 |
| 重试与幂等 | 遵守 HTTP 方法的幂等语义；有重复执行风险的 `POST`/`PATCH` 按接口选择条件键、业务唯一键或 `Idempotency-Key`，声明重试边界 | [RFC 9110：Method Properties](https://www.rfc-editor.org/rfc/rfc9110)、[Zalando：Idempotency](https://opensource.zalando.com/restful-api-guidelines/#http-requests)。`Idempotency-Key` 是 Zalando 指引，不伪称已由 RFC 标准化。 |
| 版本与兼容 | URL 不预置版本段；优先做兼容扩展并完成调用方迁移。仅表示结构无法兼容且必须并行存在时使用媒体类型版本；路径或方法语义变更不能靠媒体类型版本解决 | [Zalando：Compatibility](https://opensource.zalando.com/restful-api-guidelines/#compatibility) 建议避免版本化、禁止 URL 版本化，并将媒体类型版本限定于请求与响应的表示结构。 |
| 字段命名 | JSON 属性及 query 参数使用 `camelCase`；动态业务键作为数据保留原值 | `camelCase` 依据 [Azure REST API Guidelines：JSON / Query](https://github.com/microsoft/api-guidelines/blob/vNext/azure/Guidelines.md)；动态业务键保留原值是项目数据语义的边界，不是 Azure 规范的例外条款。 |
| 集合与分页 | 可增长集合支持分页；优先 `cursor`、`limit` 与分页 page object：`items` 和 `self`、`prev`、`next` 等导航字段；优先返回可供后续分页请求使用的链接，默认避免 `total`；确需请求总数时可支持 `Prefer: return=total-count` | [Zalando：Pagination](https://opensource.zalando.com/restful-api-guidelines/#pagination)；`Prefer` 请求头由 [RFC 7240](https://www.rfc-editor.org/rfc/rfc7240) 定义，`return=total-count` 是 Zalando 指引的选择性扩展，服务端可以不采纳。分页大小默认值及上限由具体接口文档给出。 |
| 页码跳转例外 | 真正需要跳转时使用 `offset`、`limit`，不引入 `pageNo`、`pageSize` 的第二套全局参数 | [Zalando：Conventional query parameters / Pagination](https://opensource.zalando.com/restful-api-guidelines/#urls)。 |
| 搜索与排序 | 简单筛选使用 query；复杂条件使用 JSON 请求体；标准 query 排序参数使用 `sort`，多个字段用逗号分隔、方向用 `+`/`-` 前缀 | [Zalando：HTTP requests / Conventional query parameters](https://opensource.zalando.com/restful-api-guidelines/#http-requests)。具体可排序字段及稳定性由接口文档定义。 |
| 长耗时动作 | 对已有资源执行 `POST ...:action` 并转后台时，按 Azure 模式返回 `202 Accepted`、`Operation-Id`、绝对 `Operation-Location` 和状态监视资源；监视资源使用 Azure 的 `id`、`status`、按条件出现的 `kind`、`error`、`result`，轮询遵守 `Retry-After`；任务历史可通过状态监视资源集合查询 | [Azure：Long-Running Operations & Jobs](https://github.com/microsoft/api-guidelines/blob/vNext/azure/Guidelines.md#long-running-operations--jobs)、[RFC 9110](https://www.rfc-editor.org/rfc/rfc9110)。本行只采用 Azure 的“已有资源上的长耗时动作”和“列出状态监视资源”模式，不照搬其 `api-version` query 或其他创建、删除模式。 |
| 错误 | `application/problem+json`，以 `type` 标识错误类型，使用 RFC 定义的成员；字段错误可参考 RFC 9457 的 `errors[{detail,pointer}]` 示例 | [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457)。示例中的 `errors` 是问题类型扩展，并非 RFC 强制的通用字段。 |
| 日期时间 | 时间点使用 RFC 3339 `date-time` 字符串，日期使用 `date`；对外时间点优先输出 UTC `Z`，业务本地时间的时区语义由接口声明 | [RFC 3339](https://www.rfc-editor.org/rfc/rfc3339)、[Zalando：Date and Time](https://opensource.zalando.com/restful-api-guidelines/#data-formats)。 |
| 缓存 | 依据数据敏感性和可变性声明 `Cache-Control`；认证挑战及其他不能存储的响应使用 `no-store`，可缓存资源的条件请求按 HTTP 语义处理 | [RFC 9111](https://www.rfc-editor.org/rfc/rfc9111)、[RFC 9110](https://www.rfc-editor.org/rfc/rfc9110)。具体缓存策略由接口声明。 |
| 文件响应 | 下载接口声明实际 `Content-Type`；需要建议保存文件名时使用 `Content-Disposition`，国际化文件名按标准编码 | [RFC 9110](https://www.rfc-editor.org/rfc/rfc9110)、[RFC 6266](https://www.rfc-editor.org/rfc/rfc6266)。上传字段、大小和权限由业务接口声明。 |
| ID 与内部类型 | 每类资源的 ID 类型在其接口 schema 中声明；JSON number 遵守可互操作整数范围；Java DTO 与 Controller 写法不充当 HTTP 线协议 | [RFC 8259 §6](https://www.rfc-editor.org/rfc/rfc8259)、[Zalando：OpenAPI specifications](https://opensource.zalando.com/restful-api-guidelines/#general-guidelines)；内部代码边界以[架构文档](../docs/architecture.md)为准。 |

本项目决定组合这些来源，并决定哪些资源使用条件请求、异步处理、总数偏好或缓存；外部规范不替项目规定业务权限、资源字段、ID 线类型、分页默认值及上限。动态业务键保持原值、当前不保留旧路径别名、Controller 路径写法也属于项目选择，不作为某个 RFC 或公司规范的原文要求。

### 本地执行示例

以下示例是本项目对已选规则的写法，不是从外部规范复制的原文；真实资源字段、权限和分页大小由对应业务规格及 OpenAPI 声明。

```http
GET /archive-items?limit=50&sort=-createdAt,%2Bid HTTP/1.1
Accept: application/json

HTTP/1.1 200 OK
Content-Type: application/json

{"items":[],"self":"https://example.org/archive-items?limit=50","next":"https://example.org/archive-items?cursor=opaque&limit=50"}
```

默认分页不计算总数。若接口支持按需总数，客户端可使用 `Prefer: return=total-count`，服务端可以不采纳；不能为此新增 `requestTotal`。分页链接包含后续请求所需的分页参数，cursor 对客户端不透明。

```http
PATCH /archive-items/42 HTTP/1.1
Content-Type: application/merge-patch+json

{"description":"修订说明","remark":null}
```

此处 `description` 被替换，`remark` 从资源表示中删除；未给出的成员保持不变。数组作为整体替换，必需字段和业务不变量仍由服务端校验。`remark` 只是示意字段，实际可更新字段由资源 schema 声明。

### 存量项目字段的规范替代

以下对照用于迁移，不表示旧字段已经废止，也不要求一个接口在迁移前同时支持两套协议。

| 存量合同 | 新接口选用的外部规范 |
| --- | --- |
| `requestTotal`、首页 count、全局 `limit=100/1000`、分页大小档位 | Zalando 分页推荐 `cursor`、`limit`、page object 并避免默认返回总数；需要时可采用 Zalando 的 `Prefer: return=total-count`，分页大小由各接口文档声明，不设项目通用档位。 |
| `self`、`prev`、`next` 纯 token | Zalando 优先使用包含分页参数的 URL；客户端沿用原 HTTP 方法和必要的搜索请求体，不自行拼接 token。 |
| `pageNo`、`pageSize`、强制 `total` | 需要位置跳转时采用 Zalando 的 `offset`、`limit`；默认不执行 count。 |
| `orderBy` 的自定义结构和固定 `createdAt DESC`、`id DESC` | Zalando 的 `sort=+field,-field`；接口文档声明可排序字段与稳定排序保证。 |
| `JobAcceptedResponse`、`jobId`、`operationLocation` 与自定义状态集合 | Azure 已有资源上的长耗时动作：`202`、`Operation-Id`、`Operation-Location` 和状态监视资源；状态值与条件字段按 Azure 定义。 |
| 必填的 `code`、`reason`、`fieldViolations`、`traceId`、`path` | RFC 9457 的 `type/title/status/detail/instance`；确需字段错误时按其 `errors[{detail,pointer}]` 示例定义特定问题类型。 |
| 全局 JSON number ID、可选 `name` | 资源 ID 的线类型写在接口 schema 中；数值 ID 必须处于 RFC 8259 可互操作范围。 |
| Controller 完整路径、Java DTO 命名和类型层次 | 属于内部代码规则，不能伪称外部 API 规范；留在架构与业务实现层管理。 |

GitHub REST API 的[页码与 `Link` 响应头](https://docs.github.com/en/rest/using-the-rest-api/using-pagination-in-the-rest-api)、[日期版本请求头](https://docs.github.com/en/rest/about-the-rest-api/api-versions)属于其具体产品协议，本项目未选用；不将它们误列为所选规则的来源。

## 验收要求
### 要求： API 资源建模与业务动作

项目自有 HTTP API SHALL 优先使用资源路径和标准 HTTP 方法；仅在操作具有独立业务语义且标准方法无法自然表达时，MAY 使用冒号动作。

#### 场景： 暴露项目自有 API

- **WHEN** 系统新增项目自有 HTTP API
- **THEN** API SHALL 遵守本合同定义的路径、字段、分页、兼容性和错误响应约定
- **AND** URL SHALL 优先使用资源名和标准 HTTP 方法表达业务语义

#### 场景： 命名资源 URL

- **WHEN** 新增或调整项目自有资源路径
- **THEN** 集合路径段 SHALL 使用表达业务资源的小写复数 `kebab-case` 名称，例如 `/archive-items`
- **AND** 单项资源 SHALL 在集合路径后追加资源标识符路径段，从属资源 MAY 再追加子资源集合路径段，路径 SHALL NOT 包含空段或尾斜杠
- **AND** 路径 SHALL NOT 用页面名、数据库表名或普通 CRUD 动词命名；业务命令按下文冒号动作规则表达

#### 场景： 维持已发布接口兼容

- **WHEN** 已发布接口需要调整路径或响应合同
- **THEN** 服务端 SHALL 保持兼容，或在对应业务规格和调用方完成迁移后再移除旧合同

#### 场景： 暴露标准 CRUD

- **WHEN** API 表达创建、查询、更新或删除资源
- **THEN** API SHALL 优先使用资源路径和 HTTP 方法表达标准操作
- **AND** 系统 SHALL NOT 直接按数据库表、页面按钮或服务方法名暴露接口

#### 场景： 修改资源字段

- **WHEN** 客户端只修改已有资源的部分普通可编辑字段
- **THEN** API SHALL 使用 `PATCH /{resources}/{id}` 和 [RFC 7396 JSON Merge Patch](https://www.rfc-editor.org/rfc/rfc7396)，请求 `Content-Type` SHALL 为 `application/merge-patch+json`
- **AND** 请求体及其效果 SHALL 遵守 RFC 7396：对象成员递归合并，未出现的成员保持不变；非对象补丁值替换整个目标，数组作为整体替换
- **AND** 显式 `null` SHALL 表示从资源 JSON 表示中移除该成员，不得解释为“设置成 JSON null”；必需字段不能删除时 SHALL 拒绝整个请求
- **AND** 允许删除的字段在更新后的资源表示中 SHALL 不再出现；对不存在字段提交 `null` 不产生该字段
- **AND** 需要把显式 JSON null 作为业务值保留的字段 SHALL 使用对应业务规格定义的其他资源操作，不得改变 RFC 7396 的 `null` 含义
- **AND** 服务端 SHALL 对资源 schema、权限、业务不变量和最终资源表示进行校验，并以原子方式应用或拒绝整个更新，不得通过普通 PATCH 绕过业务命令的独立规则
- **AND** 服务端 SHALL 拒绝其他 PATCH 文档媒体类型，使用 `415 Unsupported Media Type`；支持 PATCH 的资源 SHOULD 在 `OPTIONS` 响应中以 `Accept-Patch: application/merge-patch+json` 声明格式

#### 场景： 整体替换资源

- **WHEN** 客户端提交可替换资源的完整表示并要求整体替换
- **THEN** API MAY 使用 `PUT /{resources}/{id}`，并在业务规格中明确完整表示及缺失字段的处理
- **AND** API SHALL NOT 将部分字段更新伪装为整体替换

#### 场景： 表达标准方法之外的业务操作

- **WHEN** 标准方法无法自然表达动作
- **THEN** API SHALL 先判断操作产生的状态、请求或处理过程能否自然建模为资源
- **AND** 发布、审批、撤回、重建等标准方法无法自然表达的业务命令 MAY 对目标资源或集合使用 `POST ...:action`
- **AND** 冒号后的动作名 SHALL 使用 `lowerCamelCase`，并在对应业务规格中明确请求语义、权限、结果及失败路径
- **AND** 实现复杂度本身 SHALL NOT 成为新增冒号动作的理由；普通字段修改、标准 CRUD 和简单列表筛选 SHALL 使用标准方法
- **AND** 只读操作 SHALL NOT 因内部处理复杂而产生副作用；只有请求条件不适合放入 URL 时，MAY 按 Google AIP-136 使用 `POST ...:search` 等只读自定义方法

### 要求： API 路径与字段命名

项目自有 API SHALL 直接以资源路径开始，不使用 `/api` 或版本前缀，并优先兼容扩展。

#### 场景： 新增或修改项目自有 API

- **WHEN** 系统新增或兼容修改项目自有 HTTP API
- **THEN** 路径 SHALL 直接使用小写 `kebab-case` 资源路径段，例如 `/archive-items`
- **AND** JSON 属性和 query 参数 SHALL 按 Azure 规范使用 `camelCase`；表单字段以具体媒体类型和业务规格为准，Problem Details 成员按 RFC 9457 定义
- **AND** 动态档案字段等业务自定义键 SHALL 保留其原始键值，不得递归改写命名
- **AND** 新增可选字段、可选参数或端点 SHALL 兼容扩展，不因应用发布而增加版本号

#### 场景： 无法兼容地修改 API

- **WHEN** API 需要删除或重命名字段、改变字段类型或修改已发布操作语义等破坏性变更
- **THEN** 系统 SHALL 先评估兼容扩展或新增资源是否可行
- **AND** 仅表示结构无法兼容且必须并行提供时，MAY 按 Zalando 使用媒体类型版本，并在对应业务规格中明确媒体类型、调用方切换和旧版本退出条件
- **AND** 路径、HTTP 方法或操作语义发生破坏性变化时，SHALL 新增资源或动作，或先迁移调用方再移除旧合同；SHALL NOT 把媒体类型版本当作这些变化的兼容手段
- **AND** 系统 SHALL NOT 为该变更增加 URL 路径版本、日期查询参数或版本请求头

### 要求： 新接口的集合、异步与错误合同

新接口 SHALL 使用上文选定的外部规范已有的线协议；分页大小、允许排序的字段、资源 ID 类型及业务任务结果属于具体接口 schema，不设项目级统一数值或字段变体。

#### 场景： 返回可增长集合

- **WHEN** 新接口返回可增长集合
- **THEN** API SHALL 按 Zalando Pagination 使用 `cursor`、`limit` 和包含 `items` 的 page object，优先提供 `self`、`prev`、`next` 分页链接；调用方沿用原 HTTP 方法和必要的搜索请求体
- **AND** API SHALL 默认不计算 `total`；确需请求总数时，MAY 按 Zalando 支持 `Prefer: return=total-count`，服务端 MAY 不采纳该偏好；不得新增项目级 `requestTotal`、`pageNo`、`pageSize` 或固定分页大小档位
- **AND** 业务确需位置跳转时 MAY 按 Zalando 使用 `offset` 和 `limit`；该接口 SHALL 在业务规格中说明数据变化时的翻页语义

#### 场景： 返回异步动作

- **WHEN** 新接口的冒号业务动作开始长耗时处理
- **THEN** 服务端 SHALL 在可靠记录操作及其发起人、使任务可被后台接续和查询之后，按 Azure 模式返回 `202 Accepted`、`Operation-Id`、指向状态监视资源的绝对 `Operation-Location` URL 和状态监视资源表示；未成功接收任务时 SHALL 返回错误，不得虚报 `202`
- **AND** 请求 MAY 携带 `Operation-Id` 以标识同一次操作；相同 ID 但请求不同 SHALL 返回 `409 Conflict`，相同 ID 且请求相同 SHALL 按重试处理
- **AND** 状态监视资源 SHALL 使用 Azure 定义的字符串 `id` 和 `status`；`status` SHALL 取 `NotStarted`、`Running`、`Succeeded`、`Failed` 或 `Canceled`，多个操作类型共用监视端点时 SHALL 包含 `kind`
- **AND** 失败时的监视资源 `error` SHALL 使用 Azure `ErrorDetail`，动作成功且有结果时 MAY 包含 `result`；普通 HTTP 错误响应仍 SHALL 使用下文 RFC 9457 Problem Details
- **AND** 客户端 SHALL 通过 `Operation-Location` 以 `GET` 轮询；未结束时的监视响应 SHALL 提供 `Retry-After`，完成后监视资源 SHALL 至少保留 Azure 要求的 24 小时；用户任务需要更长查询或结果下载期限时由业务规格明确声明

#### 场景： 用户关闭浏览器后找回任务

- **WHEN** 用户启动长耗时任务后关闭页面、网络中断、刷新页面或会话过期
- **THEN** 浏览器连接断开 SHALL NOT 隐式取消服务端已接受的任务；执行和进度 SHALL 由服务端持久状态驱动，服务重启后 SHALL 能识别并继续或明确标记未完成任务，不依赖浏览器定时器维持执行
- **AND** 用户重新登录后 SHALL 可通过 `GET /operations` 查询其有权查看的任务，并通过 `GET /operations/{id}` 恢复状态监视；列表分页遵守本合同的 Zalando 分页规则，状态监视资源集合参考 Azure 的列表模式
- **AND** 服务端 SHALL 在查询任务列表、状态和结果时重新校验当前身份及业务权限；任务 ID 或 `Operation-Location` 不是授权凭据，权限撤销后 SHALL NOT 仅凭旧链接继续读取敏感结果
- **AND** 浏览器关闭、退出登录和权限变化是否导致后台任务取消 SHALL 由对应业务规格明确；默认 SHALL 仅停止客户端轮询，不自动取消已受理任务。主动取消如被业务支持，SHALL 是独立且经授权的操作
- **AND** 任务结果及生成文件的保留期限、过期后的响应、失败后重试方式 SHALL 由业务规格明确，不把 Azure 的最短 24 小时监视资源保留期误当作用户结果的保留期限
- **AND** 如果发起请求的响应丢失，客户端 MAY 使用相同 `Operation-Id` 重试，或在重新登录后从有权访问的任务列表找回操作；服务端 SHALL 防止相同操作意图被重复执行

上述“关浏览器继续执行、重新登录找回、按当前权限查看和结果保留”的约束来自本项目的用户任务需求；Azure 提供的是 `202`、状态监视资源、轮询和列表的 HTTP 表达方式，并不替本项目规定用户权限或后台执行可靠性。

```http
POST /archive-categories/42:rebuildSearchProjection HTTP/1.1
Operation-Id: op-123

HTTP/1.1 202 Accepted
Operation-Id: op-123
Operation-Location: https://example.org/operations/op-123
Content-Type: application/json

{"id":"op-123","status":"NotStarted"}
```

用户稍后通过任务列表找回 `op-123`，再对 `Operation-Location` 发起 `GET`；任务未结束时响应携带 `Retry-After`。示例的操作 ID 和地址只说明线协议，不规定实际 ID 生成方式或服务地址。

#### 场景： 返回新接口错误

- **WHEN** 新接口返回错误
- **THEN** API SHALL 使用 RFC 9457 的 `application/problem+json`，客户端 SHALL 以 `type` 识别问题类型
- **AND** 新接口 SHALL NOT 把存量 `code`、`reason`、`fieldViolations`、`traceId` 和 `path` 扩展当成全局必填字段
- **AND** 如果需要字段级校验错误，MAY 定义符合 RFC 9457 的特定问题类型，并参考其 `errors[{detail,pointer}]` 示例；该示例本身不是 RFC 强制字段

## 存量接口兼容合同

以下具体类型、字段、路径和响应约束仅约束尚未迁移的存量接口及其维护工作。迁移同一接口时，应同步修改业务规格、后端、前端 client/types 和测试；本节中的 `SHALL` 不覆盖上文对新接口的外部规范选择。

### 要求： Java HTTP 边界类型命名

项目自有 Java HTTP 边界类型 SHALL 按单一动作或真实响应视图命名。

#### 场景： 命名请求类型

- **WHEN** 系统为存量接口新增或调整 HTTP 请求类型
- **THEN** 类型名 SHALL 以 `Request` 结尾并表达单一动作或场景
- **AND** 新增请求 SHALL 使用 `CreateXxxRequest`，修改请求 SHALL 使用 `UpdateXxxRequest`
- **AND** 只有部分更新需要区分字段是否出现时，类型名 SHALL 使用 `PatchXxxRequest`
- **AND** 搜索或复杂条件请求 SHALL 使用 `SearchXxxRequest`，普通列表筛选请求 MAY 使用 `ListXxxRequest`
- **AND** 只有少量参数的简单 `GET` 请求 MAY 直接使用请求参数，无需机械创建请求类型
- **AND** 批量、导入、导出、预览和校验请求 SHALL 使用对应动作前缀
- **AND** 系统 SHALL NOT 使用泛化 `XxxRequest`、`SaveXxxRequest`、默认 `QueryXxxRequest`，或通过 `operationType` 加大量可选字段混合多个动作

#### 场景： 命名响应类型

- **WHEN** 系统为存量接口新增或调整 HTTP 响应类型
- **THEN** 类型名 SHALL 以 `Response` 结尾
- **AND** 列表项、详情、选择项、树节点等存在真实视图差异时，系统 SHALL 按视图语义命名和拆分响应类型
- **AND** 系统 SHALL NOT 直接使用持久化实体或 `VO` 作为 HTTP 响应合同
- **AND** 系统 SHALL NOT 使用包含大量可选字段的单一 Response 混合多个视图

### 要求： API 成功响应

项目自有 API 成功响应 SHALL 直接返回资源对象或专用响应对象。

#### 场景： 返回单个资源或动作结果

- **WHEN** API 创建、查询、更新资源或执行自定义动作成功
- **THEN** 响应 SHALL 直接返回资源对象或专用动作响应对象
- **AND** 系统 SHALL NOT 使用 `Result<T>` 这类统一包装层

#### 场景： 返回集合

- **WHEN** API 返回资源集合
- **THEN** 响应 SHALL 使用项目自有集合或分页响应对象
- **AND** 小规模、不需要分页的集合 SHALL 使用 `CollectionResponse<T>`
- **AND** 默认分页集合 SHALL 使用键集分页 `CursorPageResponse<T>`
- **AND** 只有对应业务规格明确声明允许 offset 分页时，offset 分页集合 MAY 使用 `OffsetPageResponse<T>`
- **AND** 三种响应对象 SHALL 以并列 record 表达，不通过继承、多态类型信息或框架分页父类表达 JSON 合同
- **AND** 系统 SHALL NOT 为每个资源分别设计 `archives`、`tasks`、`users` 这类资源复数字段响应对象
- **AND** 系统 SHALL NOT 直接暴露框架或持久化层的分页类型
- **AND** 系统 SHALL NOT 将 Jakarta Data、Hibernate、MyBatis 或其他持久化入口返回的分页对象直接序列化为 HTTP 响应
- **AND** 集合响应的列表字段 SHALL 固定使用 `items`
- **AND** 系统 SHALL 提供不带 `total` 的默认分页响应版本
- **AND** 系统 SHALL 提供带 `total` 的 offset 分页响应版本

### 要求： API 分页

可增长集合 API SHALL 使用项目统一分页响应，并默认使用键集分页；除非对应业务规格明确声明，项目自有 API SHALL NOT 使用 offset 分页。

#### 场景： 默认选择分页方式

- **WHEN** 系统维护尚未迁移的可增长集合 API
- **THEN** API SHALL 默认使用键集分页 `CursorPageResponse<T>`
- **AND** 请求 SHALL 支持 `limit` 和不透明 `cursor`
- **AND** 服务端 SHALL NOT 默认提供 `offset` 参数
- **AND** 服务端 SHALL NOT 默认返回 `total`
- **AND** 如确需总数，SHALL 优先通过 `:count` custom method 或明确请求参数单独表达
- **AND** 使用明确请求参数返回总数时，服务端 SHALL 只在未提交 `cursor` 的首页请求执行 count
- **AND** 带 `cursor` 的后续翻页请求 SHALL NOT 执行 count
- **AND** 当 count 成本不稳定或可能耗时较长时，系统 SHALL 将 count 设计为独立 `:count` 方法或异步任务，不得让默认分页列表隐式等待 count

#### 场景： 分页请求参数载体

- **WHEN** 客户端提交项目自有分页请求
- **THEN** 分页控制参数 SHALL 通过 URL query 参数提交
- **AND** cursor 分页接口 SHALL 使用 URL query 参数提交 `limit`、`cursor` 和 `requestTotal`
- **AND** offset 分页接口 SHALL 使用 URL query 参数提交 `pageSize` 和 `pageNo`
- **AND** 使用 JSON 请求体表达复杂查询条件的 cursor 搜索接口 SHALL 将 `orderBy` 放在同一个 JSON 请求体中
- **AND** offset 分页接口的 `orderBy` SHALL 通过 URL query 参数提交
- **AND** 服务端 SHALL NOT 从 JSON 请求体解析分页控制参数
- **AND** 服务端 SHALL 将 URL query 中的 `limit`、`cursor`、`pageSize`、`pageNo`、`offset` 和 `requestTotal` 视为分页控制字段，不纳入 cursor 查询摘要
- **AND** 服务端 SHALL 拒绝通过非 JSON 请求体提交分页请求

#### 场景： 请求 offset 分页集合

- **WHEN** 对应业务规格明确声明该集合规模可控、排序稳定且客户端需要页码跳转或默认总数
- **THEN** 请求 SHALL 支持 `pageSize` 和 `pageNo`
- **AND** 服务端 SHALL 校验 `pageSize` 上限
- **AND** 服务端 SHALL 为分页查询定义稳定排序
- **AND** 排序字段 SHALL 使用 API 字段名，并在进入 SQL 前通过白名单映射为数据库列
- **AND** 服务端 SHALL 追加唯一且稳定的兜底排序字段，例如 `id`
- **AND** 未经业务规格明确声明的接口 SHALL NOT 使用 offset 分页

#### 场景： 返回 offset 分页集合

- **WHEN** 业务规格明确声明 API 返回 offset 分页结果
- **THEN** 响应 SHALL 使用统一 page object
- **AND** 响应 SHALL 包含 `items`
- **AND** 响应 SHALL 包含 `pageSize`
- **AND** 响应 SHALL 包含 `pageNo`
- **AND** 响应 SHALL 包含 `total`
- **AND** 服务端 SHALL 将 `total` 作为单独 count 查询执行
- **AND** 服务端 SHALL NOT 将 count 查询隐藏在持久化框架分页对象序列化过程中

#### 场景： 请求键集分页集合

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

#### 场景： 用户自定义排序

- **WHEN** 客户端提交 `orderBy`
- **THEN** `orderBy` SHALL 使用 API 字段名和 `ASC` / `DESC` 方向
- **AND** 服务端 SHALL 通过白名单将 API 字段名映射为数据库列或安全表达式
- **AND** 服务端 SHALL 按客户端提交顺序优先应用用户自定义排序
- **AND** 服务端 SHALL 在用户自定义排序之后追加 `createdAt DESC` 和 `id DESC` 作为稳定兜底排序
- **AND** `createdAt DESC` SHALL 作为默认时间序兜底
- **AND** `id DESC` SHALL 保证排序键唯一
- **AND** 如果用户自定义排序已包含某个兜底字段，服务端 SHALL NOT 重复追加同一字段
- **AND** cursor 查询摘要 SHALL 包含完整排序列表
- **AND** 当用户修改排序时，客户端 SHALL 清空旧 `cursor` 并重新搜索
- **AND** 服务端 SHALL 拒绝未进入白名单的排序字段，并返回 ProblemDetail 错误

#### 场景： 用户修改分页大小

- **WHEN** 用户在当前结果列表中修改分页大小
- **THEN** 客户端 SHALL 使用当前已提交查询状态重新发起一次不带旧 `cursor` 的查询
- **AND** 新查询 SHALL 从第一页开始
- **AND** 客户端 SHALL 丢弃旧的 `self`、`prev` 和 `next` token
- **AND** 客户端 SHALL NOT 使用旧 cursor 请求新分页大小的上一页或下一页

#### 场景： 用户编辑搜索条件但尚未提交

- **WHEN** 用户在搜索表单中输入新的关键字、筛选条件、排序或分页大小但尚未点击搜索、回车提交或触发明确搜索动作
- **THEN** 客户端 SHALL 只更新搜索表单草稿状态
- **AND** 当前列表的已提交查询状态 SHALL 保持不变
- **AND** 上一页、下一页、刷新当前页等翻页请求 SHALL 继续使用当前列表的已提交查询状态和对应 `cursor`
- **AND** 客户端 SHALL NOT 将未提交的搜索表单草稿混入带 `cursor` 的翻页请求
- **AND** 用户提交搜索后，客户端 SHALL 用草稿生成新的已提交查询状态，并清空旧 `cursor`
- **AND** 新搜索响应返回前，客户端 MAY 保留旧列表显示，但 SHALL 将旧翻页 token 视为不可继续用于新搜索

#### 场景： 返回键集分页集合

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
- **AND** 响应默认 SHALL NOT 返回 `total`
- **AND** 当接口明确支持 `requestTotal=true` 且请求未提交 `cursor` 时，响应 MAY 返回与本次筛选条件一致的 `total`
- **AND** 当请求提交 `cursor` 时，即使 `requestTotal=true`，服务端 SHALL NOT 执行 count，响应 SHALL NOT 返回 `total`
- **AND** 服务端 SHALL NOT 为键集分页默认执行 count 查询
- **AND** `POST /{resources}:search` 返回的分页响应 MAY 包含 `query`，用于回显本次查询条件

#### 场景： 校验键集分页 cursor

- **WHEN** 客户端提交带 `cursor` 的键集分页请求
- **THEN** 服务端 SHALL 校验 cursor 的版本、方向、边界值、分页大小和查询摘要
- **AND** 需要防篡改的外部接口 SHALL 额外校验 cursor 签名
- **AND** cursor 签名密钥 SHALL 来自运行时配置或安全随机生成，不得使用源码内固定默认密钥
- **AND** 显式配置的 cursor 签名密钥 SHALL 满足最小长度要求
- **AND** 查询摘要 SHALL 覆盖首次请求的筛选、搜索、排序和业务范围参数
- **AND** `limit` SHALL 从查询摘要中排除，并作为 cursor 独立绑定字段校验
- **AND** cursor 查询摘要校验 SHALL 由通用请求处理组件执行，参数解析组件 SHALL 只解析分页参数，业务 Service SHALL NOT 重复实现 HTTP 请求一致性校验
- **AND** 如果当前请求参数与 cursor 绑定的查询摘要不一致，服务端 SHALL 拒绝请求
- **AND** 如果当前 `limit` 与 cursor 绑定的分页大小不一致，服务端 SHALL 拒绝请求并提示重新从第一页查询
- **AND** 服务端 SHALL 返回 `INVALID_ARGUMENT` 或 `FAILED_PRECONDITION` 类 ProblemDetail 错误
- **AND** 服务端 SHALL NOT 使用 cursor 中的客户端可见字段绕过服务端权限、数据权限或字段白名单校验

#### 场景： 返回总数

- **WHEN** 客户端需要总数
- **THEN** 系统 SHALL 通过 `POST /{resources}:count` 或显式请求参数单独表达
- **AND** 只有业务规格明确允许的 offset 分页响应 SHALL 返回 `total`
- **AND** 键集分页默认响应 SHALL NOT 返回 `total`
- **AND** 键集分页在 `requestTotal=true` 且未提交 `cursor` 的首页请求 MAY 返回 `total`
- **AND** 键集分页后续带 `cursor` 请求 SHALL NOT 执行 count 或返回 `total`
- **AND** 服务端 SHALL 将总数查询作为单独 count 查询执行，不得让默认列表查询隐式承担 count 成本
- **AND** 当总数查询可能超过交互式请求预算时，系统 SHALL 返回可轮询的异步 count 任务，或提供独立 `:count` 能力由客户端按需触发
- **AND** 大表或复杂查询入口 SHALL 优先使用键集分页，并通过单独 `:count` 方法表达总数需求，避免影响默认列表性能
- **AND** 如果显式返回总数，响应 SHOULD 区分精确总数和估算总数，例如 `totalExact`

### 要求： 交互式认证挑战的 202 响应

业务认证规格明确规定的短时效、不可授权、交互式二次验证挑战 MAY 使用不带操作监视资源的 `202 Accepted`。

#### 场景： 返回交互式二次认证挑战

- **GIVEN** 业务认证规格明确要求当前主体完成第二个认证因子
- **WHEN** 客户端已通过第一阶段凭证但整个认证流程尚未完成
- **THEN** 服务端 MAY 返回 HTTP `202 Accepted` 和短时效认证挑战
- **AND** 响应体 SHALL 使用对应认证规格定义的挑战结构，不得伪装成后台操作监视资源
- **AND** 响应 SHALL 设置 `Cache-Control: no-store`
- **AND** 服务端 SHALL NOT 因返回挑战而创建已认证会话、保存 SecurityContext 或授予任何业务权限
- **AND** 客户端 SHALL 通过认证规格定义的固定端点继续该交互，不得轮询任务资源

### 要求： API 错误响应

项目自有 API 错误响应 SHALL 使用 Spring `ProblemDetail` / RFC 9457 口径。

#### 场景： 返回错误

- **WHEN** API 返回业务错误、校验错误或系统错误
- **THEN** 响应 `Content-Type` SHALL 为 `application/problem+json`，响应体 SHALL 保留 `type`、`title`、`status`、`detail` 和 `instance` 等标准字段
- **AND** 响应体 SHALL 通过扩展字段承载 `code`、`reason`、`fieldViolations`、`traceId` 和 `path`
- **AND** 字段级校验错误 SHALL 放在顶层 `fieldViolations: [{field, message}]`
- **AND** 前端 SHALL NOT 解析纯文本、HTML、异常类名或异常栈作为项目自有 API 错误合同

### 要求： ID 合同

项目自有 API 的 ID SHALL 按当前系统规模保持简单一致。

#### 场景： 返回项目自有资源 ID

- **WHEN** API 向前端返回项目自有资源 ID
- **THEN** 默认 SHALL 返回 JSON number
- **AND** 后端实体、Mapper、Service 和 Controller 路径参数 MAY 使用 `Long`
- **AND** 前端类型 SHOULD 使用 `number`
- **AND** 系统 SHALL NOT 为尚未达到 JavaScript 安全整数风险的数据规模预先引入 Long 转字符串规则
- **AND** 只有外部协议或明确会超过安全整数范围的资源，才 SHALL 在对应业务规格中单独声明字符串 ID

#### 场景： 返回资源主标识

- **WHEN** API 返回稳定资源表示
- **THEN** 资源表示 MAY 提供稳定字符串 `name` 作为资源名
- **AND** 是否提供 `name` SHALL 以具体业务规格为准
