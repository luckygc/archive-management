# 档案元数据服务可维护性治理设计

## 背景

`ArchiveMetadataService` 当前约两千行，承担全宗、分类方案、全宗可用分类、分类、字段、字段布局、动态表构建、唯一规则和 DTO 映射等职责。它已经支撑现有 MVP 和近期分类方案改造，但继续在一个类内叠加能力会让后续修改更难判断影响面，也会增加 AI 或人工修改时误碰无关逻辑的概率。

本轮治理只处理 `ArchiveMetadataService` 内部可维护性，不改变 HTTP API、OpenSpec 合同、数据库结构或前端行为。

## 目标

- 保留 `ArchiveMetadataService` 作为现有 Controller 和其他模块调用的元数据门面。
- 把字段定义、字段布局、动态表 DDL 和唯一规则从主服务中拆成职责明确的包内协作类。
- 保持现有公开方法、嵌套请求/响应 record 和业务错误语义不变，降低回归风险。
- 用现有 `ArchiveMetadataServiceTests` 锁住外部行为，并为拆出的高复杂协作类补充聚焦单测。
- 让后续新增分类方案、字段、布局或唯一规则能力时，有明确落点，而不是继续扩大主服务。

## 不做范围

- 不拆 Controller，不修改 `/api/v1/archive-*` 路径。
- 不把 `ArchiveMetadataService` 的所有嵌套 DTO 一次性搬出。
- 不改 OpenSpec，不新增业务能力。
- 不重写动态表模型、字段模型、分类方案模型或唯一规则模型。
- 不把动态表 SQL 从 MyBatis 迁到 Jakarta Data Repository。
- 不为本轮治理引入抽象接口、策略工厂、配置开关或兼容分支。

## 当前问题

当前主服务内职责大致混在以下区域：

- 全宗、分类方案、分类和全宗可用分类范围的固定实体 CRUD。
- 字段创建、更新、删除、控件默认值、字段编码校验、字段类型到 SQL 类型映射。
- 动态表名、建表、补列、普通索引、唯一索引和索引删除。
- 字段布局读取、默认布局生成、公共布局保存、字段 DTO 布局合成。
- 唯一规则请求校验、规则字段关系替换、字段 searchable 同步和动态表唯一索引同步。
- Map 行结果到 DTO 的转换工具。

这些逻辑都属于元数据模块，但变更原因不同。把它们全部留在一个类里，会让小改动也需要理解整条元数据链路。

## 设计方案

### 主服务门面

`ArchiveMetadataService` 继续作为元数据模块对外门面：

- Controller 继续只依赖 `ArchiveMetadataService`。
- `ArchiveItemRoutingService` 等现有调用方继续使用 `ArchiveMetadataService` 的现有方法。
- 事务边界优先保留在当前公开用例方法上。
- 主服务负责固定实体用例编排、权限调用方传入的用户审计字段、跨协作类组合和最终 DTO 返回。

第一轮实现后，主服务仍会保留部分映射和加载逻辑；只有能形成稳定边界的复杂块先拆出去。

### 动态表协作类

新增包内协作类 `ArchiveDynamicTableService`，负责动态表和索引相关逻辑：

- 根据分类和档案层级计算动态表名。
- 校验动态表是否已创建。
- 构建或补齐 item / volume 动态表。
- 字段变更后补列和建普通索引。
- 唯一规则创建、更新、删除时同步唯一索引。
- 保留 MyBatis `ArchiveMapper` 作为动态 SQL 和 DDL 入口。

该类不暴露到 `web` 层，不承载 HTTP 请求/响应 DTO，不处理固定实体 Repository 写入。

### 字段定义协作类

新增包内协作类 `ArchiveFieldDefinitionService`，负责字段定义的纯业务规则：

- 字段编码格式和保留字段名校验。
- 字段请求值标准化。
- 字段作用域、档案层级和控件类型校验。
- 字段类型到 PostgreSQL SQL 类型映射。
- 字段默认编辑控件和列表宽度规则。

如果某个方法只依赖入参、枚举和常量，优先保持为包内纯方法，便于单测。它不直接写 Repository，避免把字段实体生命周期拆散。

### 字段布局协作类

新增包内协作类 `ArchiveFieldLayoutService`，负责布局相关逻辑：

- 读取公开布局。
- 生成默认布局。
- 保存公共布局配置。
- 把布局配置合成到字段 DTO。
- 根据表格、详情、编辑等 surface 计算显示、顺序、列宽和栅格跨度。

