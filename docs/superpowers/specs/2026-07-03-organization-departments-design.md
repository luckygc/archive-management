# 组织架构部门闭环设计

## 背景

当前系统已经存在 `am_organization_unit`、`OrganizationUnit` 和 `/api/v1/organization-units` 只读接口。但该模型命名偏抽象，`unit` 不符合系统管理里的真实业务语言；同时前端没有组织架构管理页，用户没有所属部门，数据范围中预留的组织主体也没有参与用户权限计算。

本设计按用户确认的方案直接切换到部门模型，不保留旧 `organization_unit` 兼容层。

## 目标

- 将组织架构节点统一命名为部门，代码、表、API 和前端类型使用 `department`。
- 提供组织架构管理页，支持部门树维护。
- 用户管理支持所属部门。
- 档案数据范围支持部门作为授权主体，并让用户所属部门范围参与运行时数据范围计算。
- 部门不作为档案条目或案卷的固定归属字段；门类需要部门类业务字段时，通过档案元数据动态字段表达。
- 清理旧 `OrganizationUnit` / `ORG_UNIT` 语义，避免新旧概念并存。

## 不做范围

- 不处理存储配置、系统参数、工作台、归档接收等其他占位页。
- 不增加岗位、职级、部门负责人、部门类型、行政区划、用户多部门。
- 不实现部门范围继承；绑定上级部门不会自动包含下级部门用户。
- 不提供部门删除接口；停用作为退出可选范围的方式。
- 不保留旧表、旧 API、旧前端类型或旧枚举兼容分支。

## 数据模型

组织架构固定表改为 `am_organization_department`：

- `id`
- `department_code`
- `department_name`
- `parent_id`
- `enabled`
- `sort_order`
- `version`
- `created_at`
- `updated_at`

关联调整：

- `am_authentication_user` 新增 `department_id`，允许为空。
- 档案数据范围主体枚举从 `ORG_UNIT` 改为 `DEPARTMENT`，`subject_id` 指向部门 ID。
- 档案数据范围固定维度不新增 `DEPARTMENT`，也不在档案主表增加 `department_id`。

迁移处理：

- 因项目未正式发布，Flyway 迁移直接维护目标结构。
- 现有 `V20260703_0100__create_organization_unit.sql` 调整为创建 `am_organization_department`。
- 不写旧结构迁移兼容分支。

## 后端架构

新增 `module/organization`：

- `OrganizationDepartment`
- `OrganizationDepartmentDataRepository`
- `OrganizationDepartmentService`
- `OrganizationDepartmentController`
- Web 请求和响应类型放在组织模块 `web` 边界下。

模块依赖：

- `authentication` 通过 `OrganizationDepartmentService` 校验用户所属部门，不直接访问组织 Repository。
- `archive.authorization` 后续在实现部门主体绑定时通过 `OrganizationDepartmentService` 校验部门主体。
- `archive.item` 不依赖组织架构模块。
- `archive.metadata` 不再承载组织架构概念。

持久化入口：

- 部门是固定项目表，使用 Jakarta Data Repository。
- 写入使用显式 `insert` / `update`，不使用 `save` 或 upsert。
- 部门包含 `@Version`，更新时保留乐观锁字段。

## API 设计

项目自有 API 使用标准资源路径：

- `GET /api/v1/organization-departments?enabled=true|false`
- `GET /api/v1/organization-departments/{department}`
- `POST /api/v1/organization-departments`
- `PATCH /api/v1/organization-departments/{department}`

请求和响应命名：

- `CreateOrganizationDepartmentRequest`
- `UpdateOrganizationDepartmentRequest`
- `OrganizationDepartmentResponse`

列表返回 `CollectionResponse<OrganizationDepartmentResponse>`。部门在本阶段按系统配置字典处理，不做分页。

权限：

