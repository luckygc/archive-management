## ADDED Requirements

### Requirement: 档案主表业务库路由

系统 SHALL 在档案条目和案卷主表保存当前业务库，并由业务库决定正式查询路由。

#### Scenario: 保存条目当前业务库

- **WHEN** 系统创建或变更档案条目业务库
- **THEN** `am_archive_item.repository_id` SHALL 引用有效档案业务库
- **AND** 分类动态表 SHALL NOT 复制当前业务库字段

#### Scenario: 保存案卷当前业务库

- **WHEN** 系统创建或变更案卷业务库
- **THEN** `am_archive_volume.repository_id` SHALL 引用有效档案业务库
- **AND** 案卷与卷内条目 SHALL 各自保存当前业务库

#### Scenario: 正式查询应用业务库路由

- **WHEN** 系统执行现有正式条目或案卷查询
- **THEN** 查询 SHALL 关联档案业务库并只保留角色为 `HOLDING` 的记录
- **AND** 报表或业务查询 SHALL 按其业务规格显式选择业务库范围
