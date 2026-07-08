# 仓库协作规则

## 基本要求

- 始终使用中文交流、编写文档和注释。
- 默认按最小闭环推进改动；不要把需求顺手扩展到相邻模块。
- 项目必须遵守奥卡姆剃刀原则：能用现有框架、现有组件、现有数据模型或更少代码清楚解决的问题，不新增抽象、适配层、配置开关、兼容分支或未来预留能力；只有真实需求已经出现且收益大于复杂度时，才引入额外机制。

## 工程判断原则

- 代码优先追求可维护、可排障和可演进，不追求炫技、框架感或抽象层数量；好的实现可以朴素甚至无聊，但需求变化、线上事故、回滚和接手维护时必须站得住。
- 写代码时默认为下一位维护者降低理解成本：命名表达业务意图，控制流直接，边界条件清楚，异常路径明确；不要把复杂性藏进过度通用的 helper、魔法配置或隐式约定里。
- 新增抽象前先确认真实重复、真实边界和真实收益；只有当抽象能减少实质复杂度、统一稳定合同或隔离确定变化点时才引入。为了“以后可能会用”而新增的适配层、工厂、开关和兼容分支默认不接受。
- 优先让副作用集中且可追踪：数据库写入、远程调用、缓存失效、权限判断、事务边界、文件读写和状态变更不要散落在表现层或无关 helper 中。
- 实现必须覆盖失败路径和恢复路径：空值、权限不足、并发冲突、外部服务失败、部分成功、重复提交、回滚和重试策略都应按风险处理；不要只交付 happy path。
- 类型、校验、状态机和数据库约束应共同守住业务不变量；前端校验和 UI 状态只改善体验，不能替代服务端权限、数据权限、持久化约束和审计。
- 选型和实现默认选择能减少长期问题的方案：少一套状态源、少一层同步、少一个运行时、少一个自研基础设施；确需引入新技术时，先说明它消除的具体复杂度和新增的维护成本。
- 表单、表格、接口、权限和缓存这类后台系统高频能力，默认先明确唯一状态源、服务端合同、错误回填和刷新策略，再写页面代码；不要让同一份数据同时被多个框架、store、组件内部状态或前后端约定重复管理。
- 选择组件或库时，必须好了协议及商业化行为，不把 Enterprise/Pro 组件能力当成默认基础能力，不用采用了限制商业行为的库。
- 重复代码治理先判断重复背后的业务语义是否一致：语义不同的重复可以暂时保留，语义稳定且变更方向一致时再抽公共组件、hook、service 或规格；抽象后必须让调用方更少知道细节，而不是增加参数矩阵。

## 前端样式约定

- 遵守PRODUCT.md和DESIGN.md,涉及到前端，先阅读这两个文件和使用相关技能。

## 后端约定

