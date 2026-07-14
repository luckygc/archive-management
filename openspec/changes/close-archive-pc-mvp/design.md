## Context

档案条目查询、详情、锁定、解锁、删除、电子文件、审计、案卷、关系和明细表定义在 Spring Boot 中已有主要合同，但 PC 端只接通了其中一部分：搜索结果不能持续翻页，编辑器丢弃实物与参考数据字段，案卷、关系和明细表没有完整页面入口，路由权限与按钮权限不一致，工作台仍使用硬编码数据。明细行数据和用户数据范围内的工作台摘要尚缺服务端资源。

本变更横跨 Vue PC、档案 item 子域和 MyBatis 动态表查询。实现必须遵守现有 `api-contract` 和 `archive-metadata`，并通过 `archive-record-search` delta 增加 `volumeId` 固定筛选合同；服务端权限、数据范围和状态约束继续作为真相源，PC 保持 Element Plus 高密度企业工作台的既有设计语言。

## Goals / Non-Goals

**Goals:**

- 让档案搜索与管理页面可以稳定浏览超过 100 条结果，并在查询状态变化时正确重置游标。
- 接通档案生命周期、完整字段编辑、案卷、关系、明细表和明细行的 PC 操作闭环。
- 让菜单、分组、路由直接访问、页签和按钮使用一致的权限判断，同时保留服务端复核。
- 提供用户数据范围内的真实工作台摘要，并让关键读取区域在失败后保留上下文、原位重试。
- 以现有模块、组件、数据模型和持久化入口完成最小闭环，不新增平行抽象或运行时。

**Non-Goals:**

- 不实现归档接收、审批待办、文件预览集成、存储配置或系统参数。
- 不实现通用低代码表格设计器、第二套组件体系、前端权限真相源或新的基础设施适配层。
- 不改变全文 provider、通用分页形态、数字 ID、ProblemDetail 或既有档案元数据语义。
- 不因 Beta、Milestone、RC 等标签降级、替换或回退依赖。

## Decisions

### 1. 分页状态由各查询页面显式维护

档案管理与档案搜索分别维护草稿查询、已提交查询、排序、页大小、当前游标和响应导航游标。首次查询、提交筛选、修改排序或页大小时清除旧游标；翻页和刷新只复用已提交查询。请求继续使用 `POST /api/v1/archive-items:search` 或 `:discover`，`limit`、`cursor` 放在 URL query，`orderBy` 与业务条件放在 JSON 请求体，响应复用 `CursorPageResponse` 语义。

不抽取可配置 loader 的通用查询框架，因为管理查询与全文发现的业务边界不同，两个直接可读的组合式函数比参数矩阵更容易维护。超过 100 条且排序值重复的测试数据用于验证稳定兜底排序下无重复、无遗漏。

### 2. 生命周期和完整字段编辑复用现有档案资源

锁定、解锁、删除分别复用 `POST /api/v1/archive-items/{archiveItem}:lock`、`POST /api/v1/archive-items/{archiveItem}:unlock` 和 `DELETE /api/v1/archive-items/{archiveItem}`。锁定由 PC 强制收集原因，解锁和删除二次确认；成功后刷新当前已提交查询。状态、权限或并发冲突以服务端 ProblemDetail 为准，PC 只提前禁用已知无效动作。

详情和编辑使用同一份详情合同，完整承载密级、保管期限、实物字段值和动态字段值；密级与保管期限选项复用现有启用参考数据 API。选择直接复用既有详情与参考数据合同，避免为同一档案再维护一套编辑专用模型。

### 3. 案卷、关系和明细定义采用已有资源模型

案卷页面复用 `/api/v1/archive-volumes` 的创建、详情与 `:addItem` 动作，并将现有无界列表改为游标分页：`GET /api/v1/archive-volumes` 返回 `CursorPageResponse<ArchiveVolumeResponse>`，`limit`、`cursor` 位于 URL query，全宗、分类筛选和有效排序进入 cursor 查询摘要，默认按 `createdAt DESC, id DESC` 稳定排序。`ArchiveVolume` 是固定实体，因此仅把列表查询迁移到新建的 `ArchiveVolumeDataRepository`：Repository 显式 `@Find`，接收 `Restriction<ArchiveVolume>`、`PageRequest` 和包含 `_ArchiveVolume.createdAt.desc()`、`_ArchiveVolume.id.desc()` 的稳定 `Order<ArchiveVolume>`，返回 Jakarta Data `CursoredPage<ArchiveVolume>` 或当前 provider 的等价游标页。Service 在事务内消费 provider 页并转换为项目 `CursorPageResponse<ArchiveVolumeResponse>`，HTTP 合同不得暴露 provider 分页类型，也不能先全量加载后在内存中过滤或分页。案卷创建、详情与唯一性查询仍保留现有 MyBatis 实现，本变更不改造 `ArchiveMapper.xml` 中的案卷列表 SQL。

