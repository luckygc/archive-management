## ADDED Requirements

### Requirement: 档案记录主表路由

系统 SHALL 使用统一档案记录主表保存全宗、分类和状态，并使用主表 ID 定位分类动态数据。

#### Scenario: 创建档案记录

- **WHEN** 客户端基于某个档案分类和全宗创建档案记录
- **THEN** 系统 SHALL 在统一主表保存全宗编码、全宗名称、分类编码、分类名称、档号和状态
- **AND** 系统 SHALL 按分类元数据找到动态数据表写入字段值
- **AND** 系统 SHALL 使用统一主表 ID 作为动态数据表主键 ID
- **AND** 系统 SHALL NOT 在统一主表保存题名、动态表名或动态表行 ID

#### Scenario: 全宗不进入分类模板

- **WHEN** 客户端维护档案分类或字段定义
- **THEN** 系统 SHALL NOT 要求选择全宗
- **AND** 系统 SHALL NOT 将全宗编码保存到分类字段模板中

#### Scenario: 分类层级不进入记录主表

- **WHEN** 客户端创建档案记录
- **THEN** 系统 SHALL 只在统一主表保存当前档案分类编码和名称
- **AND** 系统 SHALL NOT 在统一主表保存门类、分类分组或分类路径字段

### Requirement: 动态档案库查询

系统 SHALL 支持基于分类元数据查询档案库动态列表。

#### Scenario: 查询单分类档案库

- **WHEN** 客户端指定档案分类查询档案库
- **THEN** 系统 SHALL 先从统一主表筛选未删除记录
- **AND** 系统 SHALL 支持按全宗编码筛选
- **AND** 系统 SHALL 按该分类动态表补齐列表显示字段

#### Scenario: 查询未建表分类

- **WHEN** 客户端查询尚未完成建表的档案分类
- **THEN** 系统 SHALL 返回空记录列表和字段定义
- **AND** 系统 SHALL 标记该分类尚未建表

#### Scenario: 查询全部分类概览

- **WHEN** 客户端未指定档案分类查询档案库
- **THEN** 系统 SHALL 只返回统一主表通用字段
- **AND** 系统 SHALL NOT 跨多张动态表拼接不同分类字段

### Requirement: 删除标记和删除审计

系统 SHALL 在业务表中统一使用删除标记表达删除状态，并在需要删除时间、删除人和快照时写入独立删除审计表。

#### Scenario: 删除档案记录

- **WHEN** 客户端删除档案记录
- **THEN** 系统 SHALL 将业务表 `deleted_flag` 标记为 `true`
- **AND** 系统 SHALL 将删除时间、删除人、删除原因和主表/动态表快照写入独立删除审计表
- **AND** 系统 SHALL NOT 在业务表中混用 `deleted_at` 作为删除状态

### Requirement: 案卷关联一致性

系统 SHALL 保证案卷和卷内档案记录属于同一全宗和同一档案分类。

#### Scenario: 关联卷内档案

- **WHEN** 客户端将档案记录加入案卷
- **THEN** 系统 SHALL 校验案卷和档案记录的全宗编码一致
- **AND** 系统 SHALL 校验案卷和档案记录的分类编码一致
- **AND** 校验不通过时 SHALL 拒绝创建关联

### Requirement: 档案记录业务锁

系统 SHALL 支持记录级业务锁，锁定后的档案记录不得被业务写入口修改。

#### Scenario: 锁定档案记录

- **WHEN** 客户端提交锁定档案记录请求
- **THEN** 系统 SHALL 将档案记录标记为已锁定
- **AND** 系统 SHALL 保存锁定原因、锁定人和锁定时间

#### Scenario: 解锁档案记录

- **WHEN** 客户端提交解锁档案记录请求
- **THEN** 系统 SHALL 清除业务锁状态
- **AND** 系统 SHALL 允许后续业务写入口继续修改该档案记录

#### Scenario: 修改已锁定记录

- **WHEN** 客户端尝试删除、加入案卷、变更附件关联或修改已锁定档案记录
- **THEN** 系统 SHALL 拒绝该写操作
- **AND** 响应 SHALL 说明档案记录已锁定
