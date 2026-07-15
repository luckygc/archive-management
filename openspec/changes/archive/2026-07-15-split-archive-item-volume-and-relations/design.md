# Design

## Object Model

档案对象拆成两个一等资源：

- `archive-item`：条目、卷内、普通档案。大多数分类只需要 item。
- `archive-volume`：案卷。只有分类启用案卷管理时才使用。

`am_archive_item.volume_id` 指向 `am_archive_volume.id`。未组卷 item 的 `volume_id` 为空。volume 不再作为 item 表中的一行存在，因此 item 主表不需要 `archive_level`。

## Tables

固定表：

- `am_archive_item`
- `am_archive_volume`
- `am_archive_item_electronic_file`
- `am_archive_volume_electronic_file`
- `am_archive_item_search`
- `am_archive_volume_search`
- `am_archive_item_search_outbox`
- `am_archive_volume_search_outbox`
- `am_archive_item_audit`
- `am_archive_volume_audit`
- `am_archive_item_relation`
- `am_archive_item_line_table`
- `am_archive_item_line_field`

动态表：

- `am_archive_item_data_{category}`
- `am_archive_volume_data_{category}`
- `am_archive_item_physical_{category}`
- `am_archive_volume_physical_{category}`
- `am_archive_item_line_{category}_{line_table}`

动态表使用主表 ID 作为 `id` 或 `item_id` 外键。item 和 volume 的系统字段不复制到分类动态表。

## Item Lines

item 明细表不是档案分类。它是分类下的重复行定义，用于会计凭证明细、付款计划、履约节点等场景。

第一阶段支持：

- 维护分类下的 line table 定义。
- 维护 line field 定义。
- 为 line table 创建动态明细数据表。
- item 详情可返回明细表定义和明细行数据。
- 全文投影可将明细行文本拼入 item 的 `search_text`。

第一阶段不支持跨多个明细表的复杂布尔筛选，只支持同一明细表内的 exists 条件作为后续扩展点。

## Item Relations

`am_archive_item_relation` 只表达 item-to-item 业务关联。它不表达案卷收纳关系，也不关联 volume。

字段：

- `source_item_id`
- `target_item_id`
- `relation_type`
- `remark`
- `sort_order`
- 审计和逻辑删除字段

约束：

- 不允许自关联。
- 同一 source、target、relation_type 的未删除关系唯一。
- 读取关联目标时必须走 item 权限过滤。
- 详情页默认只展示一层关联；图谱接口最大深度为 2，并必须去重和防循环。

## APIs

资源路径：

- `GET /api/v1/archive-items`
- `POST /api/v1/archive-items:search`
- `POST /api/v1/archive-items:discover`
- `POST /api/v1/archive-items`
- `GET /api/v1/archive-items/{item}`
- `PATCH /api/v1/archive-items/{item}`
- `DELETE /api/v1/archive-items/{item}`
- `POST /api/v1/archive-items/{item}:lock`
- `POST /api/v1/archive-items/{item}:unlock`
- `GET /api/v1/archive-items/{item}/relations`
- `POST /api/v1/archive-items/{item}/relations`
- `DELETE /api/v1/archive-items/{item}/relations/{relation}`
- `GET /api/v1/archive-volumes`
- `POST /api/v1/archive-volumes`
- `GET /api/v1/archive-volumes/{volume}`
- `PATCH /api/v1/archive-volumes/{volume}`
- `DELETE /api/v1/archive-volumes/{volume}`
- `GET /api/v1/archive-volumes/{volume}/items`
- `POST /api/v1/archive-volumes/{volume}:addItem`

DTO 字段使用 `itemId`、`volumeId`，不再使用 `recordId` 或 `parentId` 表达条目和案卷关系。

## Search

item 搜索投影包含 item 固定字段、item 分类动态字段、item 实物字段和 item 明细行文本。volume 搜索投影只包含 volume 自身字段，不拼接其下 item 文本。

后台管理查询仍使用数据库结构化查询；发现型搜索通过 PostgreSQL `pg_trgm` 投影表合并权限、全宗、分类和逻辑删除过滤。
