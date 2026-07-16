## Context

现有 `organization_unit` 半成品没有形成完整组织架构能力，却已经影响档案所属组织字段和数据范围命名。后续实现应一次性收敛到部门口径，避免在未发布阶段继续保留抽象组织单元和部门两套语义。

## Goals / Non-Goals

**Goals:**

- 在存储、Java、API 和前端合同中统一使用部门作为组织架构节点。
- 由 `organization` 业务模块拥有部门实体、服务和接口。
- 支持用户所属部门，并让用户所属启用部门绑定的数据范围参与运行期范围计算。
- 组织部门不作为档案记录的固定归属字段；档案门类若需要部门类业务字段，通过元数据动态字段定义。
- 明确停用部门的历史引用保留和新选择限制。

**Non-Goals:**

- 不保留 `organization_unit`、`OrganizationUnit`、`ORG_UNIT` 或 `/api/v1/organization-units` 兼容分支。
- 不实现父部门范围自动包含子部门用户。
- 不引入项目级组织适配层或通用组织主体抽象。

## Decisions

### Department is the organization node

组织层级在存储、Java、API 和前端合同中统一使用部门术语。`unit` 不作为兼容别名保留。

### Organization module owns departments

部门实体、Repository、Service 和 Controller 放在 `github.luckygc.am.module.organization` 下。其他模块通过 `OrganizationDepartmentService` 校验部门可用性并读取展示数据。

### Department API has no delete method

组织架构部门资源使用 `/api/v1/organization-departments`。首版只提供列表、详情、创建和 `PATCH` 更新；启停通过更新 `enabled` 字段表达，排序通过 `sortOrder` 字段表达。部门不提供删除接口，避免破坏已有用户和数据范围主体历史引用。

### Department reads support related selectors

部门创建和更新只允许 `organization:department:manage`。部门列表和详情同时服务组织架构页、用户所属部门选择和部门数据范围主体选择，因此读取允许 `organization:department:manage`、`authentication:user:manage` 或 `archive:data-scope:manage` 任一权限。

### Direct department scope only

运行期档案数据范围只纳入用户所属启用部门直接绑定的启用数据范围。父部门绑定不在本 change 中自动包含子部门用户。

### Departments are not archive-owned fields

档案条目和案卷基础表不新增 `department_id`，档案创建、更新和导入导出合同也不接收固定 `departmentId`。如果某个门类需要形成部门、承办部门或保管部门，由档案元数据动态字段配置，并可按现有动态字段数据范围机制参与过滤。

### Department references are historical-safe

停用部门可以继续被已有用户和数据范围主体关系引用。停用部门不能被新用户归属或新数据范围主体绑定选择。

## Risks / Trade-offs

- 不保留 `organization_unit` 兼容会要求同一批实现完整替换旧符号，但可以避免未发布阶段继续扩大双口径维护成本。
- 不提供删除接口会让停用成为唯一退出路径，但能保留审计和历史引用一致性。
- 父部门不自动包含子部门用户会让首版部门数据范围更简单、可解释；后续如果业务要求继承，再单独设计继承语义。
- 停用部门保留历史引用会让查询展示需要容忍 disabled 状态，但能避免历史档案和用户关系被破坏。

## Migration Plan

1. 先合并 OpenSpec 合同，固定部门口径和不兼容边界。
2. 后端直接把未发布的 `organization_unit` 迁移、实体和字段替换为部门目标结构。
3. 前端同步替换类型、页面、菜单和筛选字段。
4. 最后搜索并移除旧 `organization_unit`、`OrganizationUnit`、`ORG_UNIT` 和 `orgUnitId` 符号。