- 新增权限点 `organization:department:manage`，名称为“管理组织架构”。
- 超级管理员默认拥有。
- 部门创建、更新接口检查该权限。
- 部门列表和详情用于组织架构页、用户所属部门选择和数据范围部门主体选择，读取时允许 `organization:department:manage`、`authentication:user:manage` 或 `archive:data-scope:manage` 任一权限。

## 前端设计

新增菜单：

- 路由：`/system/organization-departments`
- 菜单：`系统配置 / 组织架构`
- 图标：`ApartmentOutlined`

页面结构：

- 左侧部门树，节点显示 `部门编码 部门名称`，支持按编码或名称筛选。
- 右侧显示当前选中部门详情和子部门列表。
- 支持新建根部门、新建下级部门、编辑部门、启用/停用、调整排序。
- 表单使用 Drawer，保留树和列表上下文。
- 空状态提供“新建根部门”主按钮，不使用占位页。

用户管理联动：

- 用户列表增加“所属部门”列。
- 新建和编辑用户增加所属部门选择，只显示启用部门。
- 用户接口返回 `departmentId`、`departmentCode`、`departmentName`。
- 用户没有所属部门时显示为空白，不用 `-` 或“暂无”占位。

数据范围联动：

- 数据范围主体从组织单元改为部门。
- 前端主体枚举从 `ORG_UNIT` 改为 `DEPARTMENT`。
- 部门主体绑定选项使用启用部门扁平列表，label 为 `部门编码 部门名称`。

## 数据范围运行时

用户有效数据范围来源为：

- 用户直接绑定的数据范围。
- 用户启用角色绑定的数据范围。
- 用户所属启用部门绑定的数据范围。

规则：

- 用户没有所属部门时，不追加部门主体范围。
- 用户所属部门停用后，不追加该部门主体范围。
- 用户直接范围和角色范围不受所属部门为空或停用影响。
- 超级管理员仍固定拥有全部数据范围。
- 如果某个档案门类需要“形成部门、承办部门、保管部门”等业务条件，由该门类动态字段表达，并通过现有动态字段数据范围机制过滤。

## 校验规则

部门创建和更新：

- 部门编码必填且唯一。
- 部门名称必填。
- 父部门必须存在。
- 父部门不能是自己或自己的后代。

引用校验：

- 设置用户所属部门时，部门必须存在且启用。
- 数据范围设置部门主体绑定时，部门必须存在且启用。
- 停用部门允许存在历史引用；停用后不能被新用户或新数据范围主体绑定选择。

错误：

- 参数错误返回现有 ProblemDetail 字段违规口径。
- 权限不足返回现有 403 口径。

## 测试与验证

后端测试：

- 部门创建、更新、启停、父级防环。
- 停用部门拒绝被用户归属和数据范围主体新引用。
- 用户所属部门范围参与 `resolveUserDataScope`。
- 停用所属部门不参与 `resolveUserDataScope`。
- `ORG_UNIT` 旧枚举和旧 API 不再存在。

前端测试：

- 组织架构页渲染树和右侧详情。
- 新建根部门、新建下级部门、编辑部门提交流。
- 用户管理部门列和部门选择。
- 数据范围主体从 `ORG_UNIT` 改为 `DEPARTMENT`。

验证命令：

- 后端：`cd server && mvn -q -DskipTests test-compile`
- 后端相关单测：按组织、认证、数据范围相关测试类定向执行。
- 前端：`vp check`
- 前端：`vp test`

## 实施顺序

1. 更新 OpenSpec，固化组织架构部门合同、用户部门归属和数据范围部门主体。
2. 调整 Flyway、实体、Repository、Service、Controller 和权限点。
3. 改造认证用户模型与用户管理接口。
4. 改造档案数据范围主体中的 `ORG_UNIT`，并移除档案写入中的旧组织字段。
5. 新增组织架构前端页面，并联动用户管理、数据范围页面。
6. 删除旧 `OrganizationUnit`、`organization-units` API 和前端旧类型。
7. 补齐后端和前端测试，执行定向验证。
