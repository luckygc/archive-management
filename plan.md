# 档案动态字段检索和唯一规则实现计划

## 当前结论

没有需要继续澄清的问题。下一会话按以下口径实现：

- 动态档案查询改用 MyBatis，动态结果使用 `Map<String, Object>` 接收。
- 项目已配置 `mybatis.configuration.call-setters-on-nulls: true`，动态列值为 `null` 时也应保留 Map key；实现时补测试锁定这个行为。
- 精确搜索字段、全文检索字段、唯一规则都作为字段元数据和规则元数据管理，不把检索能力硬编码在业务查询里。
- 全宗编码从 `am_archive_record` 移到各分类动态表，作为固定精确搜索字段；分类动态表固定包含 `fonds_code` 和 `deleted_flag`。
- 唯一规则不使用 `key_text`、`key_hash` 或唯一键投影表，而是通过动态 DDL 在分类动态表上创建部分唯一索引。
- 全文检索采用“所有全文字段拼接成一个搜索文本”的投影模型，先建全文投影表和索引；历史索引重建单独处理。
- 首版全文检索直接使用已安装的 PostgreSQL `pg_textsearch` 扩展和 BM25 投影表模型。

## 数据模型调整

### 字段定义表

在 `am_archive_field` 增加：

- `exact_searchable boolean not null default false`
- `full_text_searchable boolean not null default false`

含义：

- `exact_searchable`：字段允许作为精确筛选条件，并可为该字段生成普通索引。
- `full_text_searchable`：字段进入全文拼接投影。
- 两者可以同时为 true。
- 枚举仍然是运行时动态选项，不新增物理字段类型。

### 唯一规则表

新增规则表：

```sql
am_archive_unique_rule
- id
- category_id
- rule_code
- rule_name
- include_fonds
- enabled
- deleted_flag
- created_by
- created_at
- updated_by
- updated_at
```

新增规则字段表：

```sql
am_archive_unique_rule_field
- id
- rule_id
- field_id
- field_order
```

说明：

- 支持单字段唯一，也支持多字段联合唯一。
- `include_fonds=true` 时唯一索引列前置动态表固定列 `fonds_code`。
- 字段来源首版只支持动态字段；如后续要把 `archive_no` 等主表字段纳入唯一规则，再扩展字段来源类型。

### 档案记录主表固定字段

`ArchiveRecord` / `am_archive_record` 承载所有跨分类一致的档案记录字段：

```sql
id
category_code
category_name
archive_no
archive_status
process_status
security_level
sort_order
archived_at
archive_year
locked_flag
lock_reason
locked_by
locked_at
deleted_flag
created_by
created_at
updated_by
updated_at
```

说明：

- `archive_year`、`archive_no`、`archive_status`、`process_status` 等固定字段不进入 `am_archive_field`。
- 前端新增和编辑表单必须在固定字段区域编辑这些字段，动态字段区域只渲染分类字段定义表中的字段。
- 后端字段定义接口必须拒绝使用固定字段编码创建动态字段。

### 分类动态表固定字段

每张分类动态表固定包含：

```sql
id bigint primary key references am_archive_record(id)
fonds_code varchar(100) not null
deleted_flag boolean not null default false
created_at timestamp not null default localtimestamp
updated_at timestamp not null default localtimestamp
```

说明：

- `fonds_code` 是每张分类动态表的固定列，固定支持精确筛选。
- `deleted_flag` 用于动态表部分唯一索引释放逻辑删除记录占用的唯一值。
- `am_archive_record` 不再保存 `fonds_code` 和 `fonds_name`。

唯一规则对应的索引示例：

```sql
create unique index uk_am_archive_record_item_1_rule_1
    on am_archive_record_item_1 (fonds_code, f_doc_no)
    where deleted_flag = false;
```

索引语义：

- `include_fonds=false` 时只按规则字段建唯一索引。
- `include_fonds=true` 时索引列前置 `fonds_code`。
- 空值使用 PostgreSQL 默认唯一索引语义，多个 `NULL` 不互相冲突。

### 全文检索投影表

新增：

