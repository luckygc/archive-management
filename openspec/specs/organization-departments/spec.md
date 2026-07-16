# organization-departments Specification

## Purpose

定义组织部门的管理、层级、启停、排序和访问权限合同，为用户归属与档案数据范围提供统一、可校验且保留历史引用的组织主体。

## Requirements

### Requirement: 组织架构部门管理

系统 SHALL 使用部门作为组织架构节点，并通过 `/api/v1/organization-departments` 提供部门列表、详情、创建、更新、启停和排序能力。

#### Scenario: 查询部门列表

- **WHEN** 客户端请求 `GET /api/v1/organization-departments`
- **THEN** 系统 SHALL 返回所有部门，包含启用和停用部门
- **AND** 请求 MAY 使用 `enabled` 参数筛选启用或停用部门
- **AND** 响应 SHALL 使用 `CollectionResponse`
- **AND** 每个部门 SHALL 包含 `id`、`departmentCode`、`departmentName`、`parentId`、`enabled` 和 `sortOrder`
- **AND** 部门 SHALL 按 `sortOrder ASC`、`id ASC` 稳定排序

#### Scenario: 查询部门详情

- **WHEN** 客户端请求 `GET /api/v1/organization-departments/{organizationDepartment}`
- **THEN** 系统 SHALL 返回指定部门详情
- **AND** 响应 SHALL 包含 `id`、`departmentCode`、`departmentName`、`parentId`、`enabled` 和 `sortOrder`

#### Scenario: 创建部门

- **WHEN** 管理员请求 `POST /api/v1/organization-departments` 并提交部门编码、部门名称、父部门、启用状态和排序
- **THEN** 系统 SHALL 创建 `OrganizationDepartment`
- **AND** 请求字段 SHALL 使用 `departmentCode`、`departmentName`、`parentId`、`enabled` 和 `sortOrder`
- **AND** 部门编码 SHALL 唯一
- **AND** 父部门为空时 SHALL 创建根部门
- **AND** 父部门不为空时 SHALL 校验父部门存在

#### Scenario: 更新部门字段

- **WHEN** 管理员请求 `PATCH /api/v1/organization-departments/{organizationDepartment}`
- **THEN** 系统 SHALL 支持更新 `departmentCode`、`departmentName`、`parentId`、`enabled` 和 `sortOrder`
- **AND** 更新部门编码时 SHALL 校验部门编码唯一
- **AND** 更新父部门时 SHALL 拒绝将父级设置为自己
- **AND** 系统 SHALL 拒绝将父级设置为自己的后代

#### Scenario: 启用或停用部门

- **WHEN** 管理员通过 `PATCH /api/v1/organization-departments/{organizationDepartment}` 更新 `enabled`
- **THEN** 系统 SHALL 保存部门启用状态
- **AND** 启用部门后系统 SHALL 允许该部门被新用户归属和新数据范围主体绑定选择
- **AND** 停用部门后系统 SHALL 保留历史引用
- **AND** 停用部门 SHALL NOT 被新用户归属或新数据范围主体绑定选择

#### Scenario: 调整部门排序

- **WHEN** 管理员通过 `PATCH /api/v1/organization-departments/{organizationDepartment}` 更新 `sortOrder`
- **THEN** 系统 SHALL 保存排序值
- **AND** 后续列表 SHALL 按新的 `sortOrder ASC`、`id ASC` 返回

#### Scenario: 不提供删除部门接口

- **WHEN** 客户端尝试删除部门资源
- **THEN** 系统 SHALL NOT 提供 `DELETE /api/v1/organization-departments/{organizationDepartment}` 项目自有接口
- **AND** 系统 SHALL 通过停用部门保留历史引用

#### Scenario: 停用部门保留历史引用

- **WHEN** 管理员停用部门后系统读取已有用户或数据范围主体关系
- **THEN** 系统 SHALL 保留历史引用
- **AND** 系统 SHALL 允许响应继续展示该停用部门的编码和名称

#### Scenario: 查询启用部门

- **WHEN** 客户端请求 `GET /api/v1/organization-departments?enabled=true`
- **THEN** 系统 SHALL 只返回 `enabled=true` 的部门
- **AND** 响应 SHALL 使用 `CollectionResponse`

### Requirement: 组织架构权限

系统 SHALL 使用独立功能权限控制组织架构管理。

#### Scenario: 读取部门选项

- **WHEN** 用户调用 `GET /api/v1/organization-departments`
- **OR** 用户调用 `GET /api/v1/organization-departments?enabled=true`
- **OR** 用户调用 `GET /api/v1/organization-departments/{organizationDepartment}`
- **THEN** 系统 SHALL 要求用户至少具备 `organization:department:manage`、`authentication:user:manage` 或 `archive:data-scope:manage` 之一
- **AND** 超级管理员 SHALL 默认拥有读取能力

#### Scenario: 管理组织架构部门接口

- **WHEN** 用户调用 `POST /api/v1/organization-departments`
- **OR** 用户调用 `PATCH /api/v1/organization-departments/{organizationDepartment}`
- **THEN** 系统 SHALL 要求 `organization:department:manage`
- **AND** 超级管理员 SHALL 默认拥有该权限