- 后端当前按单 Spring Boot 应用起步；应用启动和总装配入口放在 `server/src/main/java/github/luckygc/am/app` 下，只承载启动类、应用级装配和启动期编排，不承载业务逻辑或具体技术适配。
- 业务功能统一放在 `server/src/main/java/github/luckygc/am/module` 下，并按业务边界拆子包，例如 `module/authentication`；`module` 表示业务模块集合。
- 基础设施能力统一放在 `server/src/main/java/github/luckygc/am/infrastructure` 下，只承载技术适配，例如 Spring Security、Hibernate、文件存储、外部系统客户端、缓存和调度适配。
- `common` 只放跨业务模块共享的应用基础约定，不承载业务模块，也不承载具体外部技术适配；认证、用户、权限、档案、存储对象等业务语义不得放入 `common`。
- 模块下先按业务子域拆包，例如 `module/archive/metadata`、`module/archive/item`；子域内再按实现职责分层：`web` 放 Controller 和只服务 HTTP 的请求/响应对象，`service` 放业务用例编排和事务边界，`manager` 放确有必要拆出的领域编排或跨多个 Service 复用的非 HTTP 协作，`repository` 放 Jakarta Data Repository，`mapper` 放 MyBatis Mapper 接口和 SQL 条件对象，`dto` 只在对象确需跨 web/service 复用且不适合继续作为 Service 内部 record 时使用。实体、枚举和值对象默认留在业务子域根包，除非数量或语义边界已经需要继续分包。
- 模块内依赖方向默认为 `web -> service -> manager -> repository/mapper`；`web` 不直接依赖 `repository` 或 `mapper`，`repository` / `mapper` 不依赖 `web`、`service` 或 `manager`。同一子域内可以按需要直接使用实体、枚举和值对象；跨业务模块复用能力时优先依赖目标模块已有 Service，不绕过 Service 操作对方的 Repository、Mapper 或底层表。
- 跨业务模块协作允许直接依赖目标业务模块公开的类，但不要绕过目标模块已有 Service 去直接操作其 Repository、Mapper 或底层表；需要复用业务能力时优先抽出明确的 Service 方法。
- 控制器请求/响应 DTO 如果只服务本模块 HTTP 接口，可放在模块内的 `dto` 或 `web` 相关包下；只有确实跨多个模块复用时再提升为模块根下稳定类型。
- 后端对象命名和拆分遵循奥卡姆剃刀原则：不采用 `DO` / `BO` / `VO` 作为默认分层后缀，不为每一层机械复制对象；固定表 Jakarta Persistence 实体在模块内部 Service、Repository 协作中可直接流转。跨越 HTTP 合同边界时使用语义明确的 `Request` / `Response`，并避免把持久化实体直接作为 HTTP 响应合同。
- HTTP 请求 DTO 统一以 `Request` 结尾，但类名必须表达具体动作或场景，不使用泛化的 `XxxRequest` 承载新增、修改、查询、批量操作等多种语义。正例：`CreateArchiveCategoryRequest`、`UpdateArchiveCategoryRequest`、`SearchArchiveRecordsRequest`、`ListArchiveCategoriesRequest`、`BatchDeleteArchiveItemsRequest`、`ImportArchiveItemsRequest`、`ExportArchiveRecordsRequest`、`PreviewArchiveImportRequest`、`ValidateArchiveRuleRequest`。反例：`ArchiveCategoryRequest`、`ArchiveRecordRequest`、`ArchiveRequest`、`SaveArchiveCategoryRequest`、`QueryRequest`、`BaseArchiveRequest`。
- 新增请求使用 `CreateXxxRequest`；整体修改或语义明确的编辑使用 `UpdateXxxRequest`；如果接口合同明确采用部分更新语义且需要区分字段是否出现，可使用 `PatchXxxRequest`。不要用 `SaveXxxRequest` 混合 create/update，也不要在项目自有写入接口中引入 upsert 语义。
- 查询请求按接口语义命名：搜索型、条件多或使用 custom method 的接口使用 `SearchXxxRequest`；普通列表筛选可使用 `ListXxxRequest`；简单 `GET` 查询只有少量 `@RequestParam` 且不会复用校验、不会导致 Controller 参数膨胀时，可以不新增请求 DTO。不要用 `QueryXxxRequest` 或 `XxxQueryRequest` 作为默认命名，除非已有上下文中 `query` 是明确业务概念。
- 批量、导入导出、预览、校验等动作请求使用动作前缀，例如 `BatchApproveTransferTasksRequest`、`ImportArchiveItemsRequest`、`ExportArchiveRecordsRequest`、`PreviewArchiveImportRequest`、`ValidateArchiveUniqueRuleRequest`；不要把这些字段塞进创建或修改请求，也不要靠大量可选字段和 `operationType` 在一个 DTO 内分流。
- HTTP 响应 DTO 统一以 `Response` 结尾，并按前端视图语义拆分。正例：`ArchiveCategoryResponse`、`ArchiveCategoryListItemResponse`、`ArchiveCategoryOptionResponse`、`ArchiveCategoryTreeNodeResponse`、`ArchiveItemDetailResponse`。反例：直接返回 `ArchiveCategory` 实体、`ArchiveCategoryVO`、字段大量可选的 `ArchiveCategoryResponse` 同时服务列表/详情/选择器/树节点。
- 只有请求场景字段差异明显、前端列表/详情/选择器/树节点等视图字段差异明显、可选字段过多影响类型表达，或确需隔离业务命令和跨模块调用时，才新增 `Command`、`Detail`、`Summary`、`Option`、`TreeNode` 等对象。Service 内部对象命名应表达业务用途，例如 `CreateArchiveCategoryCommand`、`ArchiveFieldLayoutSummary`；不要新增没有明确边界收益的 `XxxBO`、`XxxDTO`、`XxxModel`、`XxxInfo`。
- 后端架构边界通过 ArchUnit 测试固化；调整包结构、跨模块调用或公共类型落点时，必须同步维护对应架构测试。
- Java 代码格式化统一以 Spotless 调用 `google-java-format` 的 AOSP 风格为真相源，不再另行维护手写缩进、折行、空格、括号位置等细粒度格式规则；需要格式化时运行 Maven/Makefile 暴露的 Spotless 任务。Spotless 对同次改动范围内 Java 文件产生的换行、导入和格式调整应保留，不要作为无关夹带改动还原。
- Java 导入整理最终交给 Spotless：先按 `google-java-format` 处理格式，再按 `importOrder` 分组排序，并执行 `removeUnusedImports`。当前导入分组顺序为 `java|javax`、`jakarta`、`org`、`com`、`github`、其他导入、静态导入；不要手工调整成 IDE 默认顺序或 Checkstyle 口径。
- Java nullness 默认使用 JSpecify：项目源码包默认在 `package-info.java` 标注 `@NullMarked`，未标注的类型视为非空；普通 Java 代码中允许为 null 的字段、参数、返回值、泛型类型参数和数组元素必须显式使用 `org.jspecify.annotations.Nullable`。Jakarta Data Repository 方法签名例外：为了让 Hibernate/Jakarta Data provider 正确识别可空返回和非空参数，Repository 接口方法继续使用 `jakarta.annotation.Nullable` / `jakarta.annotation.Nonnull`。不要再新增 `javax.annotation.Nullable`、JetBrains Nullable 或 FindBugs Nullable；第三方 API 边界无法准确表达时，优先在最窄作用域使用 `@NullUnmarked` 或局部 `@Nullable`，不要扩大到整个模块。
- Java 字符串判空统一使用 Apache Commons Lang `StringUtils`，不要直接散写原生空值和空白判断组合。
- 摘要、编码、Hex 等通用加密/编码类方法优先使用 Apache Commons Codec 等成熟工具库，不要在业务代码里手写通用算法封装。
- 方法参数校验、请求参数校验这类纯校验操作不应该被事务包裹；只有确实需要原子写入、状态变更、消费令牌、扣减库存、锁定记录等场景才开启事务。
- 新增或改动 Java 方法时，超过 5 个业务参数应收敛为语义明确的对象参数，例如请求 record、条件 record、命令对象或 Mapper 条件对象；不要用长参数列表承载查询筛选、分页、排序或写入字段。框架回调、简单构造器和极少数稳定底层工具方法可例外，但业务 Service、Repository、Mapper 和 Controller 协作边界默认遵守该规则。
- 数据库和 Flyway 迁移以 PostgreSQL 为唯一优先目标；项目自有 DDL、索引、约束、查询、执行计划和迁移脚本可以围绕 PostgreSQL 能力优化，不为 MySQL、Oracle、SQL Server 等数据库做兼容性折中。除非明确新增其他数据库支持，不维护多数据库迁移分支。
- SQL 默认不使用双引号或反引号包裹标识符，也不要通过 `as "camelCase"` 这类 quoted alias 维持大小写；表名、列名、别名、索引名和约束名都使用小写 snake_case，让非法标识符尽早暴露。查询列名与结果名相同的场景不要写 `fonds_code as fonds_code` 这类冗余别名，只有表达式或确需更名时才使用小写 snake_case alias。动态 SQL 同样不要依赖自动引用标识符兜底，拼接前应校验标识符合法性。
- SQL、迁移验证和测试断言不得假定 schema 固定为 `public`；需要限定当前 schema 时使用 `current_schema()`、当前 search_path 下的未限定对象名，或使用配置提供的 schema。除第三方上游脚本确实要求外，不写 `public.`、`table_schema = 'public'` 这类硬编码。
- 档案记录搜索、管理查询和全文 provider 的业务合同以 `openspec/specs/archive-record-search/spec.md` 为准；代码层只保留 provider 通过标准 Bean 与 `archive.search.full-text.provider` 配置切换，不在业务查询核心代码中绑定具体中间件。
- 基础设施组件优先使用 Spring Boot AutoConfiguration、标准框架 Bean 和组件自身配置，不维护项目级基础设施总装配、统一 adapter、统一降级或统一 fail-fast 校验。HTTP 会话直接使用 Spring Session 和 `spring.session.*` 配置；缓存直接使用 Spring Cache 和 `spring.cache.*` 配置；调度直接使用 Spring Quartz 和 `spring.quartz.*` 配置；文件对象存储使用 S3 兼容协议和 `archive.storage.*` 配置；模块事件可靠发布使用 Spring Modulith 与 JDBC Event Publication Registry，不再维护项目自研数据库队列或 `archive.queue.*` 配置。
- 模块间业务事件默认使用 Spring Modulith：需要可靠执行的监听器使用 `@ApplicationModuleListener`，只在当前进程内即时处理且可丢或可重算的动作才使用普通 Spring 事件。跨系统外发使用 Spring Modulith event externalization/outbox 或明确选定的消息中间件；导入、导出、AI/OCR、批处理等长耗时后台任务后续优先选 JobRunr 等成熟组件，不重新引入项目自研通用队列。
- 项目暂不提供分布式锁组件，不保留项目级锁接口、旧锁配置或 `am_runtime_lock`。后续确需分布式锁时，先选定成熟开源库并固定具体实现，再按该库的配置方式接入，不重新引入项目级锁 adapter。
- Spring Session、Spring Cache、Spring Quartz 都由各自框架接管，项目不要再定义会话 adapter、缓存 adapter、自定义缓存端口、调度 adapter 或其他项目级基础设施 adapter。关闭调度使用 `spring.quartz.auto-startup=false`，JDBC JobStore 使用 `spring.quartz.job-store-type=jdbc`；Quartz 必需表缺失时交给 Quartz/Spring 启动过程暴露错误，不再写项目级 Quartz 表存在性校验。
- 缓存直接使用 Spring Cache 抽象，业务模块使用 `CacheManager` 或 Spring Cache 注解，不使用项目自定义缓存端口。默认 `spring.cache.type=none` 使用 `NoOpCacheManager`；单机可用 `simple`、`caffeine`、`cache2k` 等本地实现，集群必须使用 Redis、Hazelcast、Infinispan、Couchbase 等分布式实现，或接入数据库缓存、多级缓存等共享 `CacheManager`。需要本地简单缓存、Redis、Caffeine+Redis、JetCache、Redisson 本地缓存等能力时，应优先使用 Spring Boot AutoConfiguration 或在基础设施层注册 Spring Cache 兼容的 `CacheManager`；引入多级缓存前必须先明确缓存对象范围、TTL、失效事件、集群一致性和脏读容忍度。
- 当前项目未正式发布前，Flyway 迁移按目标结构直接维护；不为尚未发布的旧结构保留兼容分支或增量包袱。
- 档案元数据、项目自有表命名、业务字段校验、枚举持久化、逻辑删除唯一性、分类动态表和唯一规则合同以 `openspec/specs/archive-metadata/spec.md` 为准。
- 第三方框架原生表不属于项目自有表，例如 Spring Session 的 `SPRING_SESSION`、Quartz 的 `QRTZ_*`。除非明确改为项目自维护表，否则保留框架默认命名，避免偏离上游脚本。
- Flowable 使用 `flowable-spring-boot-starter-process` 的流程引擎能力，流程引擎由 starter 默认启用；不要配置无法被元数据解析的 `flowable.process.enabled`。默认禁用 Flowable IDM 与 Event Registry，只使用 `flowable.idm.enabled=false` 和 `flowable.eventregistry.enabled=false`，不要使用已弃用的 `flowable.db-identity-used`。
- Flowable 原生表由 Flyway 管理，运行期 `flowable.database-schema-update=false`。迁移脚本应从当前 Flowable 版本随包 PostgreSQL DDL 复制，当前只纳入 common、process engine、history 表；不要纳入 IDM 的 `ACT_ID_*` 表或 Event Registry 的 `FLW_EVENT_*` 表，除非明确启用对应引擎。
- 当前持久化入口是 Jakarta Data 和 MyBatis：固定 CRUD 表优先使用 Jakarta Data Repository；动态表、复杂 SQL、批处理、报表和认证适配查询统一使用 MyBatis；不要在项目代码里使用 JdbcClient 作为持久化入口，不要引入 Spring Data JPA。当前 Jakarta Data Repository 必须通过 Hibernate `StatelessSession` / `EntityAgent` 执行，不允许切换为普通有状态 `Session` 或引入依赖一级缓存、脏检查、延迟会话生命周期的写法。业务代码不得直接使用 Hibernate 有状态 `Session`、Hibernate `Query` 或其他依赖 Session 生命周期的对象；如确需 Hibernate 底层适配，只能封装在基础设施层，不能外泄为业务模块合同。Repository 对外不得返回 `Stream`、游标等依赖会话生命周期的对象，必须在 `@Transactional` 方法内消费完查询结果。
- Jakarta Data Repository 自定义方法必须显式标注 `@Find`、`@Insert`、`@Update`、`@Delete`、`@Query` 或 Hibernate `@HQL` 等操作注解，避免缺失注解后由 provider 按方法名派生实现。方法名可以保留必要业务可读性，但不能把 Query-by-method-name 当成查询合同来源。
- 固定实体写入禁止调用 Repository `save` 或定义 `@Save` 方法；项目自有写入不使用 upsert 语义。新增、修改、删除必须使用语义明确的 `insert` / `update` / `delete` 生命周期方法，Service 层先判断业务分支再调用对应方法。
- 固定实体的 `created_at`、`updated_at` 默认通过 Hibernate `@CreationTimestamp`、`@UpdateTimestamp` 生成；需要时间审计字段的固定实体应显式标注对应注解。`created_by`、`updated_by` 通过基础设施层无状态会话审计拦截器统一填充，操作人字段从 Spring Security 上下文读取当前 `AuthenticatedUser`。无状态会话拦截器只维护用户审计字段，不写时间字段；需要创建语义时必须走显式 `insert`，需要修改语义时必须走显式 `update`。需要保留业务语义的操作流水继续写业务审计表。MyBatis 写入路径不会触发 Hibernate 时间戳注解或无状态会话拦截器，必须在 SQL 或调用方显式维护审计字段。
- StringUtils.removeStart已过时，替换为Strings.CS.removeStart

