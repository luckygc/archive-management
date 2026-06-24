# 仓库协作规则

## 基本要求

- 始终使用中文交流、编写文档和注释。
- 默认按最小闭环推进改动；不要把需求顺手扩展到相邻模块。
- 修改前先确认影响目录；如果后续新增更深层级的 `AGENTS.md`，必须优先读取最近层级规则，冲突时以更近目录规则为准。
- 规则、约定、架构边界类需求优先固化到仓库文档，再按文档口径改代码。

## 前端样式约定

- 前端样式默认使用组件库内置能力和默认视觉风格，优先通过组件 props、布局组件、结构调整和设计 token 完成界面实现。
- 业务系统、管理后台、工作台等非展示类界面默认以功能和操作效率为主；页面身份由侧边菜单、面包屑、页签和必要的工具栏上下文承担，内容区不要再额外放页面标题、说明型 `div`、营销式 hero、装饰卡片或展示页式大段引导文案。首屏应直接呈现筛选、表格、表单、详情、状态面板、操作栏等核心工作区；只有空状态、错误状态、不可逆操作提示或复杂业务规则确实需要解释时，才放最小必要说明。
- 不要为组件库已有的按钮、表单、表格、弹窗、菜单、标签、分页、页签等基础组件手写大段自定义 CSS；除非用户明确要求或组件库能力确有缺口，不要通过 `:deep(.el-*)`、全局 `.el-*` 选择器或组件内部 DOM 类名覆盖 Element Plus 的默认颜色、圆角、阴影、间距、交互态等视觉细节。
- 自定义 CSS 基本只用于页面布局、容器尺寸、栅格/弹性排布、滚动区域、间距组织和必要的响应式结构控制。
- 确需自定义视觉样式时，必须有明确业务或组件库能力缺口，并控制在局部范围内，避免形成全局覆盖和临时 hack。
- 业务系统的基础交互反馈默认即时响应。按钮、页签、菜单、表格行、工具栏入口等高频操作控件的 hover、focus、active、selected 状态默认不写 `transition`，颜色、背景、边框、透明度变化应立即生效。除侧边栏折叠、弹窗/抽屉显隐、列表项进入退出、加载中状态、进度反馈、骨架屏、异步提交反馈等确有状态过程含义的场景外，不主动添加过渡或动画。确需过渡时应控制在 120ms 以内，禁止设置 `transition-delay`，并提供 `prefers-reduced-motion: reduce` 降级；loading、progress、skeleton 这类表达等待或处理过程的动效可以保留，但应克制、循环稳定，不影响用户继续理解界面。

## 后端约定

