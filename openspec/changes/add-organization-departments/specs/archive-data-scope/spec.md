## MODIFIED Requirements

### Requirement: 固定维度范围条件

系统 SHALL 在既有档案数据范围能力中使用部门作为档案所属组织维度，并使用 `DEPARTMENT` 替代 `ORG_UNIT`。

#### Scenario: 保存档案所属部门范围

- **WHEN** 管理员选择档案所属部门范围
- **THEN** 系统 SHALL 保存维度类型 `DEPARTMENT`
- **AND** 系统 SHALL 校验部门存在且启用
- **AND** 查询编译时 SHALL 将部门 ID 条件应用到档案主表 `department_id`

#### Scenario: 停用部门不能作为新范围条件

- **WHEN** 管理员保存档案所属部门范围并提交停用部门
- **THEN** 系统 SHALL 拒绝保存
- **AND** 系统 SHALL NOT 将停用部门写入新的 `DEPARTMENT` 维度条件

### Requirement: 授权主体绑定档案数据范围

系统 SHALL 支持用户所属部门绑定的数据范围参与用户有效数据范围计算。

#### Scenario: 保存部门主体数据范围绑定

- **WHEN** 管理员为部门绑定档案数据范围
- **THEN** 系统 SHALL 保存主体类型 `subject_type=DEPARTMENT`
- **AND** `subject_id` SHALL 使用部门 ID
- **AND** 系统 SHALL 校验部门存在且启用
- **AND** 系统 SHALL NOT 将部门主体绑定与档案所属部门维度条件混用

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
