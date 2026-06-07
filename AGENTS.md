# 仓库协作规则

## 基本要求

- 始终使用中文交流、编写文档和注释。
- 默认按最小闭环推进改动；不要把需求顺手扩展到相邻模块。
- 修改前先确认影响目录；如果后续新增更深层级的 `AGENTS.md`，必须优先读取最近层级规则，冲突时以更近目录规则为准。
- 规则、约定、架构边界类需求优先固化到仓库文档，再按文档口径改代码。

## 前端样式约定

- 前端样式默认使用组件库内置能力和默认视觉风格，优先通过组件 props、布局组件、结构调整和设计 token 完成界面实现。
- 不要为组件库已有的按钮、表单、表格、弹窗、菜单、标签、分页等基础组件手写大段自定义 CSS；不要用自定义 CSS 覆盖组件库默认颜色、圆角、阴影、间距、交互态等视觉细节。
- 自定义 CSS 基本只用于页面布局、容器尺寸、栅格/弹性排布、滚动区域、间距组织和必要的响应式结构控制。
- 确需自定义视觉样式时，必须有明确业务或组件库能力缺口，并控制在局部范围内，避免形成全局覆盖和临时 hack。

## 后端约定

- 后端当前按单 Spring Boot 应用起步；业务功能统一放在 `server/src/main/java/github/luckygc/am/module` 下，并按业务边界拆子包，例如 `module/auth`。
- 基础设施能力统一放在 `server/src/main/java/github/luckygc/am/infrastructure` 下，只承载技术适配，例如 Spring Security、Hibernate、文件存储、外部系统客户端、缓存和调度适配。
- `common` 只放跨业务模块共享的应用基础约定，不承载业务模块，也不承载具体外部技术适配；认证、用户、权限、档案、存储对象等业务语义不得放入 `common`。
- Java 字符串判空统一使用 Apache Commons Lang `StringUtils`，不要直接散写原生空值和空白判断组合。
- 摘要、编码、Hex 等通用加密/编码类方法优先使用 Apache Commons Codec 等成熟工具库，不要在业务代码里手写通用算法封装。
- 方法参数校验、请求参数校验这类纯校验操作不应该被事务包裹；只有确实需要原子写入、状态变更、消费令牌、扣减库存、锁定记录等场景才开启事务。
- 数据库以 PostgreSQL 为唯一优先目标；项目自有 DDL、索引、约束、查询和执行计划可以围绕 PostgreSQL 能力优化，不为 MySQL、Oracle、SQL Server 等数据库做兼容性折中。
- Flyway 迁移默认按 PostgreSQL 编写；除非明确新增其他数据库支持，不维护多数据库迁移分支，也不为了跨库兼容回避 PostgreSQL 的成熟能力。
- 项目不会跨时区使用；项目自有表时间字段使用无时区 `timestamp`，不使用 `timestamptz`。
- 项目自有数据库表必须使用 `am_模块_表名` 命名，例如 `am_auth_user`、`am_archive_file`、`am_storage_file`；不要只使用 `am_表名` 这类缺少模块语义的名称。
- Flyway 迁移中新建业务表、平台表、审计表、中间表等项目自有表时，同样必须使用 `am_模块_表名`；索引、约束、序列等对象名称应跟随表名保持 `am_模块_` 语义。
- 项目自有表不允许使用数据库 `CHECK` 约束；枚举、状态、数值范围等校验放在应用层或字典/配置层。
- 带逻辑删除字段的项目自有表不允许使用覆盖已删除数据的唯一约束；唯一性必须使用 `where deleted_at is null` 的部分唯一索引，只约束未删除记录。
- 第三方框架原生表不属于项目自有表，例如 Spring Session 的 `SPRING_SESSION`、Quartz 的 `QRTZ_*`。除非明确改为项目自维护表，否则保留框架默认命名，避免偏离上游脚本。
- 固定 CRUD 表优先使用 Hibernate Data Repositories，并通过 Spring 事务边界复用 Hibernate `StatelessSession`；不要引入 Spring Data JPA。Repository 对外不得返回 `Stream`、Hibernate Query、游标等依赖 session 生命周期的对象，必须在 `@Transactional` 方法内消费完查询结果。动态表、复杂 SQL、批处理、报表和认证适配查询继续使用 JdbcClient 或 MyBatis。
- StringUtils.removeStart已过时，替换为Strings.CS.removeStart

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
