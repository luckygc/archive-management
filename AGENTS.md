# 仓库协作规则

## 基本要求

- 始终使用中文交流、编写文档和注释。
- 默认按最小闭环推进改动；不要把需求顺手扩展到相邻模块。
- 项目必须遵守奥卡姆剃刀原则：能用现有框架、现有组件、现有数据模型或更少代码清楚解决的问题，不新增抽象、适配层、配置开关、兼容分支或未来预留能力；只有真实需求已经出现且收益大于复杂度时，才引入额外机制。
- 修改前先确认影响目录；如果后续新增更深层级的 `AGENTS.md`，必须优先读取最近层级规则，冲突时以更近目录规则为准。
- 规则、约定、架构边界类需求优先固化到仓库文档，再按文档口径改代码；业务能力、业务流程、业务字段、状态机、接口行为和验收场景应进入 OpenSpec，不继续写入 `AGENTS.md`。`AGENTS.md` 只保留协作方式、代码风格、架构边界、技术选型和工具链规则；现有业务条款后续修改时应迁移到对应 OpenSpec 规格，再从 `AGENTS.md` 删除或仅保留指向性规则。

## 工程判断原则

- 代码优先追求可维护、可排障和可演进，不追求炫技、框架感或抽象层数量；好的实现可以朴素甚至无聊，但需求变化、线上事故、回滚和接手维护时必须站得住。
- 写代码时默认为下一位维护者降低理解成本：命名表达业务意图，控制流直接，边界条件清楚，异常路径明确；不要把复杂性藏进过度通用的 helper、魔法配置或隐式约定里。
- 新增抽象前先确认真实重复、真实边界和真实收益；只有当抽象能减少实质复杂度、统一稳定合同或隔离确定变化点时才引入。为了“以后可能会用”而新增的适配层、工厂、开关和兼容分支默认不接受。
- 优先让副作用集中且可追踪：数据库写入、远程调用、缓存失效、权限判断、事务边界、文件读写和状态变更不要散落在表现层或无关 helper 中。
- 实现必须覆盖失败路径和恢复路径：空值、权限不足、并发冲突、外部服务失败、部分成功、重复提交、回滚和重试策略都应按风险处理；不要只交付 happy path。
- 类型、校验、状态机和数据库约束应共同守住业务不变量；前端校验和 UI 状态只改善体验，不能替代服务端权限、数据权限、持久化约束和审计。
- 选型和实现默认选择能减少长期问题的方案：少一套状态源、少一层同步、少一个运行时、少一个自研基础设施；确需引入新技术时，先说明它消除的具体复杂度和新增的维护成本。
- 表单、表格、接口、权限和缓存这类后台系统高频能力，默认先明确唯一状态源、服务端合同、错误回填和刷新策略，再写页面代码；不要让同一份数据同时被多个框架、store、组件内部状态或前后端约定重复管理。
- 表单实现必须明确字段状态归属：如果使用 AntD Form 管字段，就不要再让 React Hook Form、TanStack Form 或本地 store 同步同一字段；如果使用 React Hook Form 或 TanStack Form，AntD Form/Form.Item 只能作为布局和错误展示壳，不写字段 `name` 接管状态。
- 表格实现必须先区分“表格状态内核”和“视觉组件”：排序、筛选、分页、列宽、列显隐、行选择、编辑草稿、导入错误回填等状态应有单一归属；除非明确接受商业许可和能力边界，不把 Enterprise/Pro 组件能力当成默认基础能力。
- API 设计先固定资源语义、请求/响应 DTO、错误模型、分页/排序/过滤合同和权限边界，再实现调用方；页面不得靠猜字段、猜状态码或自行拼接非合同化路径来补后端接口缺口。
- 管理后台性能问题优先从数据范围、分页/虚拟化、缓存失效、请求合并、渲染范围和权限过滤位置解决；不要先用局部 loading、前端全量加载后过滤或无界 watch/effect 掩盖结构问题。
- 重复代码治理先判断重复背后的业务语义是否一致：语义不同的重复可以暂时保留，语义稳定且变更方向一致时再抽公共组件、hook、service 或规格；抽象后必须让调用方更少知道细节，而不是增加参数矩阵。
- 新增服务端运行时、BFF、前端状态库、表格/表单内核或缓存层前，必须说明它负责的边界、替代掉的旧路径、与现有权限/审计/事务/缓存的关系，以及不采用静态部署或现有后端能力的具体原因。

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
- Java 代码格式化统一以 Spotless 调用 `google-java-format` 的 AOSP 风格为真相源，不再另行维护手写缩进、折行、空格、括号位置等细粒度格式规则；需要格式化时运行 Maven/Makefile 暴露的 Spotless 任务。Spotless 对同次改动范围内 Java 文件产生的换行、导入和格式调整应保留，不要作为无关夹带改动还原。
- Java 导入整理最终交给 Spotless：先按 `google-java-format` 处理格式，再按 `importOrder` 分组排序，并执行 `removeUnusedImports`。当前导入分组顺序为 `java|javax`、`jakarta`、`org`、`com`、`github`、其他导入、静态导入；不要手工调整成 IDE 默认顺序或 Checkstyle 口径。
- Java nullness 默认使用 JSpecify：项目源码包默认在 `package-info.java` 标注 `@NullMarked`，未标注的类型视为非空；普通 Java 代码中允许为 null 的字段、参数、返回值、泛型类型参数和数组元素必须显式使用 `org.jspecify.annotations.Nullable`。Jakarta Data Repository 方法签名例外：为了让 Hibernate/Jakarta Data provider 正确识别可空返回和非空参数，Repository 接口方法继续使用 `jakarta.annotation.Nullable` / `jakarta.annotation.Nonnull`。不要再新增 `javax.annotation.Nullable`、JetBrains Nullable 或 FindBugs Nullable；第三方 API 边界无法准确表达时，优先在最窄作用域使用 `@NullUnmarked` 或局部 `@Nullable`，不要扩大到整个模块。
- Java 字符串判空统一使用 Apache Commons Lang `StringUtils`，不要直接散写原生空值和空白判断组合。
- 摘要、编码、Hex 等通用加密/编码类方法优先使用 Apache Commons Codec 等成熟工具库，不要在业务代码里手写通用算法封装。
- 方法参数校验、请求参数校验这类纯校验操作不应该被事务包裹；只有确实需要原子写入、状态变更、消费令牌、扣减库存、锁定记录等场景才开启事务。
- 数据库和 Flyway 迁移以 PostgreSQL 为唯一优先目标；项目自有 DDL、索引、约束、查询、执行计划和迁移脚本可以围绕 PostgreSQL 能力优化，不为 MySQL、Oracle、SQL Server 等数据库做兼容性折中。除非明确新增其他数据库支持，不维护多数据库迁移分支。
- SQL 默认不使用双引号或反引号包裹标识符，也不要通过 `as "camelCase"` 这类 quoted alias 维持大小写；表名、列名、别名、索引名和约束名都使用小写 snake_case，让非法标识符尽早暴露。查询列名与结果名相同的场景不要写 `fonds_code as fonds_code` 这类冗余别名，只有表达式或确需更名时才使用小写 snake_case alias。动态 SQL 同样不要依赖自动引用标识符兜底，拼接前应校验标识符合法性。
- SQL、迁移验证和测试断言不得假定 schema 固定为 `public`；需要限定当前 schema 时使用 `current_schema()`、当前 search_path 下的未限定对象名，或使用配置提供的 schema。除第三方上游脚本确实要求外，不写 `public.`、`table_schema = 'public'` 这类硬编码。
- 档案记录搜索、管理查询和全文 provider 的业务合同以 `openspec/specs/archive-record-search/spec.md` 为准；代码层只保留 provider 通过标准 Bean 与 `archive.search.full-text.provider` 配置切换，不在业务查询核心代码中绑定具体中间件。
- 基础设施组件优先使用 Spring Boot AutoConfiguration、标准框架 Bean 和组件自身配置，不维护项目级基础设施总装配、统一 adapter、统一降级或统一 fail-fast 校验。HTTP 会话直接使用 Spring Session 和 `spring.session.*` 配置；缓存直接使用 Spring Cache 和 `spring.cache.*` 配置；调度直接使用 Spring Quartz 和 `spring.quartz.*` 配置；文件对象存储使用 S3 兼容协议和 `archive.storage.*` 配置；模块事件可靠发布使用 Spring Modulith 与 JDBC Event Publication Registry，不再维护项目自研数据库队列或 `archive.queue.*` 配置。
- 模块间业务事件默认使用 Spring Modulith：需要可靠执行的监听器使用 `@ApplicationModuleListener`，只在当前进程内即时处理且可丢或可重算的动作才使用普通 Spring 事件。跨系统外发使用 Spring Modulith event externalization/outbox 或明确选定的消息中间件；导入、导出、AI/OCR、批处理等长耗时后台任务后续优先选 JobRunr 等成熟组件，不重新引入项目自研通用队列。
- 项目暂不提供分布式锁组件，不保留项目级锁接口、旧锁配置或 `am_runtime_lock`。后续确需分布式锁时，先选定成熟开源库并固定具体实现，再按该库的配置方式接入，不重新引入项目级锁 adapter。
- Spring Session、Spring Cache、Spring Quartz 都由各自框架接管，项目不要再定义会话 adapter、缓存 adapter、自定义缓存端口、调度 adapter 或其他项目级基础设施 adapter。关闭调度使用 `spring.quartz.auto-startup=false`，JDBC JobStore 使用 `spring.quartz.job-store-type=jdbc`；Quartz 必需表缺失时交给 Quartz/Spring 启动过程暴露错误，不再写项目级 Quartz 表存在性校验。
- 缓存直接使用 Spring Cache 抽象，业务模块使用 `CacheManager` 或 Spring Cache 注解，不使用项目自定义缓存端口。默认 `spring.cache.type=none` 使用 `NoOpCacheManager`；单机可用 `simple`、`caffeine`、`cache2k` 等本地实现，集群必须使用 Redis、Hazelcast、Infinispan、Couchbase 等分布式实现，或接入数据库缓存、多级缓存等共享 `CacheManager`。需要本地简单缓存、Redis、Caffeine+Redis、JetCache、Redisson 本地缓存等能力时，应优先使用 Spring Boot AutoConfiguration 或在基础设施层注册 Spring Cache 兼容的 `CacheManager`；引入多级缓存前必须先明确缓存对象范围、TTL、失效事件、集群一致性和脏读容忍度。
- Flyway 迁移，档案server的pom.xml里版本未达到1.0.0时，都按照目标结构修改，1.0.0之后使用增量迁移
- 档案元数据、项目自有表命名、业务字段校验、枚举持久化、逻辑删除唯一性、分类动态表和唯一规则合同以 `openspec/specs/archive-metadata/spec.md` 为准。
- 第三方框架原生表不属于项目自有表，例如 Spring Session 的 `SPRING_SESSION`、Quartz 的 `QRTZ_*`。除非明确改为项目自维护表，否则保留框架默认命名，避免偏离上游脚本。
- Flowable 使用 `flowable-spring-boot-starter-process` 的流程引擎能力，流程引擎由 starter 默认启用；不要配置无法被元数据解析的 `flowable.process.enabled`。默认禁用 Flowable IDM 与 Event Registry，只使用 `flowable.idm.enabled=false` 和 `flowable.eventregistry.enabled=false`，不要使用已弃用的 `flowable.db-identity-used`。
- Flowable 原生表由 Flyway 管理，运行期 `flowable.database-schema-update=false`。迁移脚本应从当前 Flowable 版本随包 PostgreSQL DDL 复制，当前只纳入 common、process engine、history 表；不要纳入 IDM 的 `ACT_ID_*` 表或 Event Registry 的 `FLW_EVENT_*` 表，除非明确启用对应引擎。
- 当前持久化入口是 Jakarta Data 和 MyBatis：固定 CRUD 表优先使用 Jakarta Data Repository；动态表、复杂 SQL、批处理、报表和认证适配查询统一使用 MyBatis；不要在项目代码里使用 JdbcClient 作为持久化入口，不要引入 Spring Data JPA。当前 Jakarta Data Repository 必须通过 Hibernate `StatelessSession` / `EntityAgent` 执行，不允许切换为普通有状态 `Session` 或引入依赖一级缓存、脏检查、延迟会话生命周期的写法。业务代码不得直接使用 Hibernate 有状态 `Session`、Hibernate `Query` 或其他依赖 Session 生命周期的对象；如确需 Hibernate 底层适配，只能封装在基础设施层，不能外泄为业务模块合同。Repository 对外不得返回 `Stream`、游标等依赖会话生命周期的对象，必须在 `@Transactional` 方法内消费完查询结果。
- Jakarta Data Repository 自定义方法必须显式标注 `@Find`、`@Insert`、`@Update`、`@Delete`、`@Save`、`@Query` 或 Hibernate `@HQL` 等操作注解，避免缺失注解后由 provider 按方法名派生实现。方法名可以保留必要业务可读性，但不能把 Query-by-method-name 当成查询合同来源。
- 固定实体写入不要默认调用 Repository `save` 或定义 `@Save` 方法；新增、修改、删除应优先使用语义明确的 `insert` / `update` / `delete` 生命周期方法，Service 层先判断业务分支再调用对应方法。只有明确需要 upsert 语义且已说明并发、审计和唯一约束影响时，才允许使用 `save`。
- 固定实体的 `created_at`、`updated_at`、`created_by`、`updated_by` 应通过基础设施层无状态会话审计拦截器统一填充；其中操作人字段从 Spring Security 上下文读取当前 `AuthenticatedUser`。`@CreationTimestamp`、`@UpdateTimestamp` 可以保留为 Hibernate 实体写入路径的辅助生成注解，但不要把它们当成 `save`/upsert 或 MyBatis 写入路径的审计真相源。无状态会话 `onUpsert` 只能维护 `updated_at`、`updated_by`，不得覆盖 `created_at`、`created_by`；需要创建语义时必须走显式 `insert`。需要保留业务语义的操作流水继续写业务审计表。MyBatis 写入路径不会触发该拦截器，必须在 SQL 或调用方显式维护审计字段。
- StringUtils.removeStart已过时，替换为Strings.CS.removeStart