## API 设计约定

- 项目自有 HTTP API 合同的真相源是 `openspec/specs/api-contract/spec.md`；分页、排序、过滤、响应 DTO、错误响应、ID 类型、异步任务、第三方协议例外和验收场景都以该规格为准，不在 `AGENTS.md` 复制细节。
- 设计或修改项目自有 HTTP API 时，按以下规范路由执行：
    - 普通资源、URL、HTTP 方法、JSON 响应、分页、兼容性：遵守 `api-contract` 中采用的 Zalando RESTful API Guidelines 口径。
    - 标准 CRUD 难以自然表达的业务动作：仅使用 Google AIP-136 custom method 扩展，例如 `POST /api/v1/{resources}/{id}:action` 或 `POST /api/v1/{resources}:batchAction`。
    - 长耗时或异步执行的导入、导出、批处理、外部同步、AI/OCR 等任务：遵守 `api-contract` 中采用的 Microsoft Azure REST long-running operation 口径，使用 `202 Accepted`、`Operation-Location` 和可轮询 job 资源。
    - 错误响应：遵守 Spring `ProblemDetail` / RFC 9457 口径，并按 `api-contract` 的项目扩展字段返回。
    - 第三方组件或外部协议固定路径：只作为适配层例外处理，不反向污染项目自有 API 规范。
