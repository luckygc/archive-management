# split-archive-item-volume-and-relations

## Why

当前档案主模型使用统一 `archive-record` 概念，并通过 `archive_level` 区分案卷和卷内条目。这个模型让案卷、条目、分类动态表、全文投影和关联档案都围绕同一张主表展开，后续继续扩展会让资源命名和业务语义变得含混。

本变更将核心对象收口为：

- `archive-item`：条目、卷内或普通档案，是默认档案对象。
- `archive-volume`：案卷，是启用案卷管理分类下的可选对象。
- item 明细子表：承载会计凭证明细等用户可设计的重复行数据。
- item 关联：只做条目与条目的业务关联，不做案卷关联，也不表达案卷收纳关系。

## What Changes

- 将档案条目主资源命名为 `archive-items`，数据库主表命名为 `am_archive_item`。
- 将案卷拆为独立资源 `archive-volumes`，数据库主表命名为 `am_archive_volume`。
- 条目通过 `volume_id` 归入案卷，不再使用 `archive_level + parent_id` 在同一主表内表达层级。
- 分类动态表继续按分类建表，但只保存分类业务字段；系统身份、权限、锁定、审计、附件、搜索投影和关联外键由 item/volume 主表承载。
- 新增 item 明细子表定义和动态明细数据表，用于会计凭证明细等重复行数据。
- 新增 item-to-item 关联表，并限制嵌套关联展示深度，避免循环展开和权限泄露。

## Impact

- 需要更新数据库迁移、样例数据、Mapper、服务 DTO、Controller URL 和 OpenSpec 合同。
- 旧 `archive-records` API 将被 `archive-items` 和 `archive-volumes` 替代。
- 旧 `archive_level` 对 item 主表不再存在；字段定义和唯一规则仍可使用 item/volume 维度表达适用对象。
