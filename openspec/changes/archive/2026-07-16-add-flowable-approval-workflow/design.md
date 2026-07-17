## Context

项目当前是单 Spring Boot 应用，后端业务代码按 `module` 拆分，技术适配放在 `infrastructure`，并通过 ArchUnit 固化边界。审批流将被多个档案业务复用，不能让业务模块直接散落 Flowable `RuntimeService`、`TaskService`、`RepositoryService` 调用，否则后续替换引擎、统一审计和 API 合同都会失控。

Flowable 文档提供 Spring Boot starter，支持自动配置 BPMN process engine；针对只需要 BPMN 流程引擎的场景，可以使用 `flowable-spring-boot-starter-process`。本项目不引入 Spring Data JPA，Flowable 的 JPA 支持不是首版需求。

## Goals / Non-Goals

**Goals:**

- 使用 Flowable process engine 承担 BPMN 定义部署、实例运行、人机任务和历史能力。
- 建立项目自有审批流业务模块，统一流程定义、实例、待办、办理动作、历史记录和业务接入 API。
- 将 Flowable 调用封装在基础设施适配层，业务模块通过审批流模块公开服务接入。
- 首版支持顺序审批、候选人解析、同意、驳回、撤回或终止、流程进度和审批意见。
- 保持项目 API 风格：路径包含 `/api/v1`，自定义动作使用 AIP 冒号动作。

**Non-Goals:**

- 不引入 Flowable UI、Modeler、IDM、CMMN、DMN 和完整应用套件。
- 不把 Flowable 作为导入、AI 识别、批量归档等长业务编排运行时；这类 durable workflow 后续单独评估。
- 不允许业务模块直接依赖 Flowable 原生 Service、Entity 或表结构。
- 不支持运行中流程实例迁移到新定义版本。
- 首版不绑定具体档案业务，由业务模块后续通过审批流模块公开 Service 发起实例。

## Decisions

### 1. 只引入 Flowable BPMN process starter

使用 `org.flowable:flowable-spring-boot-starter-process`，不使用完整 `flowable-spring-boot-starter`。这样只启用审批流需要的 BPMN process engine，避免把 IDM、CMMN、DMN 等暂不需要的能力带入项目。

备选方案是完整 starter。它接入更简单，但默认范围过宽，不符合当前最小闭环和单体模块边界要求。

### 2. Flowable 放在基础设施适配层，审批语义放在业务模块

新增 `infrastructure/flowable` 封装 Flowable engine 调用，新增 `module/approval` 或 `module/flow` 承载审批流业务语义。档案业务只调用审批模块公开服务，例如发起审批、查询实例、撤回流程，不直接操作 Flowable 任务。

备选方案是在业务 Service 里直接注入 Flowable `RuntimeService` 和 `TaskService`。这种方式短期少写代码，但会让引擎 API 扩散到业务模块，违反当前架构边界。

### 3. 项目表保存审批业务扩展，Flowable 表保留原生命名

Flowable 原生 `ACT_*` 表属于第三方框架表，按仓库规则不强制改成 `am_模块_表名`。项目自有扩展表使用 `am_approval_` 前缀，例如保存流程定义草稿、业务绑定、审批意见快照和实例业务索引。

备选方案是完全依赖 Flowable 变量和历史表。这样会减少项目表，但业务查询、权限过滤、审计展示和后续引擎替换都会被 Flowable 表结构绑定。

### 4. 流程定义由项目模型生成 BPMN

首版前端维护项目自有流程定义模型，例如节点、审批人策略、允许动作和业务类型；后端发布时转换为 BPMN XML 并部署到 Flowable。运行时实例记录启动时使用的定义版本。

备选方案是直接让用户编辑 BPMN XML 或接入 Flowable Modeler。前者可用性差，后者引入的 UI 和权限体系过重。

### 5. 候选人按启动时上下文和任务创建时解析

流程定义保存候选策略，不直接保存可变用户快照。发起或进入节点时由审批模块根据业务上下文解析候选用户，并写入 Flowable task assignee/candidate users，同时保留项目自有任务快照供列表和审计使用。

备选方案是每次列表实时计算候选人。这样会让历史任务随组织变更漂移，也会增加待办列表查询复杂度。

### 6. 先做依赖兼容 PoC

虽然 Flowable 文档说明维护 Spring Boot 4.x 支持，项目当前使用 Spring Boot 4.1.0、Java 25 和 Hibernate 8 Beta，仍必须先通过最小依赖解析、应用上下文启动和空 BPMN 部署测试，再进入业务建模。

### 7. 首版候选人只支持指定用户

审批节点直接保存候选用户 ID 列表，发布时写入 BPMN 用户任务候选人，并在任务创建时同步到项目待办候选人关系表。首版不支持岗位、角色、表达式或运行时组织解析，避免在审批闭环内扩展组织授权模型。

### 8. 驳回直接终止实例

候选人提交驳回后，审批模块终止 Flowable 运行实例，将项目实例标记为已驳回并关闭未完成待办。首版不实现退回上一节点、重新提交或运行中流程迁移。

## Risks / Trade-offs

- Flowable 与 Spring Boot 4.1.0 或 Hibernate 8 Beta 存在兼容缺口 → 先做最小 Maven resolve 和 `@SpringBootTest` PoC，失败时停在依赖版本评估，不继续写业务层。
- Flowable 原生表较多，数据库可读性下降 → 将原生表视为框架内部表，项目查询只走审批模块 API 和项目扩展表。
- BPMN 能表达的流程过宽，容易把首版做复杂 → 首版只支持顺序审批和明确动作，复杂网关、并行会签、超时、委托后续作为增量。
- 业务模块绕过审批服务直接操作 Flowable → 增加 ArchUnit 规则，禁止 `module..` 直接依赖 `org.flowable..`，只允许 `infrastructure.flowable` 和审批模块适配包使用。
- Flowable 历史数据和项目扩展表一致性风险 → 启动、办理、撤回等动作在同一事务边界内更新项目表并调用 Flowable，必要时补偿扫描未同步状态。

## Migration Plan

1. 增加 Flowable 依赖和最小配置，在本地 PostgreSQL 验证应用启动、Flowable 表初始化和简单 BPMN 部署。
2. 新增审批模块扩展表和基础 DTO/API，不接入具体档案业务。
3. 实现流程定义发布、实例启动、待办查询和任务办理的竖切链路。
4. 通过项目自有 API 验证通用业务绑定、候选用户办理和历史展示，不接入具体档案业务。
5. 通过 ArchUnit、后端测试和 OpenSpec validate 固化边界。

回滚时移除 Flowable starter、审批模块注册入口和未使用的扩展表迁移；Flowable 原生表不承载项目业务真相源，回滚不影响已有档案数据。

## Confirmed Scope

- 首版提供通用审批闭环，不接入具体档案业务。
- 候选人策略只支持指定用户。
- 驳回动作终止当前流程实例。
