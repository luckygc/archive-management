## ADDED Requirements

### Requirement: 档案业务库配置

系统 SHALL 使用可配置业务库表达档案当前业务归属，并使用稳定角色承载跨库查询语义。

#### Scenario: 创建业务库

- **WHEN** 有档案元数据管理权限的用户提交唯一编码、名称、业务库角色、启用状态和排序
- **THEN** 系统 SHALL 创建业务库
- **AND** 业务库角色 SHALL 为 `INTAKE` 或 `HOLDING`
- **AND** 系统 SHALL 允许同一角色存在多个业务库实例

#### Scenario: 禁用业务库

- **WHEN** 有档案元数据管理权限的用户禁用业务库
- **THEN** 系统 SHALL 保留该业务库及已有档案关联
- **AND** 后续档案创建或库变更 SHALL NOT 选择该业务库作为目标

#### Scenario: 删除仍被引用的业务库

- **WHEN** 用户删除仍被档案或业务历史引用的业务库
- **THEN** 系统 SHALL 拒绝删除
- **AND** 响应 SHALL 说明业务库仍在使用

### Requirement: 档案当前业务库

档案条目与案卷 SHALL 各自保存唯一当前业务库，并在库间移动时保持档案 ID 不变。

#### Scenario: 创建时指定业务库

- **WHEN** 客户端创建档案条目或案卷并指定已启用业务库
- **THEN** 系统 SHALL 将新档案保存到指定业务库
- **AND** 系统 SHALL 返回当前业务库 ID

#### Scenario: 创建时未指定业务库

- **WHEN** 兼容调用创建档案条目或案卷但未指定业务库
- **THEN** 系统 SHALL 将新档案保存到系统内置室藏库

#### Scenario: 变更条目业务库

- **WHEN** 有档案更新权限的用户向 `POST /api/v1/archive-items/{id}:changeRepository` 提交已启用目标业务库
- **THEN** 系统 SHALL 更新条目当前业务库且保持条目 ID 不变
- **AND** 系统 SHALL 记录来源库、目标库、业务类型、业务 ID、原因、操作人和操作时间

#### Scenario: 变更案卷业务库

- **WHEN** 有档案更新权限的用户向 `POST /api/v1/archive-volumes/{id}:changeRepository` 提交已启用目标业务库
- **THEN** 系统 SHALL 更新案卷当前业务库且保持案卷 ID 不变
- **AND** 系统 SHALL 记录库变更历史

#### Scenario: 变更到禁用业务库

- **WHEN** 客户端请求将档案变更到已禁用或不存在的业务库
- **THEN** 系统 SHALL 拒绝变更
- **AND** 档案原业务库 SHALL 保持不变

#### Scenario: 完成归档进入室藏库

- **WHEN** 预归档条目或案卷完成归档并变更到角色为 `HOLDING` 的业务库
- **THEN** 系统 SHALL 保持档案 ID 不变并记录库变更历史

#### Scenario: 正式档案退回预归档库

- **WHEN** 客户端请求将角色为 `HOLDING` 的条目或案卷变更到角色为 `INTAKE` 的业务库
- **THEN** 系统 SHALL 拒绝变更
- **AND** 正式档案原业务库 SHALL 保持不变

#### Scenario: 实物移交接收

- **WHEN** 业务部门向档案室提交、接收或退回实物移交批次
- **THEN** 系统 SHALL NOT 因实物移交改变档案当前业务库

### Requirement: 正式档案查询隔离

正式档案列表和搜索 SHALL 默认仅返回当前业务库角色为 `HOLDING` 的档案。

#### Scenario: 查询正式档案条目

- **WHEN** 客户端通过现有正式档案条目列表或搜索接口查询
- **THEN** 系统 SHALL 只返回当前业务库角色为 `HOLDING` 的未删除条目
- **AND** 当前处于预归档库的条目 SHALL NOT 出现在结果中

#### Scenario: 查询正式案卷

- **WHEN** 客户端通过现有案卷列表接口查询
- **THEN** 系统 SHALL 只返回当前业务库角色为 `HOLDING` 的未删除案卷
- **AND** 当前处于预归档库的案卷 SHALL NOT 出现在结果中

#### Scenario: 按 ID 读取预归档档案

- **WHEN** 有相应档案读取权限和数据范围的客户端按 ID 读取预归档库档案
- **THEN** 系统 SHALL 允许对应业务入口读取
- **AND** 正式列表的默认隔离 SHALL NOT 被解释为记录不存在
