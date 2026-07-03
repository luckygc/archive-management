## ADDED Requirements

### Requirement: 组织架构部门管理

系统 SHALL 使用部门作为组织架构节点，并提供部门创建、查询、更新、启停和排序能力。

#### Scenario: 创建部门

- **WHEN** 管理员提交部门编码、部门名称、父部门和排序
- **THEN** 系统 SHALL 创建 `OrganizationDepartment`
- **AND** 部门编码 SHALL 唯一
- **AND** 父部门为空时 SHALL 创建根部门
- **AND** 父部门不为空时 SHALL 校验父部门存在

#### Scenario: 更新部门父级

- **WHEN** 管理员更新部门父级
- **THEN** 系统 SHALL 拒绝将父级设置为自己
- **AND** 系统 SHALL 拒绝将父级设置为自己的后代

#### Scenario: 停用部门

- **WHEN** 管理员停用部门
- **THEN** 系统 SHALL 保留历史引用
- **AND** 系统 SHALL NOT 允许该部门被新用户归属、新档案记录或新数据范围条件选择

#### Scenario: 查询启用部门

- **WHEN** 客户端请求启用部门列表
- **THEN** 系统 SHALL 只返回 `enabled=true` 的部门
- **AND** 响应 SHALL 使用 `CollectionResponse`

### Requirement: 组织架构权限

系统 SHALL 使用独立功能权限控制组织架构管理。

#### Scenario: 管理组织架构

- **WHEN** 用户调用组织架构管理 API
- **THEN** 系统 SHALL 要求 `organization:department:manage`
- **AND** 超级管理员 SHALL 默认拥有该权限
