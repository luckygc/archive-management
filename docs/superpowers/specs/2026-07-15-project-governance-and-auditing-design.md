# 项目规范治理与统一审计设计

## 背景

项目已经形成 `AGENTS.md`、`PRODUCT.md`、`DESIGN.md`、工程文档、OpenSpec、项目技能和架构测试等多类约束，但当前存在以下问题：

- 同一规则分散在多个文件，出现持久化、缓存、文件存储和前端组件体系等口径冲突。
- 多个任务已经完成的 OpenSpec change 尚未归档，长期业务合同仍停留在 change delta 中。
- `openspec/config.yaml` 未承载项目上下文和制品规则。
- 项目持久化技能推荐的 Repository 基接口与当前 ArchUnit 规则冲突。
- 架构文档混入页面状态、定时刷新和具体 composable 等易过期实现快照。
- Hibernate 与 MyBatis 写入路径的通用审计值来源不统一。
- Repository 通过项目自定义基础接口暴露了并非每个实体都需要的方法。

本设计采用“单一真相源、薄路由、可执行门禁”的治理方式，同时收敛 Jakarta Data Repository 公开面，并统一 Hibernate 与 MyBatis 的通用审计上下文。

## 目标

- 每类规范只有一个明确真相源，其他文件只解释或链接。
- `AGENTS.md` 保持短小，只承载协作总原则、规则路由、安全边界和最小验证闭环。
- 已完成 OpenSpec change 完成归档，活动区只保留真实进行中的 change。
- 项目技能引用当前规范，不再成为独立或冲突的规则来源。
- 删除项目自定义 Repository 基类，每个 Repository 只声明真实用例需要的方法。
- Hibernate 与 MyBatis 使用同一个审计上下文提供通用时间和用户值。
- 所有治理规则通过 Taskfile、ArchUnit、测试或脚本尽可能机械验证。

## 非目标

- 不修改业务状态机、权限边界和项目自有 HTTP API 合同。
- 不实现仍在进行中的 Flowable 审批任务。
- 不引入 SQL parser，也不通过 MyBatis 插件自动改写最终 SQL。
- 不新增 GitHub Actions 或其他 CI 平台配置。
- 不为历史方案保留兼容分支，也不逐份重写历史计划。
- 不引入新的通用 CRUD 层、Repository 适配层或审计配置开关。

## 规范分层与唯一归属

| 内容 | 唯一真相源 | 其他文件职责 |
| --- | --- | --- |
| 业务规则、状态机、权限、API 合同、验收场景 | `openspec/specs/` 与活动 change delta | 说明文档只解释和链接 |
| 产品定位、用户、产品原则 | `PRODUCT.md` | 不放技术实现和构建命令 |
| 视觉、交互、可访问性、设计 Token | `DESIGN.md` | 页面代码消费这些规则 |
| 稳定架构边界、技术选型 | `docs/architecture.md` | `AGENTS.md` 只提供路由 |
| 开发、验证、部署和运维操作 | `Taskfile.yml`、构建配置及对应 `docs/` | 命令以可执行配置为准 |
| AI 与贡献者协作约束 | `AGENTS.md` | 不复制业务合同和框架手册 |
| 特定任务执行流程 | `.codex/skills/`、`.agents/skills/` | 必须引用项目真相源，不自行发明规则 |
| 可机械验证的规则 | ArchUnit、测试、lint、构建脚本 | 文档说明意图，测试负责阻断 |
| 历史方案与计划 | `docs/superpowers/`、已归档 change | 明确标记为非当前规范 |

冲突按内容领域处理，而不是为所有文件建立一条线性优先级。用户当前明确要求优先于现有制品；活动 OpenSpec delta 覆盖对应当前业务规格；代码与 OpenSpec 不一致时视为实现偏差；技能与稳定架构决策或架构测试不一致时修改技能；说明文档和历史资料不得覆盖所属领域真相源。

