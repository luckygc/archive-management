## ADDED Requirements

### Requirement: 档案数据范围定义

系统 SHALL 将档案数据范围作为独立资源管理，数据范围只表达档案集合，不表达读、写、删等功能权限。

#### Scenario: 创建任意范围

- **WHEN** 管理员创建 `*` 任意档案数据范围
- **THEN** 系统 SHALL 将范围类型保存为 `ALL`
- **AND** 系统 SHALL 拒绝同时提交全宗、分类、密级或动态字段条件
- **AND** 该范围在查询编译时 SHALL 表达为任意档案数据

#### Scenario: 创建条件范围

- **WHEN** 管理员创建条件数据范围
- **THEN** 系统 SHALL 至少保存一个全宗、分类、密级或动态字段条件
- **AND** 数据范围 SHALL NOT 保存 SQL 文本
- **AND** 数据范围 SHALL NOT 包含功能权限编码

#### Scenario: 无数据范围默认拒绝

- **WHEN** 用户没有通过启用角色绑定任何启用档案数据范围
- **THEN** 系统 SHALL 将该用户档案数据范围计算为空
- **AND** 系统 SHALL NOT 将空范围解释为 `*`

### Requirement: 固定维度范围条件

系统 SHALL 支持全宗、分类和密级等固定维度数据范围条件，并支持树形维度是否包含子级。

#### Scenario: 保存分类继承范围

- **WHEN** 管理员选择一个分类并启用包含子级
- **THEN** 系统 SHALL 保存分类 ID 和 `includeDescendants=true`
- **AND** 查询编译时 SHALL 将该范围应用到该分类及其启用子分类

#### Scenario: 保存全宗范围

- **WHEN** 管理员选择全宗范围
- **THEN** 系统 SHALL 校验全宗存在
- **AND** 系统 SHALL 以全宗稳定标识生成后端范围谓词

#### Scenario: 保存密级范围

- **WHEN** 管理员选择密级范围
- **THEN** 系统 SHALL 校验密级 ID 来自固定密级字典
- **AND** 查询编译时 SHALL 将密级 ID 条件作为档案主表固定字段谓词的一部分

### Requirement: 动态字段范围条件

系统 SHALL 支持分类内动态字段作为数据范围条件，并将动态字段条件保存到数据范围的 jsonb 条件部分。

#### Scenario: 保存动态字段条件

- **WHEN** 管理员为数据范围添加动态字段条件
- **THEN** 请求 SHALL 指定分类 ID、字段编码、操作符和值
- **AND** 系统 SHALL 校验字段属于该分类且允许用于数据范围
- **AND** 系统 SHALL 校验操作符和值类型匹配字段类型
- **AND** 系统 SHALL 将该动态字段条件保存为 jsonb 结构化数据
- **AND** 系统 SHALL NOT 为权限或数据范围表动态增加字段列

#### Scenario: 编译动态字段条件

- **WHEN** 系统计算档案查询数据范围
- **THEN** 系统 SHALL 解析动态字段 jsonb 条件
- **AND** 系统 SHALL 根据分类和字段定义定位对应动态表与列
- **AND** 系统 SHALL 生成受控 MyBatis 查询条件
- **AND** 系统 SHALL NOT 直接拼接来自 jsonb 的 SQL 文本

#### Scenario: 动态字段条件不跨分类继承

- **WHEN** 分类范围设置了包含子级且同时存在动态字段条件
- **THEN** 动态字段条件 SHALL 只作用于其声明的分类
- **AND** 系统 SHALL NOT 将动态字段条件自动套用到子分类，除非子分类显式配置同类条件

### Requirement: 授权主体绑定档案数据范围

系统 SHALL 支持用户和角色绑定一个或多个档案数据范围，并按用户直接绑定范围与启用角色范围计算数据范围并集。

#### Scenario: 用户直接范围与角色范围合并

- **WHEN** 用户直接绑定了数据范围，且用户拥有的启用角色也绑定了数据范围
- **THEN** 系统 SHALL 将用户直接范围和角色范围按 OR 语义合并
- **AND** 只要任一范围为 `ALL`，最终数据范围 SHALL 为任意档案数据

#### Scenario: 禁用范围不生效

- **WHEN** 授权主体绑定的数据范围已禁用
- **THEN** 系统 SHALL NOT 将该数据范围计入用户有效数据范围

#### Scenario: 预留组织主体类型

- **WHEN** 系统保存数据范围主体绑定
- **THEN** 绑定主体 SHALL 使用明确主体类型区分 `ROLE`、`USER` 和后续组织类主体
- **AND** 在组织架构能力未实现前，系统 SHALL NOT 将组织主体绑定计入用户有效数据范围

### Requirement: 档案接口应用数据范围

系统 SHALL 在档案读取、写入、文件、审计和导入导出接口统一应用数据范围。

#### Scenario: 查询档案列表

- **WHEN** 用户查询档案管理列表或全文发现
- **THEN** 系统 SHALL 使用 `deleted_flag=false AND 用户数据范围 AND 用户查询条件` 查询档案
- **AND** 系统 SHALL NOT 先查出全量档案再在 Java 或前端过滤

#### Scenario: 读取档案详情

- **WHEN** 用户读取不在其数据范围内的档案详情
- **THEN** 系统 SHALL 拒绝读取
- **AND** 响应 SHALL 使用项目统一 ProblemDetail 错误模型

#### Scenario: 写入越权档案

- **WHEN** 用户创建、更新、删除、锁定或解锁不在其数据范围内的档案
- **THEN** 系统 SHALL 拒绝操作
- **AND** 系统 SHALL NOT 写入业务数据

#### Scenario: 档案电子文件和审计范围控制

- **WHEN** 用户上传、新增、查看列表、预览、下载或删除档案电子文件，或查询档案操作审计
- **THEN** 系统 SHALL 校验目标档案或审计记录命中用户数据范围
- **AND** 系统 SHALL NOT 因文件 ID 或审计 ID 可猜测而绕过档案数据范围
- **AND** 系统 SHALL 使用独立功能权限区分档案电子文件列表、预览和下载