- 后端当前按单 Spring Boot 应用起步；应用启动和总装配入口放在 `server/src/main/java/github/luckygc/am/app` 下，只承载启动类、应用级装配和启动期编排，不承载业务逻辑或具体技术适配。
- 业务功能统一放在 `server/src/main/java/github/luckygc/am/module` 下，并按业务边界拆子包，例如 `module/auth`；`module` 表示业务模块集合，不改名为 `business` 这类容易被理解成横切业务层的包名。
- 基础设施能力统一放在 `server/src/main/java/github/luckygc/am/infrastructure` 下，只承载技术适配，例如 Spring Security、Hibernate、文件存储、外部系统客户端、缓存和调度适配。
- `common` 只放跨业务模块共享的应用基础约定，不承载业务模块，也不承载具体外部技术适配；认证、用户、权限、档案、存储对象等业务语义不得放入 `common`。
- 业务模块不按 `api` / `internal` 继续拆包；当前是单体应用，不按微服务式模块合同组织代码。单个业务模块根包下，同一业务子域相关类达到 5 个及以上时，应按业务子域分包；分包依据是业务边界，不是机械按数量或按 Controller/Service/Repository 横切分层。子域包内部再次变大时，才继续按 `web`、`service`、`dto`、`repository`、`mapper` 等实现形态拆包。
- 跨业务模块协作允许直接依赖目标业务模块公开的类，但不要绕过目标模块已有 Service 去直接操作其 Repository、Mapper 或底层表；需要复用业务能力时优先抽出明确的 Service 方法。
- 控制器请求/响应 DTO 如果只服务本模块 HTTP 接口，可放在模块内的 `dto` 或 `web` 相关包下；只有确实跨多个模块复用时再提升为模块根下稳定类型。
- 后端架构边界通过 ArchUnit 测试固化；调整包结构、跨模块调用或公共类型落点时，必须同步维护对应架构测试。
- Java 代码格式化统一以 Spotless 调用 `google-java-format` 的 AOSP 风格为真相源，不再另行维护手写缩进、折行、空格、括号位置等细粒度格式规则；需要格式化时运行 Maven/Makefile 暴露的 Spotless 任务。
- Java 导入整理最终交给 Spotless：先按 `google-java-format` 处理格式，再按 `importOrder` 分组排序，并执行 `removeUnusedImports`。当前导入分组顺序为 `java|javax`、`jakarta`、`org`、`com`、`github`、其他导入、静态导入；不要手工调整成 IDE 默认顺序或 Checkstyle 口径。
- Java 字符串判空统一使用 Apache Commons Lang `StringUtils`，不要直接散写原生空值和空白判断组合。
- 摘要、编码、Hex 等通用加密/编码类方法优先使用 Apache Commons Codec 等成熟工具库，不要在业务代码里手写通用算法封装。
- 方法参数校验、请求参数校验这类纯校验操作不应该被事务包裹；只有确实需要原子写入、状态变更、消费令牌、扣减库存、锁定记录等场景才开启事务。
- 数据库和 Flyway 迁移以 PostgreSQL 为唯一优先目标；项目自有 DDL、索引、约束、查询、执行计划和迁移脚本可以围绕 PostgreSQL 能力优化，不为 MySQL、Oracle、SQL Server 等数据库做兼容性折中。除非明确新增其他数据库支持，不维护多数据库迁移分支。
- SQL 默认不使用双引号或反引号包裹标识符，也不要通过 `as "camelCase"` 这类 quoted alias 维持大小写；表名、列名、别名、索引名和约束名都使用小写 snake_case，让非法标识符尽早暴露。查询列名与结果名相同的场景不要写 `fonds_code as fonds_code` 这类冗余别名，只有表达式或确需更名时才使用小写 snake_case alias。动态 SQL 同样不要依赖自动引用标识符兜底，拼接前应校验标识符合法性。
- SQL、迁移验证和测试断言不得假定 schema 固定为 `public`；需要限定当前 schema 时使用 `current_schema()`、当前 search_path 下的未限定对象名，或使用配置提供的 schema。除第三方上游脚本确实要求外，不写 `public.`、`table_schema = 'public'` 这类硬编码。
- 搜索能力必须区分管理数据查询和普通用户搜索能力：管理列表、后台筛选、排序、权限过滤、精确字段筛选和统计查询固定使用数据库语义，不提供 adapter 配置，也不允许切到 Elasticsearch、OpenSearch、Solr、Meilisearch 或其他全文检索实现；全文检索只服务查档、借阅、利用服务等普通用户发现型场景。普通用户搜索必须在同一查询语义中合并全文条件、结构化筛选、权限判断和逻辑删除判断，不允许先从全文引擎召回裸 ID 再由业务代码二次过滤。全文检索不得在核心代码中绑定某个中间件产品，必须通过 `archive.search.full-text.adapter` 选择 `disabled`、`postgresql` 或后续注册的全文检索 adapter；内置 `postgresql` adapter 使用 PostgreSQL 自带 `pg_trgm` 扩展和 GIN 索引；配置启用但依赖能力缺失时必须启动 fail-fast。
- 运行时能力按 adapter/能力标识统一建模，但默认优先利用 Spring Boot AutoConfiguration、标准框架 Bean 和条件装配自动升级或降级，减少项目自定义配置项；不为已有成熟抽象重复声明项目端口。缓存使用 Spring Cache 和 `spring.cache.*` 配置，HTTP 会话直接使用 Spring Session 和 `spring.session.*` 配置，调度直接使用 Spring Quartz 和 `spring.quartz.*` 配置，三者都不进入项目运行时能力 adapter 模型；文件对象存储使用 S3 兼容协议；只有队列、运行时锁、运行时能力标识等缺少统一项目可用标准的能力才保留薄项目接口。最低部署组合是 `topology=single`、`database.adapter=postgresql`、`queue.adapter=database`、`lock.adapter=database`、`spring.quartz.job-store-type=jdbc`、`spring.cache.type=none`；集群部署不得使用本地锁、非共享本地文件存储或本地/禁用缓存，除非配置明确声明共享或切到分布式实现，配置不满足时必须启动 fail-fast。
- 运行时 adapter 支持自动或显式降级：`queue/lock` 可通过 `fallback-adapter` 在主 adapter 未接入时降到内置基础实现，例如队列降到 `database`。自动升级不能只看 classpath，必须基于显式配置、已注册标准 Bean 或真实连通性判断。Spring Session 由 Spring Boot/Spring Session 自己接管，项目不要再定义 `session.adapter`、`RuntimeSession` 或其他会话 adapter；Redis Session 按 Spring Session 依赖和 `spring.session.data.redis.*`、`spring.data.redis.*` 配置处理，不能和“Redis 业务缓存”混为一谈。缓存由 Spring Boot Cache 自己接管，项目不要再定义 `cache.adapter` 或自定义缓存端口；Redis 缓存按 Spring Cache/Boot 依赖、`spring.cache.*` 和 `spring.data.redis.*` 配置处理。调度由 Spring Quartz 自己接管，项目不要再定义 `scheduler.adapter`、`RuntimeScheduler` 或其他调度 adapter；关闭调度使用 `spring.quartz.auto-startup=false`，JDBC JobStore 使用 `spring.quartz.job-store-type=jdbc`。启用 Quartz JDBC 时必须能在当前 schema 下找到 `spring.quartz.properties.org.quartz.jobStore.tablePrefix` 对应的 Quartz 必需表，默认前缀是 `QRTZ_`。降级只能处理“主 adapter 未注册/未接入”这类可选能力缺口，不得掩盖数据库类型不匹配、集群使用本地锁、非共享本地存储、集群使用禁用/本地/无法确认共享性的缓存、Quartz JDBC 必需表缺失、Quartz 集群配置缺失等安全边界问题；这些场景必须 fail-fast。
- 缓存直接使用 Spring Cache 抽象，业务模块使用 `CacheManager` 或 Spring Cache 注解，不使用项目自定义缓存端口。默认 `spring.cache.type=none` 使用 `NoOpCacheManager`；单机可用 `simple`、`caffeine`、`cache2k` 等本地实现，集群必须使用 Redis、Hazelcast、Infinispan、Couchbase 等分布式实现，或接入数据库缓存、多级缓存等共享 `CacheManager` 并声明 `archive.runtime.cache.shared=true`。需要本地简单缓存、Redis、Caffeine+Redis、JetCache、Redisson 本地缓存等能力时，应优先使用 Spring Boot AutoConfiguration 或在基础设施层注册 Spring Cache 兼容的 `CacheManager`；引入多级缓存前必须先明确缓存对象范围、TTL、失效事件、集群一致性和脏读容忍度。
- Flyway 迁移，档案server的pom.xml里版本未达到1.0.0时，都按照目标结构修改，1.0.0之后使用增量迁移
- 项目不会跨时区使用；项目自有表时间字段使用无时区 `timestamp`，不使用 `timestamptz`。
- 项目自有数据库表必须使用 `am_模块_表名` 命名，例如 `am_auth_user`、`am_archive_file`、`am_storage_file`；不要只使用 `am_表名` 这类缺少模块语义的名称。Flyway 迁移中新建业务表、平台表、审计表、中间表等项目自有表时同样适用；索引、约束、序列等对象名称应跟随表名保持 `am_模块_` 语义。
- 项目自有表不允许使用数据库 `CHECK` 约束；枚举、状态、数值范围等校验放在应用层或字典/配置层。
- 实体枚举字段默认按文本持久化：Java enum 常量名直接使用对外和入库的小写 snake_case 值，实体属性标注 `@Enumerated(EnumType.STRING)`；不要依赖 `enum.ordinal()`，也不要为简单枚举手写 `AttributeConverter` 或额外注册 Jackson/MVC 枚举转换器。DDL 的 `comment on column` 必须列出每个枚举值及其含义。
- 带逻辑删除字段的项目自有表不允许使用覆盖已删除数据的唯一约束；唯一性必须使用 `where deleted_at is null` 的部分唯一索引，只约束未删除记录。
- 第三方框架原生表不属于项目自有表，例如 Spring Session 的 `SPRING_SESSION`、Quartz 的 `QRTZ_*`。除非明确改为项目自维护表，否则保留框架默认命名，避免偏离上游脚本。
- Flowable 使用 `flowable-spring-boot-starter-process` 的流程引擎能力，流程引擎由 starter 默认启用；不要配置无法被元数据解析的 `flowable.process.enabled`。默认禁用 Flowable IDM 与 Event Registry，只使用 `flowable.idm.enabled=false` 和 `flowable.eventregistry.enabled=false`，不要使用已弃用的 `flowable.db-identity-used`。
- Flowable 原生表由 Flyway 管理，运行期 `flowable.database-schema-update=false`。迁移脚本应从当前 Flowable 版本随包 PostgreSQL DDL 复制，当前只纳入 common、process engine、history 表；不要纳入 IDM 的 `ACT_ID_*` 表或 Event Registry 的 `FLW_EVENT_*` 表，除非明确启用对应引擎。
- 当前持久化入口是 Jakarta Data 和 MyBatis：固定 CRUD 表优先使用 Jakarta Data Repository；动态表、复杂 SQL、批处理、报表和认证适配查询统一使用 MyBatis；不要在项目代码里使用 JdbcClient 作为持久化入口，不要引入 Spring Data JPA。当前 Jakarta Data Repository 必须通过 Hibernate `StatelessSession` / `EntityAgent` 执行，不允许切换为普通有状态 `Session` 或引入依赖一级缓存、脏检查、延迟会话生命周期的写法。业务代码不得直接使用 Hibernate 有状态 `Session`、Hibernate `Query` 或其他依赖 Session 生命周期的对象；如确需 Hibernate 底层适配，只能封装在基础设施层，不能外泄为业务模块合同。Repository 对外不得返回 `Stream`、游标等依赖会话生命周期的对象，必须在 `@Transactional` 方法内消费完查询结果。
- 固定实体写入不要默认调用 Repository `save` 或定义 `@Save` 方法；新增、修改、删除应优先使用语义明确的 `insert` / `update` / `delete` 生命周期方法，Service 层先判断业务分支再调用对应方法。只有明确需要 upsert 语义且已说明并发、审计和唯一约束影响时，才允许使用 `save`。
- 固定实体的 `created_at`、`updated_at`、`created_by`、`updated_by` 应通过基础设施层无状态会话审计拦截器统一填充；其中操作人字段从 Spring Security 上下文读取当前 `AuthenticatedUser`。`@CreationTimestamp`、`@UpdateTimestamp` 可以保留为 Hibernate 实体写入路径的辅助生成注解，但不要把它们当成 `save`/upsert 或 MyBatis 写入路径的审计真相源。无状态会话 `onUpsert` 只能维护 `updated_at`、`updated_by`，不得覆盖 `created_at`、`created_by`；需要创建语义时必须走显式 `insert`。需要保留业务语义的操作流水继续写业务审计表。MyBatis 写入路径不会触发该拦截器，必须在 SQL 或调用方显式维护审计字段。
- StringUtils.removeStart已过时，替换为Strings.CS.removeStart