`POST /api/v1/archive-volumes/{archiveVolume}:addItem` 请求体固定为 `{ itemId, displayOrder? }`，同步完成后返回 `204 No Content`；Web 客户端返回 `Promise<void>`，不解析案卷响应。查看卷内档案通过 `SearchArchiveItemsRequest.volumeId` 固定字段筛选完成；`volumeId` 进入业务筛选和 cursor 查询摘要，URL query 中的分页控制字段仍不进入摘要，避免再造一套档案列表响应。

关系维护复用 `/api/v1/archive-items/{archiveItem}/relations` 子资源，并将现有无界列表改为 `CursorPageResponse<ArchiveItemRelationResponse>`。`limit`、`cursor` 位于 URL query，列表固定按 `id ASC` 稳定排序；保留的 `depth` 也是业务查询参数，必须进入 cursor 查询摘要。关系列表继续扩展现有 MyBatis Mapper：分页 SQL 同时连接目标档案摘要，并把 `ArchiveDataScopeFilter` 编译得到的 SQL 分组应用到目标档案，再应用 cursor 边界。不能先查一页关系再由 Repository 或 Service 逐行过滤目标档案，否则会产生不足一页、错误 `next` 或可见记录遗漏。该查询不新增平行 Repository、adapter 或关系分页状态源。目标档案从现有游标搜索结果中选择，禁止用户手输数据库 ID。案卷、关系和档案结果都复用现有 cursor 组件与项目分页合同，不新增通用分页框架。

分类配置页只接通已有明细表、明细字段和 `:build` API；字段类型、校验和渲染复用现有元数据类型，不引入拖拽或通用设计器。

### 4. 明细行作为档案与明细表下的子资源

新增以下项目 API，Controller 的每个方法显式写完整路径：

- `GET /api/v1/archive-items/{archiveItem}/line-tables`
- `GET /api/v1/archive-items/{archiveItem}/line-tables/{lineTable}/rows`
- `POST /api/v1/archive-items/{archiveItem}/line-tables/{lineTable}/rows`
- `PATCH /api/v1/archive-items/{archiveItem}/line-tables/{lineTable}/rows/{row}`
- `DELETE /api/v1/archive-items/{archiveItem}/line-tables/{lineTable}/rows/{row}`

条目范围的明细定义读取先校验档案读取权限与数据范围，只返回当前条目分类下已启用且已构建的明细表。响应使用独立只读视图，仅包含界面渲染所需的表、字段标识和展示信息，不暴露物理表名、物理列名或元数据管理属性；分类元数据维护接口继续要求元数据管理权限。

行列表使用统一游标响应，`limit`、`cursor` 通过 URL query 提交，固定按 `lineOrder ASC, id ASC` 排序。创建使用 `CreateArchiveItemLineRowRequest`，部分更新使用 `PatchArchiveItemLineRowRequest`，响应使用 `ArchiveItemLineRowResponse`。PATCH 中 `values` 对象缺失的字段键表示不修改，显式 `null` 表示清空对应动态字段；`lineOrder` 缺失表示不修改，显式 `null` 返回 `INVALID_ARGUMENT`。Controller 在现有 Jackson `JsonNode` 边界或等价的字段出现性表示中区分缺失与显式 null，再构造语义明确的请求对象，不为此引入新的 nullable 包。

Service 先校验档案数据范围、更新权限、档案可编辑状态、明细表已构建且属于档案分类，再把字段编码映射为元数据白名单列。动态表名和列名只能来自已验证定义，请求键和值不得直接拼接 SQL；MyBatis 负责动态行查询与写入，删除沿用逻辑删除字段。

不把明细行存入新的固定表，也不通过 Jakarta Data 操作动态表，因为现有动态明细表已经是唯一数据源，MyBatis 更适合白名单动态 SQL。

### 5. 工作台摘要复用档案查询的数据范围

新增 `GET /api/v1/workspace-summary`，返回 `WorkspaceSummaryResponse(archiveItemCount, draftCount, lockedCount, electronicFileCount)`。`ArchiveWorkspaceService` 枚举启用分类并调用另一个 Spring Bean 中的分类摘要方法；该方法复用档案查询已有的动态表、结构化条件和数据范围编译，再由 Service 汇总。这样搜索与摘要不会形成两套权限 SQL，也符合同一 Bean 的 public 方法不互调规则。

MVP 接受按启用分类逐次聚合的成本，因为它最小化权限语义分叉；后续只有在真实性能证据出现时才考虑物化摘要或批量聚合。工作台删除虚构待办，审批能力上线前不返回待办占位字段。

持久化入口按查询形态选择：固定实体的普通 CRUD 和固定字段普通分页优先使用 Jakarta Data；动态表、统计、复杂连接以及需要把数据范围编译进同一分页 SQL 的查询使用 MyBatis。因此案卷列表使用 Jakarta Data，关系列表、动态明细行和工作台摘要保持 MyBatis，避免把“固定表”误解为所有查询都必须经由同一种持久化入口。

### 6. 前端权限只提供一致的导航与操作体验

