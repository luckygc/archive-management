# archive-record-routing Specification

## Purpose
TBD - created by archiving change add-archive-metadata-routing. Update Purpose after archive.
## Requirements
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

### Requirement: 档案记录详情读取

系统 SHALL 提供单条档案记录详情读取能力，返回固定字段、分类、字段定义和动态字段值。

#### Scenario: 读取档案记录详情

- **WHEN** 客户端请求读取某条未删除档案记录详情
- **THEN** 系统 SHALL 返回统一档案记录主表固定字段
- **AND** 系统 SHALL 返回该记录所属档案分类
- **AND** 系统 SHALL 返回该分类启用字段定义
- **AND** 系统 SHALL 返回该记录在分类动态表中的字段值

#### Scenario: 读取不存在的档案记录

- **WHEN** 客户端请求读取不存在或已删除的档案记录
- **THEN** 系统 SHALL 拒绝请求
- **AND** 响应 SHALL 指出档案记录不存在

### Requirement: 档案记录编辑

系统 SHALL 支持编辑单条档案记录的固定字段、全宗编码和动态字段。

#### Scenario: 编辑未锁定档案记录

- **WHEN** 客户端提交未锁定档案记录的固定字段、全宗编码和动态字段
- **THEN** 系统 SHALL 校验全宗存在且启用
- **AND** 系统 SHALL 按字段定义校验并转换动态字段值
- **AND** 系统 SHALL 更新统一档案记录主表和分类动态表
- **AND** 系统 SHALL 写入全文检索投影 outbox
- **AND** 系统 SHALL 写入 `UPDATE` 档案记录操作审计
- **AND** 系统 SHALL 返回更新后的档案记录详情

#### Scenario: 编辑已锁定档案记录

- **WHEN** 客户端提交已锁定档案记录编辑请求
- **THEN** 系统 SHALL 拒绝更新
- **AND** 响应 SHALL 指出档案记录已锁定

#### Scenario: 编辑违反唯一规则的档案记录

- **WHEN** 客户端提交的全宗和动态字段值违反该分类的唯一规则
- **THEN** 系统 SHALL 拒绝更新
- **AND** 响应 SHALL 指出字段值违反唯一约束

### Requirement: 档案记录前端详情编辑工作区

系统 SHALL 在档案库前端提供独立详情页和编辑页，并按字段定义渲染控件，按独立布局配置渲染表格、详情和编辑工作区。

#### Scenario: 从档案库进入详情和编辑

- **WHEN** 用户在档案库列表查看记录
- **THEN** 前端 SHALL 提供进入详情页的入口
- **AND** 前端 SHALL 对未锁定记录提供进入编辑页的入口

#### Scenario: 按布局配置渲染动态字段

- **WHEN** 前端渲染档案记录详情页或编辑页
- **THEN** 前端 SHALL 按当前用户有效详情或编辑布局中的排序、显示状态和跨列配置渲染动态字段
- **AND** 前端 SHALL 按字段定义的编辑控件配置渲染 `INPUT`、`TEXTAREA`、`NUMBER`、`DATE` 或 `DATETIME` 控件

#### Scenario: 按表格布局渲染列表列

- **WHEN** 前端渲染档案库列表
- **THEN** 前端 SHALL 只展示当前用户有效表格布局中配置为显示的动态字段
- **AND** 前端 SHALL 按有效表格布局的顺序和列宽配置渲染动态字段列

### Requirement: 档案记录高级筛选

系统 SHALL 支持按字段类型提交高级筛选条件。

#### Scenario: 文本字段高级筛选

- **WHEN** 客户端对允许精确筛选的文本字段提交筛选值
- **THEN** 系统 SHALL 按该字段值等值筛选档案记录

#### Scenario: 整数和小数字段范围筛选

- **WHEN** 客户端对允许精确筛选的 `INTEGER` 或 `DECIMAL` 字段提交最小值或最大值
- **THEN** 系统 SHALL 按该字段原生数值类型执行大于等于或小于等于筛选
- **AND** 系统 SHALL NOT 将字段列统一转换为通用 `numeric` 再比较

#### Scenario: 日期和日期时间字段范围筛选

- **WHEN** 客户端对允许精确筛选的 `DATE` 或 `DATETIME` 字段提交开始值或结束值
- **THEN** 系统 SHALL 按该字段原生日期或日期时间类型执行大于等于或小于等于筛选

#### Scenario: 使用不可筛选字段

- **WHEN** 客户端提交不允许精确筛选且不属于唯一规则的字段条件
- **THEN** 系统 SHALL 拒绝请求
- **AND** 响应 SHALL 指出该字段不允许作为筛选条件

#### Scenario: 前端渲染高级筛选控件

- **WHEN** 前端渲染档案库高级筛选
- **THEN** 文本字段 SHALL 使用单值输入
- **AND** 整数、小数、日期和日期时间字段 SHALL 使用范围控件
- **AND** 唯一规则字段 SHALL 出现在高级筛选字段集合中