## `AGENTS.md` 收敛

`AGENTS.md` 最终只保留：

- 中文协作、最小闭环、奥卡姆剃刀、工作树保护等总原则。
- 按改动类型读取 OpenSpec、产品、设计、架构、开发文档和项目技能的路由表。
- 禁止主动启动长期占用端口的开发服务器等自动化安全边界。
- 前端、后端、预览服务和规范变更的最小验证要求。
- 少量无法由工具表达的强制协作约束。

分页、DTO 示例、缓存实现、Flowable 配置、Repository 选型、审计字段写入和第三方框架具体参数不在 `AGENTS.md` 重复维护。

## OpenSpec 收口

### Change 分类与归档

归档前按内容分类，不以“任务全部勾选”作为机械合并规格的唯一条件：

- 业务或 API 能力 change：校准 delta 与当前实现后，正常归档并更新 `openspec/specs/`。
- 工程治理、工具链和纯文档 change：使用 `openspec archive --skip-specs`，避免产生伪业务规格。
- 混合 change：先将仍有效的业务要求合并到对应当前规格，再使用 `--skip-specs` 归档原 change。
- `archive-mvp-foundation`、`archive-pc-mvp`、`code-maintainability` 等里程碑或工程治理规格不作为长期业务 capability 保留。
- `add-flowable-approval-workflow` 保持活动状态，其未完成任务不在本次治理中实施。

归档完成后，`openspec/changes/` 原则上只包含真实活动的 change。

### OpenSpec 配置

`openspec/config.yaml` 补充以下项目上下文和制品规则：

- 项目技术栈、目录边界和中文制品要求。
- proposal 必须包含目标、非目标和受影响真相源。
- design 必须包含关键决策、替代方案、失败路径和迁移策略。
- spec 只承载可验收业务或 API 要求，不写代码结构和工具命令。
- tasks 必须可验证，并明确对应测试或检查命令。

`openspec/README.md` 不再手工维护完整活动 change 列表，改为引导使用 `openspec list` 查询；当前规格索引继续保留并接受一致性检查。

## 产品、设计与工程文档迁移

### 产品与设计

- `PRODUCT.md` 保留用户、产品目标、工作台定位和产品原则，移除构建工具与部署差异。
- `DESIGN.md` 清除 `.ant-*` 和其他 Ant Design 残留，统一 Element Plus 口径。
- 视觉规范只由 `web/` 消费；`frontend-core/` 不承担页面、UI 壳层或视觉组件职责。
- 保留组件库优先、设计 Token、状态完整性、信息密度和可访问性规则。

### 工程文档

- `docs/architecture.md` 保留顶层结构、模块边界、持久化边界和运行时组件。
- 权限刷新定时器、具体 composable、页面拆分状态等实现快照从架构文档移除。
- 已经进入 OpenSpec 的草稿查询、游标和错误恢复合同不在架构文档重复维护。
- `docs/development.md` 与 `CONTRIBUTING.md` 统一使用 Taskfile 暴露的命令。
- `CONTRIBUTING.md` 只描述贡献流程，不复制后端和 API 详细规则。
- `docs/deployment.md` 与 `docs/architecture.md` 删除本地文件存储兼容表述，统一为 S3 兼容存储。
- 缓存默认值以实际 `application.yaml` 的 Caffeine 配置为准。
- `docs/superpowers/` 增加醒目的历史资料说明，不逐份重写已有计划和设计。

## 项目技能治理

- 持久化技能删除默认继承 `CrudRepository` 或项目自定义基础接口的建议。
- 持久化技能改为要求 Repository 直接使用 `@Repository` 并只声明真实需要的显式注解方法。
- 审计说明与统一审计上下文及双拦截器设计保持一致。
- API 技能引用 `openspec/specs/api-contract/spec.md`，只保留审查流程和决策提示。
- 技能不得复制可以直接链接到 OpenSpec 或架构文档的大段项目规则。

