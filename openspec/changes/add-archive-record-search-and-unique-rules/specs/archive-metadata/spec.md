## ADDED Requirements

### Requirement: 动态表固定删除标记和维护时间

系统 SHALL 在每张分类动态数据表中固定保存动态表删除标记和动态行维护时间。

#### Scenario: 首次创建分类动态表

- **WHEN** 客户端对有启用字段的档案分类执行建表动作
- **THEN** 系统 SHALL 创建该分类对应的动态数据表
- **AND** 动态表 SHALL 固定包含 `id`、`deleted_flag`、`created_at` 和 `updated_at`
- **AND** 动态表 SHALL 使用 `id` 作为主键并引用统一档案记录主表 ID

#### Scenario: 逻辑删除释放唯一值

- **WHEN** 客户端删除档案记录
- **THEN** 系统 SHALL 将统一档案记录主表 `deleted_flag` 标记为 `true`
- **AND** 系统 SHALL 将该记录对应分类动态表行的 `deleted_flag` 标记为 `true`
- **AND** 系统 SHALL 允许动态表部分唯一索引释放该记录占用的唯一值

### Requirement: 字段检索标记

系统 SHALL 在字段定义中只暴露精确筛选标记，全文检索不暴露字段级开关。

#### Scenario: 创建字段定义

- **WHEN** 客户端为档案分类新增字段
- **THEN** 系统 SHALL 保存该字段是否允许精确筛选
- **AND** 系统 SHALL NOT 保存该字段是否进入全文检索投影的字段级配置
- **AND** 系统 SHALL NOT 使用单一 `searchable` 字段同时表达精确筛选和全文检索

#### Scenario: 全文投影字段来源

- **WHEN** 系统维护全文检索投影
- **THEN** 系统 SHALL 固定读取该分类下所有启用动态字段
- **AND** 系统 SHALL NOT 要求客户端为字段配置全文检索开关

#### Scenario: 使用固定记录字段编码创建动态字段

- **WHEN** 客户端使用 `fonds_code`、`fonds_name`、`archive_no`、`archive_year`、`archive_status`、`process_status` 等统一档案记录主表字段编码创建分类字段
- **THEN** 系统 SHALL 拒绝保存
- **AND** 响应 SHALL 说明该字段编码属于档案记录固定字段，不能作为动态字段

### Requirement: 精确筛选索引维护

系统 SHALL 为允许精确筛选的字段维护动态表普通索引。

#### Scenario: 建表时创建精确筛选索引

- **WHEN** 客户端对分类执行建表动作且字段定义包含 `exact_searchable=true` 的字段
- **THEN** 系统 SHALL 为该字段对应动态列创建普通索引
- **AND** 索引 SHALL 只在动态表中对未删除记录生效

#### Scenario: 字段改为精确筛选

- **WHEN** 客户端将已建表字段改为允许精确筛选
- **THEN** 系统 SHALL 在下一次生成或更新表结构时补齐该字段普通索引
