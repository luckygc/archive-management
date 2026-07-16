## ADDED Requirements

### Requirement: 条目和案卷分对象建模

系统 SHALL 将档案条目和案卷建模为独立资源和独立主表。

#### Scenario: 条目主表不保存层级枚举

- **WHEN** 系统创建档案条目
- **THEN** 系统 SHALL 将系统身份字段保存到 `am_archive_item`
- **AND** `am_archive_item` SHALL NOT 保存 `archive_level`
- **AND** 条目归入案卷时 SHALL 使用 `volume_id`

#### Scenario: 案卷使用独立主表

- **WHEN** 分类启用案卷管理
- **THEN** 系统 SHALL 使用 `am_archive_volume` 保存案卷系统身份字段
- **AND** 系统 SHALL NOT 将案卷作为 `am_archive_item` 中的一行保存

### Requirement: 分类动态表只保存业务字段

分类动态表 SHALL 只保存分类自定义业务字段，不复制条目或案卷主表系统字段。

#### Scenario: 条目分类动态表

- **WHEN** 系统为分类创建条目动态表
- **THEN** 表名 SHALL 使用 `am_archive_item_data_{category}` 语义
- **AND** 动态表 SHALL 使用条目 ID 关联 `am_archive_item`
- **AND** 动态表 SHALL NOT 保存全宗、分类、锁定、审计或状态等系统字段

#### Scenario: 案卷分类动态表

- **WHEN** 系统为分类创建案卷动态表
- **THEN** 表名 SHALL 使用 `am_archive_volume_data_{category}` 语义
- **AND** 动态表 SHALL 使用案卷 ID 关联 `am_archive_volume`

### Requirement: 条目明细子表

系统 SHALL 支持分类下的条目明细子表定义和动态明细数据表。

#### Scenario: 会计凭证明细不是档案分类

- **WHEN** 会计凭证分类需要保存凭证明细行
- **THEN** 系统 SHALL 将凭证明细建模为该分类下的 item 明细子表
- **AND** 系统 SHALL NOT 要求为凭证明细创建独立档案分类

#### Scenario: 明细动态表

- **WHEN** 客户端为分类创建明细表定义和字段定义
- **THEN** 系统 SHALL 创建对应动态明细数据表
- **AND** 明细行 SHALL 通过 `item_id` 关联 `am_archive_item`
- **AND** 明细表 SHALL 支持逻辑删除和行内排序