## Jakarta Data Repository 设计

删除 `server/src/main/java/github/luckygc/am/common/repository/DataRepository.java`。项目 Repository：

- 直接标注 Jakarta Data `@Repository`，不继承项目基类，也不继承 `BasicRepository`、`CrudRepository` 等通用接口。
- 只声明当前 Service 实际调用的方法。
- 所有方法显式标注 `@Find`、`@Insert`、`@Update`、`@Delete`、`@Query` 或 Hibernate `@HQL`。
- 不使用 Query by Method Name 作为查询合同。
- 不声明或调用 `save`、`upsert`、`@Save`。
- 相同的 `insert`、`update`、`findById` 可以在不同实体 Repository 中重复声明；这些方法属于各实体边界，不再为了消除少量重复而扩大所有 Repository 的公开面。
- 删除后没有真实调用的方法，不保留兼容转发。

ArchUnit 固化 Repository 包位置、`@Repository` 标注、禁止通用基础接口、显式操作注解、禁止 save/upsert，以及 Web 层不得直接依赖 Repository 或 Mapper。

## 统一审计上下文

### 审计上下文

基础设施层提供统一 `AuditContextProvider`。每次持久化写入获得：

- 非空 `now`。
- 可空 `userId`。

`now` 由可测试的 `Clock` 产生；`userId` 从当前 Spring Security `Authentication` 解析。有认证用户时使用真实用户 ID；启动初始化、Quartz 清理和其他无认证后台任务写入 `null`，不虚构 `0` 或内置系统用户。

如果业务合同要求必须记录操作人，Service 在进入持久化前必须要求认证主体；通用审计层不猜测业务操作人。

### Hibernate 审计拦截器

保留跨模块共享的 `CreationAuditable`、`UpdateAuditable` 字段合同。Hibernate 无状态会话审计拦截器执行：

- insert：覆盖写入 `createdAt`、`updatedAt`、`createdBy`、`updatedBy`。
- update：覆盖写入 `updatedAt`、`updatedBy`。
- 不修改 insert 后的创建审计字段。
- 不接受 Service 预填的通用审计值作为真相源。

固定实体移除 `@CreationTimestamp`、`@UpdateTimestamp`，避免时间字段出现第二来源。

### MyBatis 审计拦截器

MyBatis 使用官方插件机制注入参数，不改写 SQL：

- 拦截 `Executor.update` 以及用于 `INSERT ... RETURNING` 的 `Executor.query`。
- 在动态 SQL 生成前向参数 Map 注入保留键 `_audit`。
- `_audit` 由拦截器强制覆盖，调用方不得伪造。
- 需要通用审计的 Mapper 方法使用 `@Param`，保证参数容器可注入。
- Mapper XML 显式使用 `#{_audit.userId}`、`#{_audit.now}` 指定通用审计列。
- 删除 Mapper 方法中只服务于 `created_by`、`updated_by` 的 `userId` 参数。
- 不解析最终 SQL，不根据表名自动增加列，不新增 SQL parser。

`created_at`、`updated_at`、`created_by`、`updated_by` 属于通用实体审计。`deleted_by`、`deleted_at`、`locked_by`、规则执行人、文件所有人和业务审计流水属于业务语义，继续由明确命令参数和 SQL 维护，不由通用拦截器覆盖。

### 失败语义

- `now` 缺失视为基础设施错误并立即失败。
- 无认证主体时通用用户审计字段写 `null`。
- SQL 引用了 `_audit` 但拦截器未注册时保留 MyBatis 明确错误，不静默降级。
- 业务必须记录操作人但主体缺失时，在 Service 边界拒绝执行。
- MyBatis 调用方传入同名 `_audit` 时由拦截器覆盖，不能影响审计结果。

## 可执行门禁

### 本地规范检查

Taskfile 新增 `governance-check`，执行：