- 具体业务模块的接口字段、状态机、权限边界和验收场景应进入对应 OpenSpec 业务规格；如果与通用 API 规范冲突，先修改或澄清 OpenSpec，再改代码。
- 项目 API 设计任务优先使用 `.agents/skills/archive-api-design-strategy`；持久化入口、实体和 Mapper 边界任务优先使用 `.agents/skills/archive-persistence-strategy`。
- API 设计规则在 `AGENTS.md` 只保留规范适用时机、真相源和技能路由；资源、路径、响应、错误、ID、分页和第三方协议例外的具体验收场景统一维护到 OpenSpec。

## 文件存储约定

- 文件存储业务合同以 `openspec/specs/file-storage/spec.md` 为准；代码层统一通过 `FileStorageService` 使用文件存储能力，对象存储默认使用 S3 兼容协议和成熟工具库。

## 文档查询

当用户询问库、框架、SDK、API、CLI 工具或云服务的用法、配置、版本迁移、调试和 setup 时，必须使用 Context7 查询当前文档；不要凭记忆回答。

Ant Design 生态例外：当问题涉及 Ant Design、Ant Design Pro、Pro Components、`@ant-design/cli` 或相关主题、组件、布局、表单、表格用法时，优先使用项目内的 Ant Design CLI、本地 `llms.txt` 和已安装文档查询；只有 CLI 或本地文档缺失、查不到或输出不足以回答时，才回退到 Context7。不要跳过用户明确要求的 Ant Design CLI 查询路径。

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