布局表是固定项目表，继续使用 `ArchiveFieldLayoutDataRepository`。布局协作类可以依赖字段定义规则，但不负责创建或删除字段。

### 唯一规则协作类

新增包内协作类 `ArchiveUniqueConstraintService`，负责唯一规则相关逻辑：

- 校验唯一规则请求。
- 加载并校验字段集合。
- 替换规则字段关系。
- 同步唯一规则涉及字段的 searchable 状态。
- 调用 `ArchiveDynamicTableService` 同步唯一索引。
- 映射唯一规则响应。

唯一规则会同时触及固定表关系和动态表索引，因此它是独立协作类，而不是藏在字段定义或动态表服务里。

## 数据和调用流

创建字段时：

1. `ArchiveMetadataService.createField(...)` 加载分类并校验分类可写。
2. `ArchiveFieldDefinitionService` 标准化并校验字段请求。
3. `ArchiveMetadataService` 写入 `ArchiveFieldDataRepository`。
4. 如果动态表已创建，`ArchiveDynamicTableService` 补列和索引。
5. 返回现有 `ArchiveFieldDto`。

保存字段布局时：

1. `ArchiveMetadataService.savePublicFieldLayout(...)` 加载分类和字段。
2. `ArchiveFieldLayoutService` 校验 layout item 并写入布局表。
3. `ArchiveFieldLayoutService` 合成现有 `ArchiveFieldLayoutDto`。

创建或更新唯一规则时：

1. `ArchiveMetadataService` 保持现有公开入口。
2. `ArchiveUniqueConstraintService` 校验请求、写规则实体和规则字段关系。
3. `ArchiveUniqueConstraintService` 调用 `ArchiveDynamicTableService` 同步唯一索引。
4. 返回现有 `ArchiveUniqueConstraintDto`。

## 错误处理

- 保持现有 `ResponseStatusException` 和 `BadRequestException` 语义，不在本轮统一替换错误类型。
- 拆出的协作类抛出的错误消息应与原行为一致，避免破坏已有测试和前端错误回填。
- 动态表未创建、字段编码非法、字段类型不兼容、父级分类不合法、唯一规则字段非法等错误保持原状态码和消息。

## 测试策略

后端测试分三层：

- 保留并扩展 `ArchiveMetadataServiceTests`，确认现有公开入口行为不变。
- 为 `ArchiveFieldDefinitionService` 增加纯规则测试，覆盖字段编码、保留字段、字段类型和控件规则。
- 为 `ArchiveDynamicTableService` 和 `ArchiveUniqueConstraintService` 增加 mock 级单测，覆盖动态表补列、索引同步和唯一规则字段关系替换。

本轮不要求数据库集成测试覆盖所有 DDL 分支；已有 MyBatis XML 合同测试和 metadata service 测试继续作为主验证。

## 验证命令

后端改动后至少运行：

```bash
cd /home/gc/dev/archive-management/server && mvn -q -Dtest=github.luckygc.am.module.archive.metadata.ArchiveMetadataServiceTests test
```

如果新增协作类测试，则同步运行对应测试类。收尾运行：

```bash
cd /home/gc/dev/archive-management/server && mvn -q -DskipTests test-compile
cd /home/gc/dev/archive-management/server && mvn -q -Dtest=github.luckygc.am.architecture.ArchitectureRulesTest test
```

涉及 Java 格式时运行：

```bash
cd /home/gc/dev/archive-management && make server-format-check
```

## 风险和约束

- 动态表 DDL、唯一索引和字段 searchable 状态有隐式耦合，拆分时必须先补测试再移动逻辑。
- `ArchiveMetadataService` 的嵌套 DTO 被多个模块引用，本轮不迁移 DTO，避免制造大范围 import churn。
- 拆出的类保持包可见或模块内 `@Service`，不新增公共 API。
- 不追求一次把主服务降到很小；第一轮成功标准是职责边界变清楚、后续改字段/布局/唯一规则有明确落点。

## 第一轮完成口径

- `ArchiveMetadataService` 对外方法签名保持不变。
- 字段定义、布局、动态表和唯一规则四类复杂职责从主服务拆出，并保持在 `metadata.service` 包内。
- 现有元数据服务测试通过。
- 架构测试通过。
- 没有新增兼容分支、配置开关或无真实收益的接口抽象。