## API 设计约定

- 项目自有 HTTP API 默认遵循 Google Cloud API Design Guide / AIP；除 gRPC、protobuf、HTTP/gRPC transcoding 等传输和 IDL 专属内容外，资源建模、URL、标准方法、自定义方法、分页、过滤、字段命名、错误模型和兼容性规则都按 AIP 口径执行。
- 项目 API 设计任务优先使用 `.agents/skills/archive-api-design-strategy`；持久化入口、实体和 Mapper 边界任务优先使用 `.agents/skills/archive-persistence-strategy`。
- API URL 设计使用 Google Cloud API Design Guide / AIP 的资源导向模型：先识别资源名词、层级和标准方法，再决定是否需要自定义方法；不要直接按数据库表、页面按钮或服务方法名暴露接口。
- REST API 路径必须在 `/api` 后包含主版本号，例如 `/api/v1`；只暴露 `v1`、`v2` 这类主版本，不使用 `v1.0`、`v1.1`、`v1.4.2` 这类 minor/patch 版本。
- 标准 CRUD 优先使用资源路径和 HTTP 方法表达：`GET /api/v1/books/{id}` 查询单个资源，`GET /api/v1/books` 查询集合，`POST /api/v1/books` 创建，`PATCH /api/v1/books/{id}` 局部更新，`DELETE /api/v1/books/{id}` 删除。
- 只有标准方法无法自然表达的动作才使用自定义方法。自定义方法路径使用 AIP 风格冒号动作：`POST /api/v1/books/{id}:archive`、`POST /api/v1/auth:login`；动词使用 lower camelCase，不使用 `/archive`、`/_archive`、`/validate_token`、`/validateToken` 这类路径段承载项目自有动作。
- 自定义方法如果只读取数据且请求参数适合 query string，可使用 `GET`；有副作用、消费令牌、改变服务端状态或提交复杂请求体时使用 `POST`。
- 查询当前登录会话这类单例资源使用资源名表达，例如 `GET /api/v1/auth/session`；登录、退出等非 CRUD 动作使用 `POST /api/v1/auth:login`、`POST /api/v1/auth:logout`。
- Controller 方法上的 Spring MVC 映射必须写完整 URL，例如 `@GetMapping("/api/v1/books")`、`@PostMapping("/api/v1/books/{id}:archive")`；不要使用类级 `@RequestMapping` 叠加方法级相对路径。冒号动作尤其不能通过类级路径和 `@PostMapping(":action")` 拼接，避免实际映射路径与前端 API 合同不一致。
- 错误响应使用 AIP-193 的 HTTP/JSON 形态：响应体为 `{"error": {"code": 400, "message": "...", "status": "INVALID_ARGUMENT", "details": [...]}}`；`code` 使用 HTTP 状态码，`status` 使用 `google.rpc.Code` 的枚举名，`details` 优先使用 `google.rpc.ErrorInfo`、`google.rpc.BadRequest`、`google.rpc.LocalizedMessage`、`google.rpc.Help` 等标准 detail 的 JSON 表示。字段级校验错误放在 `BadRequest.fieldViolations`，不要让前端解析纯文本、HTML 或异常栈。
- 分页、过滤、排序、字段掩码、批量方法和长任务等 API 合同按 AIP 对应章节建模；只有第三方组件固定协议、框架回调或明确无法适配 AIP 的外部接口可以作为例外，例外必须限制在适配层，不得扩散为项目自有 API 风格。
- 资源表示优先使用 AIP-122/AIP-148 的字符串 `name` 作为资源主标识；短期保留 `id`、`categoryId`、`createdBy` 等字段时，所有返回给前端的数据库 `Long`/`BigInt` ID 都必须输出为字符串，避免 JavaScript number 精度问题。实体、Mapper 和 Service 内部可以继续使用 `Long`；路径参数可以接收字符串并在 Service 层解析校验。
- 第三方组件或外部协议强制要求的回调路径可以作为适配例外保留，例如 CAP widget 固定使用的 `/api/v1/auth/cap/challenge`、`/api/v1/auth/cap/redeem`、`/api/v1/auth/cap/validateToken`；这类例外不得扩散为项目自有 API 命名风格。

