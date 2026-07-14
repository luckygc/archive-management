# 前后端代码精简与 Spring Bean 边界治理设计

## 背景

项目核心源码约 3.56 万行，复杂度集中在少数前端 API、类型、页面和后端 Service 中。当前主要问题不是缺少抽象，而是部分业务边界过大、同一能力存在门面与专项 Service 两个入口、Spring Bean 的公开方法被当作本类内部复用方法，以及前端不同业务域共同堆积在 `archive.ts` 中。

本设计采用“规则先行、热点增量收敛”的方式治理。它取代 `2026-07-08-archive-metadata-service-maintainability-design.md` 中“长期保留 `ArchiveMetadataService` 作为总门面”的设计决定。已有字段定义、字段布局、动态表和唯一约束专项 Service 可以继续使用，但最终应由调用方依赖真实业务能力，而不是经过只做转发的门面。

## 目标

- 删除无真实多实现需求的业务接口，业务模块中的单实现能力直接使用具体类。
- 禁止同一 Spring Bean 的 public 方法调用本类另一个 public 方法。
- 用自动化架构测试固化以上规则，避免后续回退。
- 收缩 `ArchiveMetadataService` 和 `ArchiveItemRoutingService` 的职责、依赖与公开方法。
- 按业务域拆分前端 API 和类型巨石，删除已经存在公共实现的局部重复。
- 保持现有 HTTP API、数据库结构、权限、审计和前端业务行为稳定。

## 明确约束

### 单实现业务能力不使用接口

业务模块中的 Service、Manager 和领域协作类，如果只有一个实现且不存在当前已经发生的多实现协作，直接注入具体类，不新增接口。

以下类型不属于应删除的单实现业务接口：

- Jakarta Data Repository 和 MyBatis Mapper 等框架声明式合同。
- 已有多个实现并通过集合注入、选择器或标准 Bean 协作的策略合同，例如 `ExpiredDataCleaner` 和 `FileLinkTargetResolver`。
- 用于隔离业务模块与基础设施实现的稳定端口，例如文件存储合同。
- OpenSpec 明确要求可切换实现的标准 Bean，例如全文检索 provider。
- 实体共同实现的审计能力标记接口。

不得为了“以后可能增加实现”保留业务接口、工厂或适配层。

### Spring Bean public 方法不做本类内部复用

同一 Spring Bean 中，一个 public 方法不得调用本类另一个 public 方法，包括省略 `this` 的直接调用和显式 `this.method()` 调用。

处理方式按语义选择：

1. 逻辑只供本类复用：提取命名明确的 private 方法，所有 public 入口共同调用该 private 方法。
2. 被调用能力是独立业务用例且需要自己的事务、权限或协作边界：移动到另一个具体 Spring Bean，通过构造器注入调用。
3. 仅为默认参数形成的 public 重载：保留真正需要的公开入口，其余改为 private 核心方法或由调用方显式构造语义对象。

禁止通过 self 注入、`ApplicationContext.getBean(thisType)`、暴露代理或手工代理绕过规则。跨 Spring Bean 调用对方 public 方法是正常的模块协作，不在禁止范围内。

## 方案选择

### 采用方案：规则先行、热点增量收敛

先增加会失败的架构测试，得到完整违规清单；再按业务边界逐项修复。每完成一批，运行聚焦测试和架构测试。

该方案比一次性重写更容易确认行为等价，也能确保重构期间不会继续引入相同问题。它比只做局部重复删除更完整，因为用户指定的 Spring Bean 和接口约束会成为长期仓库规则。

### 未采用：一次性重写巨型 Service

一次性拆完元数据、档案记录、治理和前端页面会同时改变构造依赖、类型位置和调用关系，回归范围过大，不符合最小闭环原则。

### 未采用：仅做机械拆文件

只把大文件切成更多文件不会减少公开入口、依赖和状态源，也无法解决同 Bean public 自调用，因此不作为完成标准。

## 后端设计

### 架构规则

在现有 `ArchitectureRulesTest` 中增加两项检查：

- Spring 组件类的 public 方法不得调用同一所有者类的其他 public 方法。
- `module..service..` 和 `module..manager..` 中的业务接口必须存在至少两个项目内实现；框架合同和明确稳定端口按包或类型白名单排除。

规则错误信息必须列出调用方、被调用方或单实现接口及其实现类，便于直接定位。白名单只能覆盖前述明确例外，不接受用大包通配掩盖违规。

### 元数据模块

`ArchiveMetadataService` 当前同时承担全宗、分类方案、分类、字段、布局、动态表和唯一约束入口。治理方向为：

- 全宗、分类方案、分类和分类范围等固定实体能力按真实业务边界形成具体 Service。
- 字段定义、布局、动态表和唯一约束继续由现有专项 Service 承担。
- Controller 依赖对应具体 Service，不再通过总门面转发所有能力。
- 跨模块调用依赖目标模块公开的具体 Service，不直接访问 Repository 或 Mapper。
- public 重载调用统一收敛到 private 核心方法；不保留只为内部复用的 public 方法。

迁移期间可以分批调整 Controller 和调用方，但每批完成后不能留下“新 Service + 旧门面转发”两套长期入口。

### 档案记录模块

`ArchiveItemRoutingService` 当前同时承担查询、写入、权限、锁定、删除、关系和动态 SQL 条件构造。按用例拆为具体类：

- 查询与分页：负责普通管理查询、全文发现、已删除查询和查询条件构造。
- 写入与状态：负责创建、修改、删除、锁定和解锁。
- 关系：负责关系列表、创建和删除。

