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

- 后端当前按单 Spring Boot 应用起步；业务功能统一放在 `server/src/main/java/github/luckygc/am/module` 下，并按业务边界拆子包，例如 `module/auth`。
- 基础设施能力统一放在 `server/src/main/java/github/luckygc/am/infrastructure` 下，只承载技术适配，例如 Spring Security、Hibernate、文件存储、外部系统客户端、缓存和调度适配。
- `common` 只放跨业务模块共享的应用基础约定，不承载业务模块，也不承载具体外部技术适配；认证、用户、权限、档案、存储对象等业务语义不得放入 `common`。
- 业务模块不按 `api` / `internal` 继续拆包；当前是单体应用，不按微服务式模块合同组织代码。实体、Repository、Mapper、Controller、Service、DTO 等按业务模块直接放在对应模块包下，必要时再按 `web`、`service`、`dto`、`mapper` 等实现形态拆子包。
- 跨业务模块协作允许直接依赖目标业务模块公开的类，但不要绕过目标模块已有 Service 去直接操作其 Repository、Mapper 或底层表；需要复用业务能力时优先抽出明确的 Service 方法。
- 控制器请求/响应 DTO 如果只服务本模块 HTTP 接口，可放在模块内的 `dto` 或 `web` 相关包下；只有确实跨多个模块复用时再提升为模块根下稳定类型。
- 后端架构边界通过 ArchUnit 测试固化；调整包结构、跨模块调用或公共类型落点时，必须同步维护对应架构测试。
- Java 代码格式化统一以 Spotless 调用 `google-java-format` 的 AOSP 风格为真相源，不再另行维护手写缩进、折行、空格、括号位置等细粒度格式规则；需要格式化时运行 Maven/Makefile 暴露的 Spotless 任务。
- Java 导入整理最终交给 Spotless：先按 `google-java-format` 处理格式，再按 `importOrder` 分组排序，并执行 `removeUnusedImports`。当前导入分组顺序为 `java|javax`、`jakarta`、`org`、`com`、`github`、其他导入、静态导入；不要手工调整成 IDE 默认顺序或 Checkstyle 口径。
- Java 字符串判空统一使用 Apache Commons Lang `StringUtils`，不要直接散写原生空值和空白判断组合。
- 摘要、编码、Hex 等通用加密/编码类方法优先使用 Apache Commons Codec 等成熟工具库，不要在业务代码里手写通用算法封装。
- 方法参数校验、请求参数校验这类纯校验操作不应该被事务包裹；只有确实需要原子写入、状态变更、消费令牌、扣减库存、锁定记录等场景才开启事务。
- 数据库以 PostgreSQL 为唯一优先目标；项目自有 DDL、索引、约束、查询和执行计划可以围绕 PostgreSQL 能力优化，不为 MySQL、Oracle、SQL Server 等数据库做兼容性折中。
- SQL 默认不使用双引号或反引号包裹标识符，也不要通过 `as "camelCase"` 这类 quoted alias 维持大小写；表名、列名、别名、索引名和约束名都使用小写 snake_case，让非法标识符尽早暴露。查询列名与结果名相同的场景不要写 `fonds_code as fonds_code` 这类冗余别名，只有表达式或确需更名时才使用小写 snake_case alias。动态 SQL 同样不要依赖自动引用标识符兜底，拼接前应校验标识符合法性。
- Flyway 迁移默认按 PostgreSQL 编写；除非明确新增其他数据库支持，不维护多数据库迁移分支，也不为了跨库兼容回避 PostgreSQL 的成熟能力。
- Flyway 迁移，档案server的pom.xml里版本未达到1.0.0时，都按照目标结构修改，1.0.0之后使用增量迁移
- 项目不会跨时区使用；项目自有表时间字段使用无时区 `timestamp`，不使用 `timestamptz`。
- 项目自有数据库表必须使用 `am_模块_表名` 命名，例如 `am_auth_user`、`am_archive_file`、`am_storage_file`；不要只使用 `am_表名` 这类缺少模块语义的名称。
- Flyway 迁移中新建业务表、平台表、审计表、中间表等项目自有表时，同样必须使用 `am_模块_表名`；索引、约束、序列等对象名称应跟随表名保持 `am_模块_` 语义。
- 项目自有表不允许使用数据库 `CHECK` 约束；枚举、状态、数值范围等校验放在应用层或字典/配置层。
- 带逻辑删除字段的项目自有表不允许使用覆盖已删除数据的唯一约束；唯一性必须使用 `where deleted_at is null` 的部分唯一索引，只约束未删除记录。
- 第三方框架原生表不属于项目自有表，例如 Spring Session 的 `SPRING_SESSION`、Quartz 的 `QRTZ_*`。除非明确改为项目自维护表，否则保留框架默认命名，避免偏离上游脚本。
- 当前持久化入口是 Jakarta Data 和 MyBatis：固定 CRUD 表优先使用 Jakarta Data Repository；动态表、复杂 SQL、批处理、报表和认证适配查询统一使用 MyBatis；不要在项目代码里使用 JdbcClient 作为持久化入口，不要引入 Spring Data JPA。业务代码不得直接使用 Hibernate 有状态 `Session`、Hibernate `Query` 或其他依赖 Session 生命周期的对象；如确需 Hibernate 底层适配，只能封装在基础设施层，不能外泄为业务模块合同。Repository 对外不得返回 `Stream`、游标等依赖会话生命周期的对象，必须在 `@Transactional` 方法内消费完查询结果。
- StringUtils.removeStart已过时，替换为Strings.CS.removeStart