## 文件存储约定

- 文件存储统一通过 `FileStorageService` 使用；存储层只负责对象内容的上传、下载、删除和存在判断，不提供对象列举和元信息反查作为业务真相源。
- 文件信息必须落本地数据库；每条文件记录必须固化自身的 `storage_type`、`bucket_name` 和 `object_key`，不能用当前全局存储类型推断历史文件位置；默认上传位置通过 `archive.storage.adapter` 显式选择 `local`、`s3`、`minio`、`cos`、`oss` 或 `obs`，不再按“对象存储配置完整就自动优先”隐式切换；下载、删除、存在判断按文件记录中的 `storage_type` 和 `bucket_name` 分发；本地存储允许配置多个 bucket/root，默认上传为 `local` 时使用 `active-local-bucket` 指定的本地 bucket；对象存储只配置一个 bucket，不设置 active bucket。
- 对象存储适配 AWS S3、MinIO、腾讯 COS、阿里云 OSS、华为云 OBS，统一使用 S3 兼容协议和同一套对象存储配置。
- `objectKey` 统一通过 `ObjectKeys.generate(originalFilename)` 生成，格式为 `yyyy/MM/dd/{uuid-v7}.{ext}`；UUID v7 使用开源库 `uuid-creator`，不要手写 UUID v7。
- 文件扩展名解析使用 Apache Commons IO `FilenameUtils.getExtension`，路径分隔符归一化使用 `FilenameUtils.separatorsToUnix`。
- 文件内容指纹默认只要求保存 `checksum_sha256`；`checksum_md5` 仅作为历史系统或外部系统兼容备用字段；对象存储返回的 `etag` 只作为存储侧元信息，不作为 checksum 真相源。

