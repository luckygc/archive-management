# archive-data-scope Specification

## Purpose

定义档案数据范围、固定维度和动态字段条件、授权主体绑定及档案业务接口的范围控制合同，确保功能权限与数据范围分离并由服务端统一执行。

## Requirements

### Requirement: 档案数据范围定义

系统 SHALL 将档案数据范围作为独立资源管理，数据范围只表达档案集合，不表达读、写、删等功能权限。

#### Scenario: 创建任意范围

- **WHEN** 管理员创建 `*` 任意档案数据范围
- **THEN** 系统 SHALL 将范围类型保存为 `ALL`
- **AND** 系统 SHALL 拒绝同时提交全宗、分类、密级、保管期限或动态字段条件
- **AND** 该范围在查询编译时 SHALL 表达为任意档案数据

#### Scenario: 创建条件范围

- **WHEN** 管理员创建条件数据范围
- **THEN** 系统 SHALL 至少保存一个全宗、分类、密级、保管期限或动态字段条件
- **AND** 数据范围 SHALL NOT 保存 SQL 文本
- **AND** 数据范围 SHALL NOT 包含功能权限编码

#### Scenario: 无数据范围默认拒绝

- **WHEN** 非超级管理员用户没有通过用户、启用角色或启用所属部门绑定任何启用档案数据范围
- **THEN** 系统 SHALL 将该用户档案数据范围计算为空
- **AND** 系统 SHALL NOT 将空范围解释为 `*`

#### Scenario: 超级管理员无绑定也拥有任意范围

- **WHEN** 用户拥有启用的内置 `超级管理员` 角色且没有绑定任何档案数据范围
- **THEN** 系统 SHALL 将该用户档案数据范围解析为 `ALL`
- **AND** 系统 SHALL NOT 因缺少用户、角色或部门范围绑定而拒绝超级管理员

### Requirement: 固定维度范围条件

系统 SHALL 支持全宗、分类、密级和保管期限固定维度数据范围条件，并支持树形分类是否包含子级；系统 SHALL NOT 将组织部门建模为所有档案记录共有的固定维度。

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
- **THEN** 系统 SHALL 校验密级 ID 非空
- **AND** 查询编译时 SHALL 将密级 ID 条件作为档案主表固定字段谓词的一部分

#### Scenario: 保存保管期限范围

- **WHEN** 管理员选择保管期限范围
- **THEN** 系统 SHALL 校验保管期限 ID 非空
- **AND** 查询编译时 SHALL 将保管期限 ID 条件作为档案主表固定字段谓词的一部分

#### Scenario: 不提供档案所属部门固定范围

- **WHEN** 管理员配置档案数据范围固定维度
- **THEN** 系统 SHALL NOT 提供组织部门固定维度
- **AND** 系统 SHALL NOT 在档案主表增加组织部门范围过滤合同

#### Scenario: 业务部门条件通过动态字段表达

- **WHEN** 某个档案门类需要按形成部门、承办部门或保管部门过滤
- **THEN** 系统 SHALL 通过该门类的档案元数据动态字段表达
- **AND** 动态字段是否可用于数据范围 SHALL 继续由字段配置控制

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

系统 SHALL 支持 `ROLE`、`USER` 和 `DEPARTMENT` 主体绑定一个或多个档案数据范围，并按用户直接范围、启用角色范围和启用所属部门范围计算并集。

#### Scenario: 用户直接范围、角色范围与部门范围合并

- **WHEN** 用户直接绑定了数据范围，且用户的启用角色或启用所属部门也绑定了数据范围
- **THEN** 系统 SHALL 将用户直接范围、角色范围和部门范围按 OR 语义合并
- **AND** 只要任一范围为 `ALL`，最终数据范围 SHALL 为任意档案数据

#### Scenario: 用户仅通过所属部门获得数据范围

- **WHEN** 用户没有直接范围和启用角色范围，但其启用所属部门绑定了启用档案数据范围
- **THEN** 系统 SHALL 将该部门范围计入用户有效数据范围
- **AND** 系统 SHALL NOT 因用户和角色没有范围绑定而将最终范围计算为空

#### Scenario: 禁用范围不生效

- **WHEN** 授权主体绑定的数据范围已禁用
- **THEN** 系统 SHALL NOT 将该数据范围计入用户有效数据范围

#### Scenario: 保存部门主体数据范围绑定

- **WHEN** 管理员为部门绑定档案数据范围
- **THEN** 系统 SHALL 保存主体类型 `subject_type=DEPARTMENT`
- **AND** `subject_id` SHALL 使用部门 ID
- **AND** 系统 SHALL 校验部门存在且启用

#### Scenario: 停用部门不能新增主体绑定

- **WHEN** 管理员为停用部门新增档案数据范围绑定
- **THEN** 系统 SHALL 拒绝保存
- **AND** 系统 SHALL NOT 新增 `subject_type=DEPARTMENT` 的绑定行

#### Scenario: 停用所属部门范围不参与计算

- **WHEN** 用户所属部门已停用
- **THEN** 系统 SHALL NOT 将该部门主体绑定的数据范围计入用户有效数据范围

### Requirement: 档案接口应用数据范围

系统 SHALL 在档案读取、写入、文件和导入导出接口统一应用数据范围，并单独执行当前档案审计查询授权边界。

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

#### Scenario: 创建或更新档案时校验目标范围

- **WHEN** 用户创建或更新档案
- **THEN** 系统 SHALL 使用提交后的全宗、分类、密级、保管期限和动态字段判断档案是否命中用户数据范围
- **AND** 未命中用户数据范围时系统 SHALL 拒绝写入

#### Scenario: 档案电子文件范围控制

- **WHEN** 用户上传、新增、查看列表、下载或删除档案电子文件
- **THEN** 系统 SHALL 校验目标档案命中用户数据范围
- **AND** 系统 SHALL NOT 因文件 ID 可猜测而绕过档案数据范围
- **AND** 系统 SHALL 使用独立功能权限区分档案电子文件列表可见和下载

#### Scenario: 档案审计查询当前授权边界

- **WHEN** 用户查询档案操作审计
- **THEN** 系统 SHALL 仅允许拥有启用内置 `超级管理员` 角色的用户查询
- **AND** 系统 SHALL 拒绝非超级管理员并且 SHALL NOT 查询审计数据

#### Scenario: 档案导入导出范围控制

- **WHEN** 用户导入档案或按查询条件导出档案
- **THEN** 系统 SHALL 对导入目标和导出结果应用当前用户档案数据范围
- **AND** 系统 SHALL NOT 因批量处理而绕过单条档案数据范围
