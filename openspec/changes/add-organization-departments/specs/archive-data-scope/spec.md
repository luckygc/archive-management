## MODIFIED Requirements

### Requirement: 固定维度范围条件

系统 SHALL NOT 将组织部门建模为所有档案记录共有的固定维度范围条件。

#### Scenario: 不提供档案所属部门固定范围

- **WHEN** 管理员配置档案数据范围固定维度
- **THEN** 系统 SHALL NOT 提供 `DEPARTMENT` 固定维度
- **AND** 系统 SHALL NOT 在档案主表使用 `department_id` 进行范围过滤

#### Scenario: 业务部门条件通过动态字段表达

- **WHEN** 某个档案门类需要按形成部门、承办部门或保管部门过滤
- **THEN** 系统 SHALL 通过该门类的档案元数据动态字段表达
- **AND** 动态字段是否可用于数据范围 SHALL 继续由字段配置控制

### Requirement: 授权主体绑定档案数据范围

系统 SHALL 支持用户所属部门绑定的数据范围参与用户有效数据范围计算。

#### Scenario: 保存部门主体数据范围绑定

- **WHEN** 管理员为部门绑定档案数据范围
- **THEN** 系统 SHALL 保存主体类型 `subject_type=DEPARTMENT`
- **AND** `subject_id` SHALL 使用部门 ID
- **AND** 系统 SHALL 校验部门存在且启用

#### Scenario: 停用部门不能新增主体绑定

- **WHEN** 管理员为停用部门新增档案数据范围绑定
- **THEN** 系统 SHALL 拒绝保存
- **AND** 系统 SHALL NOT 新增 `subject_type=DEPARTMENT` 的绑定行

#### Scenario: 用户所属部门范围参与计算

- **WHEN** 用户所属部门启用且该部门存在 `subject_type=DEPARTMENT` 的启用数据范围绑定
- **THEN** 系统 SHALL 将该部门范围与用户直接范围、角色范围按 OR 语义合并

#### Scenario: 停用所属部门范围不参与计算

- **WHEN** 用户所属部门已停用
- **THEN** 系统 SHALL NOT 将该部门主体绑定的数据范围计入用户有效数据范围
