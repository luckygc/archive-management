## Context

当前档案元数据和动态路由能力已经形成初版：`am_archive_record` 作为统一主表，分类元数据决定动态表名，动态字段按分类表保存。新的检索和唯一规则会穿透字段定义、动态 DDL、记录写入、记录删除和档案库查询，因此需要先把数据归属和索引语义固定。

用户已确认全宗可以从 `am_archive_record` 移入分类动态表，并固定作为搜索字段；唯一规则不使用 `key_text`、`key_hash` 或唯一键投影表，而是通过动态 DDL 在分类动态表上创建联合唯一索引。PostgreSQL 环境已安装 `pg_textsearch`，首版全文检索可以直接基于该扩展创建 BM25 索引。

## Goals / Non-Goals

**Goals:**

- 将全宗编码保存到每张分类动态表，作为固定精确筛选字段和可选唯一规则范围。
- 将 `archive_no`、`archive_year`、档案状态、流程状态、密级、排序、归档时间、锁定信息等固定记录属性收口到 `am_archive_record`。
- 将分类动态表固定列收口为 `id`、`fonds_code`、动态字段、`deleted_flag`、`created_at`、`updated_at`。
- 将字段定义的搜索语义拆分为精确筛选和全文检索。
- 使用 MyBatis 承载动态列表、动态插入、动态记录加载、搜索和动态 DDL 参数拼装。
- 使用 PostgreSQL 部分唯一索引实现分类唯一规则。
- 使用 `pg_textsearch`、全文投影表和 outbox 实现关键词检索。
- 使用通用档案记录操作审计表记录创建、删除、锁定、解锁等业务操作事实。

**Non-Goals:**

- 不支持跨分类动态表做统一动态字段查询。
- 不实现动态字段物理列删除或已建字段类型变更。
- 不把 `archive_no` 等主表字段纳入首版唯一规则字段来源。
- 不实现 Elasticsearch、OpenSearch 或其他外部搜索引擎。
- 不在字段定义改为全文字段时同步阻塞重建历史投影；历史重建走单独任务或内部接口。

## Decisions

### 全宗下沉到分类动态表

`am_archive_record` 保留记录 ID、分类编码、分类名称、档号、档案状态、流程状态、密级、排序、归档时间、年度、业务锁和删除标记。全宗编码从主表移到分类动态表固定列 `fonds_code`，查询某分类档案时通过该分类动态表筛选全宗。

`archive_no`、`archive_year`、`archive_status`、`process_status` 等主表字段不进入 `am_archive_field`，也不作为动态表字段创建。前端表单必须把这些字段作为固定档案记录字段编辑，动态字段区域只渲染分类字段定义表中的真实动态字段。

这样可以让全宗和动态字段处在同一张物理表内，普通索引、精确筛选和唯一规则都由 PostgreSQL 原生索引表达，避免额外唯一键投影。代价是未选择分类时无法自然展示全宗或跨分类筛选全宗；首版档案库应以选择分类后的查询为主，未选分类只返回主表概览或空列表。

### 动态表固定包含逻辑删除字段

分类动态表新增 `deleted_flag boolean not null default false`。删除档案记录时，主表和对应动态表都标记删除，全文投影通过 outbox 删除对应投影行。

唯一索引必须使用 `where deleted_flag = false` 的部分唯一索引，否则逻辑删除后的唯一值无法释放。动态表自身持有删除标记后，唯一索引不需要跨表判断主表删除状态。

### 字段搜索标记拆分

`am_archive_field.searchable` 拆分为：

- `exact_searchable`：允许作为精确筛选条件，并可在建表或更新表时生成普通索引。
- `full_text_searchable`：字段值进入全文投影拼接文本。

两者可以同时为 `true`。固定字段 `fonds_code` 不进入字段定义表，但固定支持精确筛选。

### 唯一规则使用动态部分唯一索引

唯一规则表只保存规则定义，真实约束落在分类动态表索引上。索引列由规则字段顺序决定；`include_fonds=true` 时索引列前置 `fonds_code`。