```sql
am_archive_record_search
- id
- archive_record_id
- search_text
- index_version
- created_at
- updated_at
```

索引：

- 普通索引：`archive_record_id`
- 全文索引：对 `search_text` 建 BM25 索引

说明：

- `search_text` 是该记录所有 `full_text_searchable=true` 字段值拼接后的文本。
- 分类、全宗、删除、锁定、档案状态和流程状态都以主表和分类动态表为准，不复制到搜索投影表。
- 新增、修改、删除和历史重建先写入全文投影 outbox，由消费端回读主表、分类动态表和字段定义后维护投影。
- 删除档案记录时通过 outbox 删除对应搜索投影行，不在搜索投影表保存删除状态。

### 全文检索投影 outbox

新增：

```sql
am_archive_record_search_outbox
- id
- archive_record_id
- event_type
- status
- attempts
- last_error
- next_retry_at
- created_at
- updated_at
- processed_at
```

### 档案记录操作审计表

新增：

```sql
am_archive_record_audit
- id
- source_table_name
- source_record_id
- archive_record_id
- fonds_code
- category_code
- operation_type
- operation_reason
- operated_by
- operated_at
```

说明：

- 审计表记录档案记录创建、删除、锁定、解锁等业务操作，不只记录删除。
- `operation_type` 固定表达操作类型，例如 `CREATE`、`DELETE`、`LOCK`、`UNLOCK`。
- 删除原因、锁定原因等统一进入 `operation_reason`。
- 审计表不保存记录数据快照，状态真相源仍为主表和分类动态表。

## 后端实现步骤

### 1. OpenSpec

- 更新 `archive-metadata` 规格：字段定义支持精确搜索和全文检索标记。
- 新增唯一规则和全文投影要求。
- 更新 `archive-record-routing` 规格：查询支持精确筛选和全文关键词。
- 明确历史全文索引重建不阻塞字段定义保存。

### 2. 迁移脚本

在当前版本未到 `1.0.0` 的前提下，继续直接调整目标结构：

- 修改 `V20260622_0100__create_archive_tables.sql`。
- 从 `am_archive_record` 移除全宗字段。
- 动态分类表固定增加 `fonds_code` 和 `deleted_flag`。
- 增加字段定义检索标记。
- 增加唯一规则、唯一规则字段、全文搜索投影表。
- 迁移中创建 `pg_textsearch` 扩展和 BM25 索引。

### 3. MyBatis 迁移

新增 Mapper XML：

```text
server/src/main/resources/mapper/archive/ArchiveMetadataMapper.xml
server/src/main/resources/mapper/archive/ArchiveRecordMapper.xml
server/src/main/resources/mapper/archive/ArchiveSearchMapper.xml
```

Java Mapper 接口放在：

```text
server/src/main/java/github/luckygc/am/module/archive/mapper
```

迁移范围：

- `ArchiveRecordRoutingService` 中动态列表、动态插入、动态记录加载改为 MyBatis。
- `ArchiveMetadataService` 中复杂 SQL 和 DDL 迁到 MyBatis；archive 模块里现有 `JdbcClient` 使用点整体收口掉。
- 动态表名、动态列名必须继续只来自服务端字段定义，并通过白名单校验后传入 Mapper。

Map 查询要求：

- 动态列表查询返回 `List<Map<String, Object>>`。
- 补充测试确认动态列为 `null` 时 Map 中仍包含该列 key。
- 依赖现有配置：

```yaml
mybatis:
  configuration:
    call-setters-on-nulls: true
    return-instance-for-empty-row: true
```

### 4. 精确搜索

请求模型增加：

```text
exactFilters: Map<String, Object>
```

处理规则：

- 只允许过滤 `exact_searchable=true` 的字段。
- `fonds_code` 是分类动态表固定字段，固定允许精确筛选。
- 文本字段首版按等值或包含二选一，建议先按等值实现。
- 日期/日期时间后续可扩展范围查询，首版可先支持等值。
- 动态 SQL 条件必须使用字段定义解析出的 `column_name`。

自动建表时：

