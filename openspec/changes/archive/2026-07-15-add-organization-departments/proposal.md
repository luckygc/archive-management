## Why

当前组织模型命名为 `organization_unit`，该名称对系统管理过于抽象，并泄漏到 API、前端类型和档案数据范围合同中。系统也缺少组织架构管理界面和用户所属部门，因此无法基于用户所属部门计算部门范围内的档案数据权限。

## What Changes

- 使用 `OrganizationDepartment` 替换 `OrganizationUnit`。
- 使用 `am_organization_department` 替换 `am_organization_unit`。
- 使用 `DEPARTMENT` 替换档案数据范围授权主体中的 `ORG_UNIT`，但不把部门建模为档案记录固定维度。
- 新增组织架构部门列表、详情、创建、更新、启停和排序 API 以及系统菜单页面。
- 为认证用户新增 `departmentId`。
- 运行期档案数据范围解析纳入用户所属启用部门绑定的数据范围。

## Capability Deltas

- 新增 `organization-departments` 能力：组织架构部门管理、部门权限和部门启停引用规则。
- 扩展既有档案数据范围能力：将组织类主体预留收敛为部门口径；档案部门类业务条件继续通过动态字段表达。
- 扩展 `login-authentication` 能力：用户管理增加所属部门字段、校验和展示要求。

## Impact

- 后端模块：`organization`、`authentication`、`archive.authorization`、`authorization`。
- 前端页面：组织架构部门、认证用户、档案数据范围、档案条目管理。
- 数据库迁移直接编辑为目标结构，因为项目尚未发布，且明确不保留旧 `organization_unit` 兼容分支。