- `openspec validate --all --strict --no-interactive`。
- 检查活动 change 中不存在任务全部完成却未归档的目录。
- 检查 `openspec/README.md` 当前规格索引与实际规格目录一致。
- 检查历史规范目录存在“非当前规范”声明。

`CONTRIBUTING.md` 将其列为提交前必需检查。本次不增加 GitHub Actions 或其他 CI 配置。

### 架构与审计测试

扩展 ArchUnit 或等价架构测试，验证：

- Repository 不继承项目或 Jakarta Data 通用 Repository 基接口。
- Repository 位于正确包并标注 `@Repository`。
- Repository 方法具有显式操作注解。
- 不存在 save、upsert、`@Save`。
- 固定实体不再使用 `@CreationTimestamp`、`@UpdateTimestamp`。

审计测试至少覆盖：

- 固定时钟下的认证用户 insert/update。
- 无认证用户 insert/update。
- Jakarta Data 无状态 Repository 四个通用字段写入。
- MyBatis 普通 insert/update。
- MyBatis `INSERT ... RETURNING`。
- MyBatis 动态表 insert/update。
- 调用参数不能伪造通用审计值。
- 业务审计字段不被通用拦截器覆盖。

## 实施顺序

### 第一批：OpenSpec 收口

- 分类并归档已完成 change。
- 合并仍有效的业务 delta。
- 保留未完成 Flowable change。
- 补充 OpenSpec 配置并简化索引。

### 第二批：规范与技能重构

- 压缩 `AGENTS.md`。
- 校准产品、设计、架构、开发、部署和贡献文档。
- 标记历史资料。
- 校准 API 与持久化技能。

### 第三批：Repository 与审计改造

- 先增加 Repository 边界和双拦截器测试。
- 删除项目自定义 Repository 基类。
- 为每个 Repository 显式声明真实所需方法。
- 实现统一审计上下文、Hibernate 拦截器和 MyBatis 拦截器。
- 迁移 Mapper XML，删除纯通用审计参数和重复 Service 赋值。
- 移除 Hibernate 时间戳注解。

### 第四批：本地治理门禁

- 增加 `governance-check` 及其脚本测试。
- 更新贡献流程和验证说明。
- 运行完整验证闭环。

## 风险与控制

- OpenSpec 历史 delta 可能已经落后于实现：归档前逐项校准，不机械合并。
- Repository 基类删除会产生广泛编译影响：先固化架构规则，再逐个 Repository 补齐真实调用方法。
- MyBatis 写入形态包含动态表、动态列和 `INSERT ... RETURNING`：拦截器同时覆盖 update/query，但只注入参数，不改写 SQL。
- 审计属于高风险横切能力：使用固定时钟、认证/匿名两类主体和数据库集成测试证明结果。
- 业务操作人和通用审计用户容易混淆：只删除纯通用审计参数，保留删除人、锁定人、规则执行人和文件归属等业务字段。
- 规范重构容易夹带实现扩展：不修改 API、业务状态机、Flowable 未完成任务和前端业务代码。

## 验证与完成标准

- `openspec validate --all --strict --no-interactive` 通过。
- 活动 change 中不存在任务全部完成却未归档的目录。
- `AGENTS.md` 不复制业务、API 和持久化实现细节。
- 不存在项目 Repository 基类或对 Jakarta Data 通用 Repository 基接口的继承。
- 所有 Repository 只声明实际需要的方法，并具有显式操作注解。
- 固定实体不再使用 `@CreationTimestamp`、`@UpdateTimestamp`。
- Hibernate 与 MyBatis 通用审计值均来自同一个审计上下文。
- 匿名后台写入时间字段完整，通用用户字段为 `null`。
- MyBatis SQL 不依赖自动 SQL 改写，动态表与 `INSERT ... RETURNING` 审计测试通过。
- `task governance-check`、后端格式检查、编译和完整测试全部通过。
- 未引入 GitHub Actions、新 SQL 解析库、Repository 兼容层或无关抽象。