- 对 `exact_searchable=true` 字段创建普通索引。
- 字段从 false 改 true 后，下次生成/更新表时补索引。

### 5. 全文检索

请求模型增加：

```text
keyword: string
```

查询流程：

1. 未传 `keyword`：沿用主表 + 分类动态表查询。
2. 传 `keyword`：先查 `am_archive_record_search` 得到匹配 `archive_record_id` 和 score。
3. 再回表查主表和动态字段。
4. 排序优先按全文 score，再按主表排序。

投影维护：

- 创建记录后，从动态字段里取 `full_text_searchable=true` 字段值拼接 `search_text`。
- 删除记录时标记投影删除。
- 字段定义改为全文字段后，不同步重建历史数据，只记录需要重建。

历史重建：

- 新增 Service 方法：按分类重建全文投影。
- 首版可以先提供后端方法或内部接口，前端入口后续再补。
- 重建任务按分类分页扫描 `am_archive_record` 和动态表，批量 upsert 投影。

### 6. 唯一规则

元数据接口新增：

- 创建唯一规则
- 修改唯一规则
- 删除唯一规则
- 查询分类唯一规则

保存档案记录时：

1. 根据分类加载启用唯一规则。
2. 规则保存或启用时，在分类动态表上创建对应部分唯一索引。
3. `include_fonds=true` 的规则索引列前置 `fonds_code`。
4. 保存档案记录时写入分类动态表，由 PostgreSQL 唯一索引兜底约束。
5. 唯一冲突时返回明确错误。

删除档案记录时：

- 同步将该记录对应分类动态表行 `deleted_flag=true`，释放部分唯一索引占用。
- 写入 `am_archive_record_audit`，操作类型为 `DELETE`，并保存删除原因。
- 写入全文投影 outbox，消费后删除对应搜索投影行。

创建、锁定、解锁档案记录时：

- 同步写入 `am_archive_record_audit`，记录操作类型、操作人、操作时间；创建和修改写入全文投影 outbox。

## 前端实现步骤

### 字段定义页面

在字段表格和表单增加：

- 精确搜索
- 全文检索

表格、详情、编辑布局不在字段表单内配置，分类字段管理区域单独提供布局配置入口：

- 切换表格、详情、编辑布局。
- 拖拽字段维护该布局的顺序。
- 表格布局维护字段显示和列宽。
- 详情、编辑布局维护字段显示和跨列。

交互约束：

- 使用 Element Plus 开关。
- 不增加说明型大段文案。
- 字段类型、控件和搜索配置集中在字段表单里，布局配置独立维护。

### 唯一规则页面

在分类字段管理区域增加唯一规则管理区域：

- 列表展示规则名、规则编码、是否包含全宗、字段组合、状态。
- 新增/编辑规则时用多选字段维护组合顺序。
- 只允许选择当前分类字段。

### 档案库查询

在档案库工具栏增加：

- 全文关键词输入框。
- 精确筛选入口。

首版建议：

- 全文关键词直接放工具栏输入。
- 精确筛选先用下拉选择字段 + 输入值的简化形式，不做复杂查询构造器。

## 验证清单

- `openspec validate add-archive-record-search-and-unique-rules --strict`
- `openspec validate add-archive-metadata-routing --strict`
- `mvn test`，在 `server/` 目录执行
- `vp check`
- `vp test run --passWithNoTests`
- `git diff --check`

需要补的专项测试：

- MyBatis `Map` 查询动态列为 null 时仍包含 key。
- 字段启用精确搜索后，建表补索引。
- 全文字段拼接投影包含多个字段，空值字段不破坏拼接。
- 唯一规则单字段冲突。
- 唯一规则多字段联合冲突。
- 删除记录后动态表 `deleted_flag` 释放唯一索引占用。
- 已锁记录不能删除、不能加入案卷，后续编辑入口也必须拦截。

## 暂不做

- 不做动态字段物理列删除。
- 不做已建字段类型变更。
- 不把全文内容放入 `am_archive_record` 主表。
- 不在字段定义保存时同步重建历史全文索引。
- 不引入独立 Elasticsearch/OpenSearch。