## 文档查询

当用户询问库、框架、SDK、API、CLI 工具或云服务的用法、配置、版本迁移、调试和 setup 时，必须使用 Context7 查询当前文档；不要凭记忆回答。

1. 先解析库：

```bash
npx ctx7@latest library <name> "<用户完整问题>"
```

2. 选择最匹配的 `/org/project` ID，优先考虑名称精确匹配、描述相关性、代码片段数量、来源信誉和 benchmark 分数。
3. 再拉取文档：

```bash
npx ctx7@latest docs <libraryId> "<用户完整问题>"
```

4. 基于获取到的文档回答或实现。

不要把 API key、密码等敏感信息放入 Context7 查询。遇到配额错误时，告知用户可执行 `npx ctx7@latest login` 或设置 `CONTEXT7_API_KEY`。

## Vite+ 工具链

本项目使用 Vite+，这是构建在 Vite、Rolldown、Vitest、tsdown、Oxlint、Oxfmt 和 Vite Task 之上的统一前端工具链。Vite+ 通过全局 CLI `vp` 管理运行时、包管理和前端任务；它不同于 Vite，运行和构建应使用 `vp dev`、`vp build`。

- 查看命令列表：`vp help`
- 查看子命令说明：`vp <command> --help`
- 本地文档：`node_modules/vite-plus/docs`
- 在线文档：https://viteplus.dev/guide/
- Codex 不允许主动启动开发服务器；不要运行 `vp dev`、`vp run *#dev`、`npm run dev`、`pnpm dev`、`vite --host` 等长期占用端口的本地服务命令。需要预览时，只说明用户可自行启动的命令。

## 检查清单

- 拉取远程变更后、开始开发前运行 `vp install`。
- 前端变更运行 `vp check` 和 `vp test`，用于格式化、lint、类型检查和测试。
- 检查 `vite.config.ts` tasks 或 `package.json` scripts 中是否有必要验证命令，并通过 `vp run <script>` 执行。
- 如果 setup、运行时或包管理行为异常，运行 `vp env doctor` 并保留输出。
- 后端 Maven 项目根目录是 `server/`，仓库根目录没有聚合 POM；运行 `mvn ...` 验证时必须以 `server/` 为工作目录，例如 `cd server && mvn -q -DskipTests test-compile`。
- 后端改动至少运行对应 Maven 编译或测试；如果测试需要本机数据库，说明依赖和结果。