拆分以减少依赖和公开方法为准。若某段逻辑仅为查询内部算法，应保持 private，不创建新的 Spring Bean。

### 审计字段

固定实体通过 Jakarta Data 无状态 Repository 写入时，以 `SecurityAuditingInterceptor` 为用户审计字段的唯一技术来源。先用现有集成测试和新增聚焦测试证明 insert、update、delete 所需路径会触发拦截器，再删除 Service 中重复的 `setCreatedBy`、`setUpdatedBy`。

以下路径继续显式维护：

- MyBatis 写入。
- 业务合同明确指定操作人而非当前安全上下文的记录。
- 业务审计流水。
- 经验证不经过 Hibernate 审计拦截器的写入路径。

## 前端设计

### API 与类型按业务域组织

将 `web/src/shared/api/archive.ts` 和 `web/src/shared/types/archive.ts` 按稳定业务边界拆分：

- `archive-metadata`
- `archive-items`
- `archive-governance`
- `archive-ontology`
- `archive-rules`
- `authentication`
- `authorization`
- `organization`

页面直接依赖所属业务域文件。共享分页合同等真正跨域类型放在窄小的公共文件中。不得增加 Endpoint Factory、前端 Repository、通用 CRUD API 类或额外状态层。

### 页面职责

大页面只在拆分能形成完整业务区域时提取组件，例如档案记录查询、记录编辑、电子文件和操作审计。页面继续负责选中对象、跨区域刷新和路由级权限编排。

不按模板、样式或行数机械拆分，也不创建参数矩阵式通用 CRUD 页面。

### 重复代码

优先删除确定重复：

- 页面局部 `errorMessage` 改用 `frontend-core` 已有实现。
- 相同的请求参数序列化、下载打开和取消确认判断，在语义一致且至少已有多个调用方时收口。

加载哪个区域、成功后刷新什么数据、关闭哪个弹窗等业务副作用保留在页面用例中，不隐藏进通用异步包装器。

## 数据流与事务

- Controller 只调用一个明确业务用例入口，不访问 Repository 或 Mapper。
- 写入事务放在实际完成原子状态变更的具体 Service public 方法上。
- public 方法需要共享实现时共同调用 private 核心方法，private 方法不依赖 Spring 代理语义。
- 跨 Bean 事务协作通过构造器注入发生，调用方能够从依赖关系看到副作用来源。
- 固定实体继续使用 Jakarta Data Repository；动态表、动态字段、复杂搜索和批处理继续使用 MyBatis。

## 错误处理

- 不改变现有 ProblemDetail、错误码、字段错误和 HTTP 状态合同。
- 拆分 Service 时保留原业务校验位置或移动到唯一负责该不变量的具体 Service。
- 不把服务端业务不变量下沉为仅前端校验。
- 前端继续使用 `frontend-core` 统一解析 HTTP 错误，页面只补充场景 fallback 文案。

## 测试策略

严格按 TDD 推进：

1. 先增加 Spring Bean public 自调用架构测试并确认它因现有违规失败。
2. 先增加单实现业务接口架构测试并确认测试能识别人工构造的违规样本或现有违规。
3. 每次修复一个聚焦业务边界，运行原有行为测试确保结果不变。
4. 审计字段删除前先运行或补充拦截器集成测试，确认测试能够在移除拦截器时失败。
5. 前端 API/type 移动依靠类型检查和页面测试；重复工具删除运行对应单测。

最终验证：

```bash
cd /home/gc/dev/archive-management/server
mise exec -- mvn -q spotless:check test

cd /home/gc/dev/archive-management
pnpm check
pnpm test
pnpm build
```

如果数据库集成测试依赖容器环境无法运行，应保留失败输出并明确区分环境失败与代码失败，不能用仅编译结果替代完整验证结论。

## 实施顺序

1. 更新协作规则和 ArchUnit 测试，得到真实违规清单。
2. 修复同 Bean public 自调用，不改变 HTTP 合同。
3. 检查并删除单实现业务接口；保留并记录明确例外。
4. 收缩元数据门面和档案记录巨型 Service，每批完成一个业务闭环。
5. 验证并收口固定实体审计字段写入源。
6. 拆分前端 API/type 巨石并删除确定重复。
7. 根据页面真实职责拆分热点页面，避免通用 CRUD 抽象。
8. 运行完整验证并按本设计逐项审计。

## 完成标准

- 架构测试能够阻止同一 Spring Bean 的 public 方法调用本类其他 public 方法。
- 当前生产代码不存在上述自调用违规。
- 业务模块不存在无明确例外的单实现 Service/Manager 接口。
- 元数据和档案记录模块不再依赖仅做转发的总门面维护双入口。
- 固定实体用户审计字段只有一个已验证技术来源，例外路径清楚可查。
- 前端 API 和类型按业务域组织，不再由单一 `archive.ts` 汇集认证、授权、组织和所有档案能力。
- 前后端完整检查、测试和构建通过，或明确记录无法执行的外部环境依赖。
- 未新增通用 CRUD 框架、接口预留、适配层、配置开关或兼容分支。

## 风险控制

- 拆 Service 时以现有 HTTP 合同和测试为边界，不顺手修改资源路径或响应结构。
- 不同时进行数据库迁移和模块拆分。
- 不把文件数量、类数量或行数作为单独目标；只有依赖、公开入口、重复状态或副作用来源实际减少才算优化。
- 每批改动保持可编译、可测试，避免长期保留半迁移状态。
