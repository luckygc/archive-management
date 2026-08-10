## ADDED Requirements

### Requirement: 管理端统一数据表格

管理端业务数据表格 SHALL 使用项目统一的数据表格呈现列、行、加载和空状态，并 SHALL NOT 由业务页面直接声明 Element Plus 表格组件。

#### Scenario: 展示完整数据集合

- **WHEN** 页面向统一数据表格传入列定义和完整数据集合
- **THEN** 表格 SHALL 按列定义呈现表头和单元格
- **AND** 复杂单元格 SHALL 保留业务页面提供的操作、状态和格式化内容

#### Scenario: 数据正在加载

- **WHEN** 页面标记表格正在加载
- **THEN** 表格 SHALL 提供可感知的加载反馈
- **AND** 已有行 SHALL NOT 因加载反馈而被错误解释为空状态

#### Scenario: 数据为空

- **WHEN** 加载完成且可见行为空
- **THEN** 表格 SHALL 呈现页面配置的空状态文案
- **AND** 空状态 SHALL 跨越全部可见列

### Requirement: 本地多列排序

当页面提供的是完整内存集合时，统一数据表格 SHALL 支持稳定的本地多列排序，并保持原始数据不变。

#### Scenario: 设置主排序列

- **WHEN** 用户点击一个可排序列的表头
- **THEN** 表格 SHALL 在未排序、升序、降序和移除排序之间切换
- **AND** 未使用多选组合键时，该列 SHALL 替换其他活动排序列

#### Scenario: 追加多列排序

- **WHEN** 用户按住 Shift、Ctrl 或 Command 点击另一个可排序列
- **THEN** 表格 SHALL 将该列追加到现有排序列表或切换其方向
- **AND** 每个活动排序列 SHALL 显示方向和从 1 开始的排序优先级

#### Scenario: 不可排序列

- **WHEN** 列定义未声明排序能力
- **THEN** 表头 SHALL NOT 呈现排序按钮、方向或优先级
- **AND** 用户交互 SHALL NOT 改变行顺序

### Requirement: 分页排序一致性

统一数据表格 SHALL 区分完整集合排序与服务端分页排序，不得把当前页重排呈现为全量排序。

#### Scenario: 服务端支持排序合同

- **WHEN** 分页页面将表格配置为远程排序并且用户修改排序
- **THEN** 表格 SHALL 按优先级发送完整排序状态
- **AND** 表格 SHALL NOT 在客户端重排当前页

#### Scenario: 服务端不支持排序合同

- **WHEN** 页面展示服务端分页结果但对应接口未声明排序参数
- **THEN** 表格 SHALL 禁用该列表的列排序交互
- **AND** 表格 SHALL 保持服务端返回顺序

### Requirement: 表格可访问性与响应式布局

统一数据表格 SHALL 使用语义化表格结构，并在窄视口或宽列集合下保持内容可访问。

#### Scenario: 键盘操作排序

- **WHEN** 键盘用户聚焦可排序表头并激活排序按钮
- **THEN** 表格 SHALL 执行与指针点击相同的排序切换
- **AND** 控件的可访问名称 SHALL 说明当前方向和下一步排序动作

#### Scenario: 表格宽度超过容器

- **WHEN** 可见列总宽度超过可用空间
- **THEN** 表格容器 SHALL 提供横向滚动
- **AND** 单元格内容 SHALL NOT 强制页面整体产生横向溢出

#### Scenario: 展示树形行

- **WHEN** 页面传入包含子行的层级数据
- **THEN** 表格 SHALL 在首个数据列显示层级缩进和展开控制
- **AND** 展开控制 SHALL 提供展开或收起的可访问名称