权限 Store 提供唯一 `has(code)` 判断：`superAdmin` 直接返回 true，普通用户检查服务端返回的权限码集合。初始化状态、超级管理员标记、权限码、校验时间、五分钟有效期和递增版本组成一个深度只读的冻结快照；内部可变引用不对外暴露。只有最新请求成功才整体替换；有效期内刷新失败保留上一成功快照，届满后原子标记过期并失败关闭，重置同时使在途响应失效。AppShell 是唯一会话期调度点：每 60 秒以及窗口 focus、页面 visible 时检查，只在未就绪或剩余有效期不超过 60 秒时刷新；同时为当前有效快照按 `validUntil` 维护一个可取消 timeout，刷新续期时取消旧 timeout 并重排，重置或卸载时清除，使失败关闭不依赖 60 秒 interval 是否与到期点对齐。所有触发复用单一在途请求，快速失败的自动请求按 60 秒节流；timeout 和 visible 回调仍先同步提交过期状态，再决定是否受节流影响发起请求。导航守卫在访问受保护路由前确保快照有效；过期校验失败显示可重试的“权限校验失败”，不冒充 403。

路由元数据通过 `permission` 声明单一权限，通过 `permissionsAnyOf` 声明满足任意一个即可访问的能力聚合页；菜单叶子、空分组、页签、按钮和导航守卫复用同一判断。权限撤销时先隐藏当前缓存内容并清理其他无权页签，当前页签只在最新 403 导航成功后删除；导航取消、拒绝或过期时保留页签并使用内联 403 收敛界面，用户离开该路由后再清理被保留的无权页签和缓存。

授权管理页只聚合 `authorization:permission:manage` 和 `archive:data-scope:manage` 两项能力：前者只维护角色功能权限，不请求数据范围接口；后者只维护角色、用户和部门的数据范围，不请求功能权限接口。页面用单个串行目录批次补载新获得能力的资源，按能力版本丢弃撤权前响应，并在撤权时清理专属主体和编辑状态。角色目录保留明确的相关管理权限读取；用户管理列表恢复为仅用户管理员读取，数据范围授权改用独立的 `GET /api/v1/authentication-user-options` 游标资源，响应只含 `id`、`username`、`displayName` 且仅数据范围管理员可读。目录写入与详情接口继续使用各自原有精确权限。服务端仍对每次敏感读写执行权限、数据范围和状态校验。

这比在菜单、页面和按钮中分别维护权限集合更少状态源，也避免仅靠隐藏菜单保护路由。专用用户选项资源消除了为完成数据范围授权而暴露邮箱、手机、部门、状态和创建时间的必要，写入不随页面聚合而放宽。

### 7. 读取失败在所属工作区原位恢复

档案搜索、档案管理、案卷和工作台的加载错误在对应内容容器内显示可执行的重试状态。错误保留草稿、已提交查询、排序、当前结果和编辑输入；重试重新执行同一请求。游标失效时明确提示数据已变化，并使用相同已提交查询从第一页重新开始。保存、锁定、解锁、删除等瞬时命令继续使用 Element Plus 消息反馈；字段级错误按 `fieldViolations` 回填。

共享 `RequestErrorState` 只负责展示消息并发出重试事件，不持有请求状态。页面继续使用 Element Plus 的表格、表单、Drawer、确认框和加载状态，不新增自定义视觉体系。

## Risks / Trade-offs

- [游标期间数据变化可能使旧 token 失效] → 展示可执行原因，清除 token 后从相同已提交查询第一页重试；服务端继续校验查询摘要、页大小和签名。
- [动态明细行 SQL 存在标识符注入风险] → 只接受元数据解析后的表名和列名，值全部参数绑定，并用 Service 与 Mapper 测试覆盖非法字段。
- [按分类统计在分类数量增大时查询次数上升] → 本阶段复用正确的数据范围语义并记录查询成本，只有真实性能证据出现后再优化。
- [前端提前禁用与服务端状态可能短暂不一致] → 服务端始终复核并返回 ProblemDetail，成功或冲突后刷新当前上下文。
- [保留旧结果时用户可能误认为数据已更新] → 错误状态与旧结果同时明确标识，加载与重试动作禁用重复提交。

## Migration Plan

1. 先补充 `volumeId` 查询、Jakarta Data 案卷游标 Repository、MyBatis 关系分页 SQL、动态明细行和工作台摘要服务端合同与测试，不改变现有数据库结构。
2. 再接通对应 PC API、页面、权限与错误状态；服务端先部署时旧 Web 不受影响，但新 Web 只能在游标分页服务端部署后上线。
3. 使用超过 100 条稳定测试数据、不同权限用户和失败响应完成定向与全量验证。
4. 更新 API、架构与 MVP 差距文档，并只在对应证据存在后勾选任务。
5. 回滚时先回滚 Web 入口，再回滚新增 Controller/Service；现有档案与动态明细数据无需迁移或转换。

## Open Questions

无。后续接收、审批、预览和系统设置分别由独立 OpenSpec 变更决定，不在本变更预留合同。
