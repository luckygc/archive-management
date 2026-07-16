## ADDED Requirements

### Requirement: 全宗管理

系统 SHALL 提供全宗管理能力，全宗作为档案记录归属维度，不作为档案分类字段模板的一部分。

#### Scenario: 创建全宗

- **WHEN** 客户端提交全宗编码和全宗名称
- **THEN** 系统 SHALL 创建一条全宗记录
- **AND** 全宗编码 SHALL 在未删除记录中唯一

#### Scenario: 查询启用全宗

- **WHEN** 客户端查询全宗列表
- **THEN** 系统 SHALL 返回未删除全宗
- **AND** 系统 SHALL 支持按启用状态筛选

#### Scenario: 禁用全宗

- **WHEN** 客户端禁用一个全宗
- **THEN** 系统 SHALL 保留历史档案记录上的全宗编码和名称
- **AND** 系统 SHALL NOT 删除该全宗关联的档案记录

### Requirement: 档案分类管理

系统 SHALL 提供多层级档案分类管理能力，档案分类作为字段模板和动态表路由源，不绑定具体全宗，不单独维护门类或分类分组。

#### Scenario: 创建档案分类

- **WHEN** 客户端提交分类编码和分类名称
- **THEN** 系统 SHALL 创建一条档案分类记录
- **AND** 分类编码 SHALL 在未删除记录中唯一
- **AND** 系统 SHALL NOT 要求选择全宗
- **AND** 系统 SHALL 允许选择一个未删除档案分类作为父级分类

#### Scenario: 查询档案分类

- **WHEN** 客户端查询档案分类列表
- **THEN** 系统 SHALL 返回分类编码、分类名称、父级分类 ID、动态表名、建表状态和更新时间

#### Scenario: 禁止分类循环

- **WHEN** 客户端创建或修改档案分类父级
- **THEN** 系统 SHALL 拒绝将分类自身设置为父级
- **AND** 系统 SHALL 拒绝将分类子孙设置为父级

#### Scenario: 删除存在子分类的分类

- **WHEN** 客户端删除仍存在未删除子分类的档案分类
- **THEN** 系统 SHALL 拒绝删除该分类

### Requirement: 档案字段定义

系统 SHALL 允许在档案分类下维护字段定义，用于驱动动态数据表和档案库列表。

#### Scenario: 创建字段定义

- **WHEN** 客户端为档案分类新增字段
- **THEN** 字段编码 SHALL 只允许小写字母、数字和下划线
- **AND** 字段类型 SHALL 只允许 `TEXT`、`INTEGER`、`DECIMAL`、`DATE`、`DATETIME`
- **AND** 同一分类下字段编码 SHALL 在未删除记录中唯一

#### Scenario: 配置字段展示

- **WHEN** 客户端修改字段名称、排序、是否列表显示或是否搜索
- **THEN** 系统 SHALL 更新字段定义运行时配置
- **AND** 系统 SHALL NOT 因展示配置变更修改物理列类型

#### Scenario: 枚举动态选项

- **WHEN** 某个字段需要枚举选项
- **THEN** 系统 SHALL 将枚举作为运行时动态选项处理
- **AND** 系统 SHALL NOT 将枚举作为独立物理列类型

### Requirement: 自动建表

系统 SHALL 按设置了字段定义的档案分类自动创建或增量维护动态数据表。

#### Scenario: 首次建表

- **WHEN** 客户端对有启用字段且未建表的档案分类执行建表动作
- **THEN** 系统 SHALL 创建该分类对应的动态数据表
- **AND** 动态表 SHALL 包含 `id`、字段定义对应列、`created_at`、`updated_at`
- **AND** 动态表主键 `id` SHALL 同时引用统一档案记录主表 `id`

#### Scenario: 增量加列

- **WHEN** 客户端对已建表分类执行建表动作且存在未建物理列的启用字段
- **THEN** 系统 SHALL 只追加缺失物理列
- **AND** 系统 SHALL NOT 删除或修改已存在物理列类型

#### Scenario: 无字段建表

- **WHEN** 客户端对没有启用字段的分类执行建表动作
- **THEN** 系统 SHALL 拒绝建表
- **AND** 响应 SHALL 说明该分类没有可建表字段