本项目使用 Vite+，这是构建在 Vite、Rolldown、Vitest、tsdown、Oxlint、Oxfmt 和 Vite Task 之上的统一前端工具链。Vite+ CLI `vp` 由项目依赖提供，不要求全局安装；日常通过根目录 `package.json` 的 `pnpm` 脚本执行，确需直接调用时使用 `pnpm exec vp ...`。它不同于 Vite，运行和构建应使用项目脚本或 `pnpm exec vp build`。

- 查看命令列表：`pnpm exec vp help`
- 查看子命令说明：`pnpm exec vp <command> --help`
- 本地文档：`node_modules/vite-plus/docs`
- 在线文档：https://viteplus.dev/guide/
- Codex 不允许主动启动开发服务器；不要运行 `pnpm run dev:web`、`pnpm exec vp dev`、`vp dev`、`vp run *#dev`、`npm run dev`、`pnpm dev`、`vite --host` 等长期占用端口的本地服务命令。需要预览时，只说明用户可自行启动的命令。

## 检查清单

- 拉取远程变更后、开始开发前运行 `pnpm install`。
- 前端变更运行 `pnpm check` 和 `pnpm test`，用于格式化、lint、类型检查和测试。
- 检查 `vite.config.ts` tasks 或 `package.json` scripts 中是否有必要验证命令，并通过 `pnpm run <script>` 或 `pnpm exec vp run <script>` 执行。
- 如果 setup、运行时或包管理行为异常，运行 `pnpm exec vp env doctor` 并保留输出。
- 后端 Maven 项目根目录是 `server/`，仓库根目录没有聚合 POM；运行 Maven 验证时必须以 `server/` 为工作目录，例如 `cd server && mise exec -- mvn -q -DskipTests test-compile`。如果本机已全局安装 Maven，也可以使用等价的 `mvn ...` 命令。
- 后端改动至少运行对应 Maven 编译或测试；如果测试需要本机数据库，说明依赖和结果。
- Java 过时 API 批量迁移优先使用 OpenRewrite；仓库根目录 `rewrite.yml` 维护可复用 recipe，后端先运行 `task server-rewrite-dry-run` 审查 `server/target/rewrite/rewrite.patch`，确认后再运行 `task server-rewrite-run`。
- OpenRewrite recipe 只沉淀能语义化迁移的安全规则；涉及参数重排、条件改写、补 import 或复杂框架升级时，先 dry run 和编译验证，YAML 不足时新增自定义 recipe，不用正则批改 Java 源码。
