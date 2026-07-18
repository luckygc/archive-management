# archive-record-routing Specification

## Purpose

定义档案条目与案卷分对象后的主表、动态数据和现有管理接口路由，确保条目使用 `archive-items`，案卷使用 `archive-volumes`。

## Requirements

### Requirement: 条目与案卷主表路由

系统 SHALL 使用 `am_archive_item` 保存档案条目，使用 `am_archive_volume` 保存案卷，并按对象类型定位对应分类动态表。

#### Scenario: 创建档案条目

- **WHEN** 客户端向 `POST /api/v1/archive-items` 提交档案分类、全宗和条目数据
- **THEN** 系统 SHALL 在 `am_archive_item` 保存条目的全宗、分类、档号、状态和案卷归属等固定字段
- **AND** 系统 SHALL 按条目对象类型和字段域找到对应分类动态表写入动态字段值
- **AND** 分类动态表 SHALL 使用条目 ID 关联 `am_archive_item`

#### Scenario: 创建案卷

- **WHEN** 客户端向 `POST /api/v1/archive-volumes` 提交已启用案卷管理的分类、全宗和案卷数据
- **THEN** 系统 SHALL 在 `am_archive_volume` 保存案卷固定字段
- **AND** 系统 SHALL NOT 将案卷作为 `am_archive_item` 中的一行保存

#### Scenario: 全宗不进入分类模板

- **WHEN** 客户端维护档案分类或字段定义
- **THEN** 系统 SHALL NOT 要求选择全宗
- **AND** 系统 SHALL NOT 将全宗编码保存到分类字段模板中

#### Scenario: 分类层级不进入条目和案卷主表

- **WHEN** 客户端创建档案条目或案卷
- **THEN** 对应主表 SHALL 只保存当前档案分类编码和名称
- **AND** 对应主表 SHALL NOT 保存分类父级、分类分组或分类路径字段

### Requirement: 分对象档案查询

系统 SHALL 分别提供档案条目和案卷查询，不通过统一档案记录资源混合返回两类对象。

#### Scenario: 查询单分类档案条目

- **WHEN** 客户端通过 `/api/v1/archive-items` 指定档案分类查询条目
- **THEN** 系统 SHALL 先从 `am_archive_item` 筛选未删除条目
- **AND** 系统 SHALL 支持按全宗编码筛选
- **AND** 系统 SHALL 按该分类的条目动态表补齐列表字段

#### Scenario: 查询未建表分类的条目

- **WHEN** 客户端查询尚未完成条目动态表建表的档案分类
- **THEN** 系统 SHALL 拒绝查询并返回 HTTP 412
- **AND** 响应 SHALL 说明档案分类动态表未创建

#### Scenario: 查询全部分类条目概览

- **WHEN** 客户端未指定档案分类查询档案条目概览
- **THEN** 系统 SHALL 只返回 `am_archive_item` 的通用字段
- **AND** 系统 SHALL NOT 跨多张分类动态表拼接不同分类字段

#### Scenario: 查询案卷

- **WHEN** 客户端通过 `GET /api/v1/archive-volumes` 查询案卷
- **THEN** 系统 SHALL 从 `am_archive_volume` 返回未删除案卷
- **AND** 系统 SHALL 支持按全宗编码和分类编码筛选
- **AND** 系统 SHALL 使用独立于档案条目的游标分页结果

### Requirement: 档案条目逻辑删除和操作审计

系统 SHALL 对档案条目及其分类动态数据执行逻辑删除，并记录现有删除操作审计。

#### Scenario: 删除档案条目

- **WHEN** 客户端向 `DELETE /api/v1/archive-items/{id}` 提交未锁定档案条目的删除请求
- **THEN** 系统 SHALL 将 `am_archive_item` 的 `deleted_flag` 标记为 `true` 并写入 `deleted_at` 和 `deleted_by`
- **AND** 系统 SHALL 将已建的条目元数据表和实物信息表对应行标记为已删除并写入删除时间和删除人
- **AND** 系统 SHALL 写入 `DELETE` 档案条目操作审计和客户端提交的删除原因
- **AND** 系统 SHALL 删除该条目的全文检索投影

### Requirement: 条目加入案卷一致性

系统 SHALL 保证加入案卷的档案条目与案卷属于同一全宗和同一档案分类。

#### Scenario: 将条目加入案卷

- **WHEN** 客户端向 `POST /api/v1/archive-volumes/{id}:addItem` 提交未锁定档案条目
- **THEN** 系统 SHALL 校验案卷和档案条目的全宗编码一致
- **AND** 系统 SHALL 校验案卷和档案条目的分类编码一致
- **AND** 校验通过后 SHALL 将条目的 `volume_id` 更新为目标案卷 ID
- **AND** 校验不通过时 SHALL 拒绝加入案卷

### Requirement: 档案条目业务锁

系统 SHALL 支持档案条目业务锁，并在现有受锁保护的写入口阻止修改已锁定条目。

#### Scenario: 锁定档案条目

- **WHEN** 客户端向 `POST /api/v1/archive-items/{id}:lock` 提交锁定请求
- **THEN** 系统 SHALL 将档案条目标记为已锁定
- **AND** 系统 SHALL 保存锁定原因、锁定人和锁定时间
- **AND** 系统 SHALL 写入 `LOCK` 档案条目操作审计

#### Scenario: 解锁档案条目

- **WHEN** 客户端向 `POST /api/v1/archive-items/{id}:unlock` 提交解锁请求
- **THEN** 系统 SHALL 清除业务锁状态、锁定原因、锁定人和锁定时间
- **AND** 系统 SHALL 写入 `UNLOCK` 档案条目操作审计