## API 设计约定

- 项目自有 HTTP API 默认遵循 Google Cloud API Design Guide / AIP；除 gRPC、protobuf、HTTP/gRPC transcoding 等传输和 IDL 专属内容外，资源建模、URL、标准方法、自定义方法、分页、过滤、字段命名、错误模型和兼容性规则都按 AIP 口径执行。
- API URL 设计使用 Google Cloud API Design Guide / AIP 的资源导向模型：先识别资源名词、层级和标准方法，再决定是否需要自定义方法；不要直接按数据库表、页面按钮或服务方法名暴露接口。
- REST API 路径必须在 `/api` 后包含主版本号，例如 `/api/v1`；只暴露 `v1`、`v2` 这类主版本，不使用 `v1.0`、`v1.1`、`v1.4.2` 这类 minor/patch 版本。
- 标准 CRUD 优先使用资源路径和 HTTP 方法表达：`GET /api/v1/books/{id}` 查询单个资源，`GET /api/v1/books` 查询集合，`POST /api/v1/books` 创建，`PATCH /api/v1/books/{id}` 局部更新，`DELETE /api/v1/books/{id}` 删除。
- 只有标准方法无法自然表达的动作才使用自定义方法。自定义方法路径使用 AIP 风格冒号动作：`POST /api/v1/books/{id}:archive`、`POST /api/v1/auth:login`；动词使用 lower camelCase，不使用 `/archive`、`/_archive`、`/validate_token`、`/validateToken` 这类路径段承载项目自有动作。
- 自定义方法如果只读取数据且请求参数适合 query string，可使用 `GET`；有副作用、消费令牌、改变服务端状态或提交复杂请求体时使用 `POST`。
- 查询当前登录会话这类单例资源使用资源名表达，例如 `GET /api/v1/auth/session`；登录、退出等非 CRUD 动作使用 `POST /api/v1/auth:login`、`POST /api/v1/auth:logout`。
- Controller 方法上的 Spring MVC 映射必须写完整 URL，例如 `@GetMapping("/api/v1/books")`、`@PostMapping("/api/v1/books/{id}:archive")`；不要使用类级 `@RequestMapping` 叠加方法级相对路径。冒号动作尤其不能通过类级路径和 `@PostMapping(":action")` 拼接，避免实际映射路径与前端 API 合同不一致。
- 错误响应使用 AIP-193 的 HTTP/JSON 形态：响应体为 `{"error": {"code": 400, "message": "...", "status": "INVALID_ARGUMENT", "details": [...]}}`；`code` 使用 HTTP 状态码，`status` 使用 `google.rpc.Code` 的枚举名，`details` 优先使用 `google.rpc.ErrorInfo`、`google.rpc.BadRequest`、`google.rpc.LocalizedMessage`、`google.rpc.Help` 等标准 detail 的 JSON 表示。字段级校验错误放在 `BadRequest.fieldViolations`，不要让前端解析纯文本、HTML 或异常栈。
- 分页、过滤、排序、字段掩码、批量方法和长任务等 API 合同按 AIP 对应章节建模；只有第三方组件固定协议、框架回调或明确无法适配 AIP 的外部接口可以作为例外，例外必须限制在适配层，不得扩散为项目自有 API 风格。
- 第三方组件或外部协议强制要求的回调路径可以作为适配例外保留，例如 CAP widget 固定使用的 `/api/v1/auth/cap/challenge`、`/api/v1/auth/cap/redeem`、`/api/v1/auth/cap/validateToken`；这类例外不得扩散为项目自有 API 命名风格。

## 文件存储约定

- 文件存储统一通过 `FileStorageService` 使用；存储层只负责对象内容的上传、下载、删除和存在判断，不提供对象列举和元信息反查作为业务真相源。
- 文件信息必须落本地数据库；每条文件记录必须固化自身的 `storage_type`、`bucket_name` 和 `object_key`，不能用当前全局存储类型推断历史文件位置；系统只维护一份当前存储配置，不配置默认 `storage_type`，上传时对象存储配置完整则优先对象存储，否则使用 `active-local-bucket` 指定的本地 bucket，下载、删除、存在判断按文件记录中的 `storage_type` 和 `bucket_name` 分发；本地存储允许配置多个 bucket/root，具体目录只以配置为准；对象存储只配置一个 bucket，不设置 active bucket。
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
- 后端改动至少运行对应 Maven 编译或测试；如果测试需要本机数据库，说明依赖和结果。
