## ADDED Requirements

### Requirement: 固定维度范围条件

系统 SHALL 使用部门作为档案所属组织维度，并使用 `DEPARTMENT` 替代 `ORG_UNIT`。

#### Scenario: 保存档案所属部门范围

- **WHEN** 管理员选择档案所属部门范围
- **THEN** 系统 SHALL 保存维度类型 `DEPARTMENT`
- **AND** 系统 SHALL 校验部门存在且启用
- **AND** 查询编译时 SHALL 将部门 ID 条件应用到档案主表 `department_id`

### Requirement: 授权主体绑定档案数据范围

系统 SHALL 支持用户所属部门绑定的数据范围参与用户有效数据范围计算。

#### Scenario: 用户所属部门范围参与计算

- **WHEN** 用户所属部门启用且部门绑定了启用数据范围
- **THEN** 系统 SHALL 将该部门范围与用户直接范围、角色范围按 OR 语义合并

#### Scenario: 停用所属部门范围不参与计算

- **WHEN** 用户所属部门已停用
- **THEN** 系统 SHALL NOT 将该部门主体绑定的数据范围计入用户有效数据范围