首版字段来源只支持动态字段。空值语义沿用 PostgreSQL 默认唯一索引行为，即 `NULL` 不互相冲突；如果未来要求空值也冲突，再引入表达式索引或应用层归一化规则。规则新增、启用或字段组合变化时创建或重建对应索引；规则禁用或删除时删除对应索引。

### 全文检索使用投影表、outbox 和 pg_textsearch

全文投影表 `am_archive_record_search` 只保存每条记录的 `archive_record_id`、拼接后的 `search_text`、`index_version` 和投影自身维护时间。分类、全宗、删除、锁定、档案状态和流程状态仍以 `am_archive_record` 与对应分类动态表为准，不在搜索表复制。

记录新增、后续编辑、删除和历史重建先写入 `am_archive_record_search_outbox`。outbox 消费端按 `archive_record_id` 回读主表、分类动态表和字段定义，重新拼接全文文本并写入或删除投影。首版可以在事务内 drain outbox 保持当前页面可见性，后续可迁移到调度器或异步任务执行器。

关键词查询先访问投影表，通过 `search_text` 单列 BM25 索引得到匹配记录和 score，再回表查询主表和分类动态字段并应用分类、全宗、删除状态等过滤。投影表避免每个分类动态表都维护全文索引，同时不成为第二份业务状态真相源。

### MyBatis 承载动态 SQL 边界

动态表名、动态列名和索引名仍然只能从服务端元数据生成，并在 Service 层通过白名单校验后传入 Mapper。Mapper XML 使用 `${}` 只接收已校验标识符，值条件继续使用 `#{}` 参数绑定。

动态列表返回 `List<Map<String, Object>>`。项目已配置 `mybatis.configuration.call-setters-on-nulls: true` 和 `return-instance-for-empty-row: true`，需要补测试锁定动态列为 `null` 时仍保留 Map key。

### 档案记录操作审计覆盖业务操作

审计表使用 `am_archive_record_audit`，不按删除动作命名。创建、删除、锁定、解锁等档案记录业务操作都写入该表，固定记录 `operation_type`、档案记录 ID、全宗编码、分类编码、操作原因、操作人和操作时间。

删除动作的删除原因保存到通用 `operation_reason` 字段；锁定动作的锁定原因同样保存到 `operation_reason`。审计不保存记录快照，详情和状态追溯以主表、动态表和业务操作事实为准。

## Risks / Trade-offs

- 跨分类概览能力变弱 → 首版明确档案库主查询必须选择分类；未选择分类只返回主表通用字段或空结果。
- 动态索引 DDL 失败会影响规则保存 → 规则保存和索引创建放在同一事务边界内，失败时返回明确错误，避免规则已启用但索引不存在。
- PostgreSQL 标识符长度限制 → 动态索引名使用规则 ID 或短 hash 生成，避免直接拼长字段名。
- `NULL` 默认不冲突可能不符合个别业务预期 → 首版在规格中固定该语义，后续如需“空值也唯一”再新增规则选项。
- 全文字段变更后历史投影不自动同步 → 字段定义保存只记录需要重建，历史重建通过 outbox 补齐，避免元数据保存阻塞。
- `pg_textsearch` 环境依赖 → 迁移使用 `create extension if not exists pg_textsearch`，部署前由数据库环境保证扩展可用。

## Migration Plan

1. 当前后端版本低于 `1.0.0`，直接调整 `V20260622_0100__create_archive_tables.sql` 目标结构。
2. 将 `am_archive_record` 的全宗字段移出主表定义，并让动态建表固定创建 `fonds_code` 和 `deleted_flag`。
3. 将 `am_archive_field.searchable` 替换为 `exact_searchable`、`full_text_searchable`。
4. 新增唯一规则表、唯一规则字段表、全文投影表和全文投影 outbox 表。
5. 新增档案记录操作审计表，覆盖创建、删除、锁定、解锁等业务操作事实。
6. 在迁移中创建 `pg_textsearch` 扩展和全文 BM25 索引。

## Open Questions

无。
