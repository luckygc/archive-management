## ADDED Requirements

### Requirement: 字段编辑控件配置
系统 SHALL 在档案字段定义中保存编辑控件配置，并校验控件与字段类型兼容。

#### Scenario: 创建文本字段并配置多行文本
- **WHEN** 客户端为档案分类创建 `TEXT` 类型字段且编辑控件为 `TEXTAREA`
- **THEN** 系统 SHALL 保存该字段的编辑控件配置
- **AND** 后续字段查询 SHALL 返回该编辑控件配置

#### Scenario: 创建数字字段并配置非数字控件
- **WHEN** 客户端为 `INTEGER` 或 `DECIMAL` 类型字段配置 `INPUT`、`TEXTAREA`、`DATE` 或 `DATETIME` 控件
- **THEN** 系统 SHALL 拒绝保存
- **AND** 响应 SHALL 指出该字段类型只能使用数字控件

#### Scenario: 创建日期字段并配置错误控件
- **WHEN** 客户端为 `DATE` 类型字段配置非 `DATE` 控件，或为 `DATETIME` 类型字段配置非 `DATETIME` 控件
- **THEN** 系统 SHALL 拒绝保存
- **AND** 响应 SHALL 指出该字段类型可用的编辑控件

### Requirement: 独立布局配置
系统 SHALL 为档案分类提供独立布局配置，表格、详情和编辑布局 SHALL 分别维护字段顺序、显示状态和布局属性，并支持公共布局与个人布局。

#### Scenario: 保存表格布局配置
- **WHEN** 客户端在表格布局中拖拽字段顺序并配置字段是否显示或列宽
- **THEN** 系统 SHALL 保存表格布局顺序、显示状态和列宽
- **AND** 系统 SHALL NOT 要求同时修改字段定义语义

#### Scenario: 保存详情和编辑布局配置
- **WHEN** 客户端在详情或编辑布局中拖拽字段顺序并配置字段是否显示或跨列
- **THEN** 系统 SHALL 保存对应布局的顺序、显示状态和跨列数
- **AND** 系统 SHALL NOT 因布局配置变更修改动态表物理列类型

#### Scenario: 个人布局优先
- **WHEN** 当前用户存在某个分类和场景的个人布局
- **THEN** 系统 SHALL 返回该个人布局作为有效布局
- **AND** 系统 SHALL NOT 使用公共布局覆盖个人布局

#### Scenario: 公共布局兜底
- **WHEN** 当前用户不存在某个分类和场景的个人布局
- **THEN** 系统 SHALL 返回该分类和场景的公共布局作为有效布局
- **AND** 如果公共布局也不存在，系统 SHALL 使用字段定义中的默认展示配置生成有效布局

#### Scenario: 配置非法跨列值
- **WHEN** 客户端提交的详情跨列或编辑跨列不在系统允许范围内
- **THEN** 系统 SHALL 拒绝保存
- **AND** 响应 SHALL 指出跨列值不合法

#### Scenario: 配置非法列表列宽
- **WHEN** 客户端提交的列表列宽超出系统允许范围
- **THEN** 系统 SHALL 拒绝保存
- **AND** 响应 SHALL 指出列表列宽不合法

### Requirement: 唯一规则字段可筛选
系统 SHALL 将唯一规则中的字段作为档案记录精确筛选字段维护。

#### Scenario: 创建唯一规则
- **WHEN** 客户端为档案分类创建唯一规则并选择字段
- **THEN** 系统 SHALL 将这些字段标记为允许精确筛选
- **AND** 系统 SHALL 为已建表分类维护这些字段的精确筛选索引

#### Scenario: 修改唯一规则字段
- **WHEN** 客户端修改唯一规则选择的字段
- **THEN** 系统 SHALL 将新选择字段标记为允许精确筛选
- **AND** 系统 SHALL 为已建表分类维护这些字段的精确筛选索引