## API 设计约定

- 项目自有 HTTP API 合同以 `openspec/specs/api-contract/spec.md` 为准；具体 API 设计默认参考 Google Cloud API Design Guide / AIP，错误响应采用 Spring `ProblemDetail` / RFC 9457 口径。
- 前端可见 ID 是否必须字符串化以 `openspec/specs/api-contract/spec.md` 为准；当前只对明确指定的高增长或 JavaScript 精度风险资源强制转换，例如档案记录、档案文件和文件存储对象。档案分类、字段、布局、唯一规则等元数据配置 ID 可以继续使用数字合同，除非对应规格明确升级。
- 项目 API 设计任务优先使用 `.agents/skills/archive-api-design-strategy`；持久化入口、实体和 Mapper 边界任务优先使用 `.agents/skills/archive-persistence-strategy`。
- API 设计规则只在 `AGENTS.md` 保留入口和技能路由；资源、路径、响应、错误、前端可见 ID 和第三方协议例外的验收场景统一维护到 OpenSpec。

## 文件存储约定

- 文件存储业务合同以 `openspec/specs/file-storage/spec.md` 为准；代码层统一通过 `FileStorageService` 使用文件存储能力，对象存储默认使用 S3 兼容协议和成熟工具库。

## 文档查询

当用户询问库、框架、SDK、API、CLI 工具或云服务的用法、配置、版本迁移、调试和 setup 时，必须使用 Context7 查询当前文档；不要凭记忆回答。

Ant Design 生态例外：当问题涉及 Ant Design、Ant Design Pro、Pro Components、Ant Design Mobile、`@ant-design/cli` 或相关主题、组件、布局、表单、表格用法时，优先使用项目内的 Ant Design CLI、本地 `llms.txt` 和已安装文档查询；只有 CLI 或本地文档缺失、查不到或输出不足以回答时，才回退到 Context7。不要跳过用户明确要求的 Ant Design CLI 查询路径。

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