#### Scenario: 修改已锁定条目

- **WHEN** 客户端尝试编辑或删除已锁定档案条目、将其加入案卷，或写入其明细行
- **THEN** 系统 SHALL 拒绝该写操作
- **AND** 响应 SHALL 说明档案条目已锁定或当前不可修改

### Requirement: 档案条目详情读取

系统 SHALL 提供单条档案条目详情读取能力，返回固定字段、分类、字段定义和动态字段值。

#### Scenario: 读取档案条目详情

- **WHEN** 客户端请求 `GET /api/v1/archive-items/{id}` 读取未删除档案条目
- **THEN** 系统 SHALL 返回 `am_archive_item` 固定字段
- **AND** 系统 SHALL 返回该条目所属档案分类
- **AND** 系统 SHALL 按请求的布局表面返回有效字段定义
- **AND** 系统 SHALL 返回该条目元数据表和实物信息表中的动态字段值

#### Scenario: 读取不存在的档案条目

- **WHEN** 客户端请求读取不存在或已删除的档案条目
- **THEN** 系统 SHALL 拒绝请求
- **AND** 响应 SHALL 指出档案条目不存在

### Requirement: 档案条目编辑

系统 SHALL 支持编辑单条档案条目的现有固定字段、案卷归属、全宗和动态字段。

#### Scenario: 编辑未锁定档案条目

- **WHEN** 客户端向 `PATCH /api/v1/archive-items/{id}` 提交未锁定档案条目的编辑请求
- **THEN** 系统 SHALL 校验全宗存在且启用
- **AND** 系统 SHALL 按字段定义校验并转换元数据和实物信息字段值
- **AND** 系统 SHALL 更新 `am_archive_item` 及对应条目动态表
- **AND** 系统 SHALL 刷新全文检索投影
- **AND** 系统 SHALL 写入 `UPDATE` 档案条目操作审计
- **AND** 系统 SHALL 返回更新后的档案条目详情

#### Scenario: 编辑已锁定档案条目

- **WHEN** 客户端提交已锁定档案条目编辑请求
- **THEN** 系统 SHALL 拒绝更新
- **AND** 响应 SHALL 指出档案条目已锁定

#### Scenario: 编辑违反唯一规则的档案条目

- **WHEN** 客户端提交的档号或动态字段值违反现有唯一规则
- **THEN** 系统 SHALL 拒绝更新
- **AND** 响应 SHALL 指出档号已存在或动态字段违反唯一约束

### Requirement: 档案条目前端详情编辑工作区

系统 SHALL 在档案管理页面通过现有抽屉提供档案条目详情和编辑工作区，并按独立布局配置渲染表格、详情和编辑字段。

#### Scenario: 从档案管理列表打开详情和编辑抽屉

- **WHEN** 用户在档案管理列表操作某个条目
- **THEN** 前端 SHALL 提供打开详情抽屉的入口
- **AND** 前端 SHALL 对未锁定且有更新权限的条目提供编辑入口

#### Scenario: 按布局配置渲染动态字段

- **WHEN** 前端渲染档案条目详情或编辑抽屉
- **THEN** 前端 SHALL 按当前用户有效详情或编辑布局中的顺序、显示状态和跨列配置渲染动态字段
- **AND** 前端 SHALL 按字段定义的编辑控件配置渲染 `INPUT`、`TEXTAREA`、`NUMBER`、`DATE` 或 `DATETIME` 控件

#### Scenario: 按表格布局渲染列表列

- **WHEN** 前端渲染档案管理列表
- **THEN** 前端 SHALL 只展示当前用户有效表格布局中配置为显示的动态字段
- **AND** 前端 SHALL 按有效表格布局的顺序和列宽配置渲染动态字段列

### Requirement: 档案条目高级筛选

系统 SHALL 支持按字段类型提交档案条目高级筛选条件。

#### Scenario: 文本字段高级筛选

- **WHEN** 客户端对允许精确筛选的文本字段提交筛选值
- **THEN** 系统 SHALL 按该字段值等值筛选档案条目

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

- **WHEN** 前端渲染档案条目高级筛选
- **THEN** 文本字段 SHALL 使用单值输入
- **AND** 整数、小数、日期和日期时间字段 SHALL 使用范围控件
- **AND** 唯一规则字段 SHALL 出现在高级筛选字段集合中

### Requirement: 档案写入运行时触发

条目、案卷和电子文件状态变更 SHALL 在持久化前执行治理版本的用户定义运行时配置。

#### Scenario: 条目创建修改删除

- **WHEN** 条目创建、修改或删除通过基础权限和字段校验
- **THEN** 系统 SHALL 分别执行 `ITEM_BEFORE_CREATE`、`ITEM_BEFORE_UPDATE` 或 `ITEM_BEFORE_DELETE`
- **AND** `SET_FIELD` 最终候选值 SHALL 再次通过类型、数据范围和数据库约束
- **AND** 阻断 SHALL 使主表、动态表、审计和决策追踪不产生部分写入

#### Scenario: 案卷创建和条目入卷

- **WHEN** 创建案卷或把条目加入案卷
- **THEN** 系统 SHALL 在关系变化前执行 `VOLUME_BEFORE_CREATE` 或 `VOLUME_BEFORE_ADD_ITEM`
- **AND** 阻断 SHALL 保持案卷、条目归属和排序不变

#### Scenario: 电子文件上传

- **WHEN** 文件通过基础权限、大小和存储请求校验
- **THEN** 系统 SHALL 在文件元数据写入前执行 `FILE_BEFORE_UPLOAD`
- **AND** 阻断 SHALL 不写文件元数据，并按现有补偿合同清理或过期临时对象
