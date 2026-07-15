# API 文档

本文提供当前 HTTP API 的使用约定和资源索引。字段级业务合同以 `openspec/specs/` 和源码中的 Request/Response 类型为准。

## 基础约定

- 主应用默认地址：`http://localhost:8080`。
- 项目自有 API 前缀：`/api/v1`。
- JSON 时间默认使用 `Asia/Shanghai`，Date 格式为 `yyyy-MM-dd HH:mm:ss`。
- 登录态保存在服务端 HTTP Session 中，浏览器通过名为 `am_session` 的 session cookie 关联会话。
- 默认成功响应为 JSON，文件导入导出和下载接口使用二进制响应或 multipart 请求。

## 认证

登录前需要完成 CAP 安全验证：

```http
POST /api/v1/cap-challenges
POST /api/v1/cap-tokens
POST /api/v1/cap-tokens:validate
POST /api/v1/login-sessions
```

当前用户：

```http
GET /api/v1/me
```

登出或踢下线：

```http
DELETE /api/v1/login-sessions/{session}
```

除登录、CAP、公开文件短链、静态资源和 OPTIONS 预检外，其他接口默认要求认证。

## 错误响应

错误响应使用 `ProblemDetail`，常见结构：

```json
{
    "type": "about:blank",
    "title": "请求参数无效",
    "status": 400,
    "detail": "请求字段校验失败",
    "code": "INVALID_ARGUMENT",
    "reason": "FIELD_VIOLATION",
    "traceId": "a-request-trace-id",
    "path": "/api/v1/archive-items",
    "fieldViolations": [
        {
            "field": "name",
            "message": "字段不合法"
        }
    ]
}
```

常见 `code`：

| HTTP 状态 | code |
| --- | --- |
| 400 | `INVALID_ARGUMENT` |
| 401 | `UNAUTHENTICATED` |
| 403 | `PERMISSION_DENIED` |
| 404 | `NOT_FOUND` |
| 409 | `ALREADY_EXISTS` |
| 412 | `FAILED_PRECONDITION` |
| 429 | `RESOURCE_EXHAUSTED` |
| 500 | `INTERNAL` |

客户端按 `code`、`reason` 和 `fieldViolations` 处理稳定语义，并在错误反馈中保留 `traceId`；不要解析异常类名或自由文本来判断字段错误、游标失效或权限状态。

## 分页和集合

简单集合响应：

```json
{
    "items": []
}
```

游标分页响应：

```json
{
    "items": [],
    "self": "encoded-self-cursor",
    "prev": null,
    "next": "encoded-next-cursor",
    "first": null
}
```

偏移分页响应：

```json
{
    "items": [],
    "pageSize": 20,
    "pageNo": 1,
    "total": 123
}
```

游标分页默认不返回 `total`。`limit` 和 `cursor` 位于 URL query；使用 JSON 请求体表达复杂条件的搜索接口，把业务筛选和 `orderBy` 留在请求体，GET 资源列表的筛选条件仍使用 URL query。翻页必须复用首次请求的业务条件、排序和页大小。token 对客户端不透明，并与查询摘要绑定。分页、排序、过滤和 token 的完整合同以 `openspec/specs/api-contract/spec.md` 为准。

## 权限合同

- 路由、菜单和按钮权限只改善 PC 体验，不替代服务端授权。
- 服务端对档案读取、创建、更新、删除、锁定、电子文件、审计、元数据和授权管理等操作分别校验精确权限；涉及档案数据时同时应用用户数据范围。
- `superAdmin` 由服务端权限摘要声明；普通用户使用服务端返回的权限码。权限不足返回 `403 PERMISSION_DENIED`。
- `GET /api/v1/me/permissions` 是当前会话功能权限摘要；`GET /api/v1/authentication-user-options` 是数据范围授权使用的最小用户选项目录，不替代用户管理列表。

## 认证和用户

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/v1/cap-challenges` | 创建 CAP challenge |
| `POST` | `/api/v1/cap-tokens` | 兑换 CAP token |
| `POST` | `/api/v1/cap-tokens:validate` | 校验 CAP token |
| `POST` | `/api/v1/login-sessions` | 登录 |
| `GET` | `/api/v1/me` | 当前主体 |
| `GET` | `/api/v1/login-sessions` | 登录会话列表 |
| `DELETE` | `/api/v1/login-sessions/{session}` | 删除会话 |
| `POST` | `/api/v1/login-failure-limits/{username}:reset` | 重置登录失败限制 |
| `GET` | `/api/v1/authentication-events` | 认证审计 |
| `GET` | `/api/v1/authentication-users` | 用户列表 |
| `GET` | `/api/v1/authentication-user-options` | 数据范围授权用户选项游标列表，仅返回 ID、用户名和显示名 |
| `POST` | `/api/v1/authentication-users` | 创建用户 |
| `GET` | `/api/v1/authentication-users/{id}` | 用户详情 |
| `PATCH` | `/api/v1/authentication-users/{id}` | 更新用户 |
| `POST` | `/api/v1/authentication-users/{id}:resetPassword` | 重置密码 |
| `GET` | `/api/v1/authentication-users/{id}/roles` | 用户角色 |
| `PUT` | `/api/v1/authentication-users/{id}/roles` | 覆盖用户角色 |

## 授权

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v1/authorization-permissions` | 权限点列表 |
| `GET` | `/api/v1/me/permissions` | 当前用户权限 |
| `GET` | `/api/v1/authorization-roles` | 角色列表 |
| `POST` | `/api/v1/authorization-roles` | 创建角色 |
| `GET` | `/api/v1/authorization-roles/{id}` | 角色详情 |
| `PATCH` | `/api/v1/authorization-roles/{id}` | 更新角色 |
| `DELETE` | `/api/v1/authorization-roles/{id}` | 删除角色 |
| `GET` | `/api/v1/authorization-roles/{role}/permissions` | 角色权限 |
| `PUT` | `/api/v1/authorization-roles/{role}/permissions` | 覆盖角色权限 |

## 组织

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v1/organization-departments` | 部门列表 |
| `POST` | `/api/v1/organization-departments` | 创建部门 |
| `GET` | `/api/v1/organization-departments/{organizationDepartment}` | 部门详情 |
| `PATCH` | `/api/v1/organization-departments/{organizationDepartment}` | 更新部门 |

## 档案元数据

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v1/archive-fonds` | 全宗列表 |
| `POST` | `/api/v1/archive-fonds` | 创建全宗 |
| `PATCH` | `/api/v1/archive-fonds/{id}` | 更新全宗 |
| `DELETE` | `/api/v1/archive-fonds/{id}` | 删除全宗 |
| `GET` | `/api/v1/archive-fonds/{fondsCode}/category-scopes` | 全宗可用分类范围 |
| `PUT` | `/api/v1/archive-fonds/{fondsCode}/category-scopes` | 覆盖全宗可用分类范围 |
| `GET` | `/api/v1/archive-fonds/{fondsCode}/categories` | 全宗可用分类 |
| `GET` | `/api/v1/archive-classification-schemes` | 分类方案列表 |
| `POST` | `/api/v1/archive-classification-schemes` | 创建分类方案 |
| `PATCH` | `/api/v1/archive-classification-schemes/{id}` | 更新分类方案 |
| `GET` | `/api/v1/archive-security-levels` | 密级列表 |
| `PATCH` | `/api/v1/archive-security-levels/{id}` | 更新密级 |
| `GET` | `/api/v1/archive-retention-periods` | 保管期限列表 |
| `PATCH` | `/api/v1/archive-retention-periods/{id}` | 更新保管期限 |
| `GET` | `/api/v1/archive-categories` | 分类列表 |
| `POST` | `/api/v1/archive-categories` | 创建分类 |
| `PATCH` | `/api/v1/archive-categories/{id}` | 更新分类 |
| `DELETE` | `/api/v1/archive-categories/{id}` | 删除分类 |
| `POST` | `/api/v1/archive-categories/{id}:buildTable` | 构建分类动态表 |
| `POST` | `/api/v1/archive-categories/{id}:rebuildSearchProjection` | 重建搜索投影 |

字段、布局和唯一规则：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v1/archive-categories/{categoryId}/fields` | 字段列表 |
| `POST` | `/api/v1/archive-categories/{categoryId}/fields` | 创建字段 |
| `PATCH` | `/api/v1/archive-categories/{categoryId}/fields/{fieldId}` | 更新字段 |
| `DELETE` | `/api/v1/archive-categories/{categoryId}/fields/{fieldId}` | 删除字段 |
| `GET` | `/api/v1/archive-categories/{categoryId}/layouts/{surface}` | 查询布局 |
| `PATCH` | `/api/v1/archive-categories/{categoryId}/layouts/{surface}` | 更新布局 |
| `GET` | `/api/v1/archive-categories/{categoryId}/unique-constraints` | 唯一规则列表 |
| `POST` | `/api/v1/archive-categories/{categoryId}/unique-constraints` | 创建唯一规则 |
| `PATCH` | `/api/v1/archive-categories/{categoryId}/unique-constraints/{constraintId}` | 更新唯一规则 |
| `DELETE` | `/api/v1/archive-categories/{categoryId}/unique-constraints/{constraintId}` | 删除唯一规则 |

## 档案条目、案卷和检索

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v1/archive-items` | 档案条目列表 |
| `POST` | `/api/v1/archive-items` | 创建档案条目 |
| `GET` | `/api/v1/archive-items/{id}` | 档案条目详情 |
| `PATCH` | `/api/v1/archive-items/{id}` | 更新档案条目 |
| `DELETE` | `/api/v1/archive-items/{id}` | 删除档案条目 |
| `POST` | `/api/v1/archive-items:search` | 高级搜索，URL query 使用 `limit`、`cursor`，请求体可含 `volumeId` 和 `orderBy` |
| `POST` | `/api/v1/archive-items:discover` | 全文发现，使用游标分页 |
| `POST` | `/api/v1/archive-items:searchDeleted` | 删除记录游标搜索 |
| `POST` | `/api/v1/archive-items/{id}:lock` | 锁定条目，请求体可提交 `reason` |
| `POST` | `/api/v1/archive-items/{id}:unlock` | 解锁条目 |
| `GET` | `/api/v1/archive-items/{id}/relations` | 条目关联游标列表，URL query 使用 `depth`、`limit`、`cursor` |
| `POST` | `/api/v1/archive-items/{id}/relations` | 创建条目关联 |
| `DELETE` | `/api/v1/archive-items/{id}/relations/{relationId}` | 删除条目关联 |
| `GET` | `/api/v1/archive-volumes` | 案卷游标列表，可按全宗和分类筛选 |
| `POST` | `/api/v1/archive-volumes` | 创建案卷 |
| `GET` | `/api/v1/archive-volumes/{id}` | 案卷详情 |
| `POST` | `/api/v1/archive-volumes/{id}:addItem` | 将条目加入案卷，请求体为 `itemId`、可选 `displayOrder`，成功返回 `204` |
| `GET` | `/api/v1/archive-item-audits` | 档案条目审计 |
| `GET` | `/api/v1/archive-categories/{id}/related-filter-categories` | 关联筛选分类 |

档案创建和更新合同分别维护固定字段、`securityLevelId`、`retentionPeriodId`、`physicalFields` 与 `dynamicFields`。字段校验错误通过 `fieldViolations` 返回完整路径，例如 `physicalFields.box_no` 或 `dynamicFields.title`。

## 明细表、导入导出和电子文件

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v1/archive-categories/{categoryId}/item-line-tables` | 分类明细表列表 |
| `POST` | `/api/v1/archive-categories/{categoryId}/item-line-tables` | 创建明细表 |
| `GET` | `/api/v1/archive-item-line-tables/{lineTableId}` | 明细表详情 |
| `POST` | `/api/v1/archive-item-line-tables/{lineTableId}:build` | 构建明细表 |
| `GET` | `/api/v1/archive-item-line-tables/{lineTableId}/fields` | 明细表字段 |
| `POST` | `/api/v1/archive-item-line-tables/{lineTableId}/fields` | 创建明细表字段 |
| `GET` | `/api/v1/archive-items/{archiveItem}/line-tables` | 当前档案可用的只读明细表定义 |
| `GET` | `/api/v1/archive-items/{archiveItem}/line-tables/{lineTable}/rows` | 明细行游标列表，按 `lineOrder`、`id` 稳定排序 |
| `POST` | `/api/v1/archive-items/{archiveItem}/line-tables/{lineTable}/rows` | 创建明细行 |
| `PATCH` | `/api/v1/archive-items/{archiveItem}/line-tables/{lineTable}/rows/{row}` | 部分更新明细行，缺失字段不修改，显式 `null` 清空值 |
| `DELETE` | `/api/v1/archive-items/{archiveItem}/line-tables/{lineTable}/rows/{row}` | 逻辑删除明细行 |
| `GET` | `/api/v1/archive-categories/{categoryId}/archive-items:importTemplate` | 下载导入模板 |
| `POST` | `/api/v1/archive-categories/{categoryId}/archive-items:import` | multipart 导入档案 |
| `POST` | `/api/v1/archive-items:export` | 按请求体导出 |
| `GET` | `/api/v1/archive-items:export` | 按 `query` 参数导出 |
| `GET` | `/api/v1/archive-items/{archiveItem}/electronic-files` | 电子文件列表 |
| `POST` | `/api/v1/archive-items/{archiveItem}/electronic-files` | multipart 上传电子文件 |
| `DELETE` | `/api/v1/archive-items/{archiveItem}/electronic-files/{electronicFile}` | 删除电子文件 |
| `POST` | `/api/v1/archive-items/{archiveItem}/electronic-files/{electronicFile}:createDownloadLink` | 创建下载短链 |

电子文件上传参数：

- `file`：文件。
- `usageType`：可选用途类型。
- `displayOrder`：可选显示顺序。

## 工作台

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v1/workspace-summary` | 当前用户数据范围内的档案总数、草稿数、锁定数和电子文件数 |

已认证用户可以读取工作台摘要；没有 `archive:item:read` 时四项计数均为零且服务端不查询档案分类。拥有读取权限时，计数继续受全宗、分类和动态字段数据范围约束。

## 数据范围

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v1/archive-data-scopes` | 数据范围列表 |
| `POST` | `/api/v1/archive-data-scopes` | 创建数据范围 |
| `GET` | `/api/v1/archive-data-scopes/{archiveDataScope}` | 数据范围详情 |
| `PUT` | `/api/v1/archive-data-scopes/{archiveDataScope}` | 更新数据范围 |
| `GET` | `/api/v1/authorization-roles/{role}/archive-data-scopes` | 角色数据范围 |
| `PUT` | `/api/v1/authorization-roles/{role}/archive-data-scopes` | 覆盖角色数据范围 |
| `GET` | `/api/v1/authorization-users/{user}/archive-data-scopes` | 用户数据范围 |
| `PUT` | `/api/v1/authorization-users/{user}/archive-data-scopes` | 覆盖用户数据范围 |
| `GET` | `/api/v1/organization-departments/{organizationDepartment}/archive-data-scopes` | 部门数据范围 |
| `PUT` | `/api/v1/organization-departments/{organizationDepartment}/archive-data-scopes` | 覆盖部门数据范围 |
| `GET` | `/api/v1/archive-categories/{archiveCategory}/data-scope-fields` | 可作为范围条件的字段 |

## 治理、本体和规则

治理方案：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v1/archive-governance-schemes` | 治理方案列表 |
| `POST` | `/api/v1/archive-governance-schemes` | 创建治理方案 |
| `GET` | `/api/v1/archive-governance-schemes/{schemeId}` | 治理方案详情 |
| `PATCH` | `/api/v1/archive-governance-schemes/{schemeId}` | 更新治理方案 |
| `DELETE` | `/api/v1/archive-governance-schemes/{schemeId}` | 删除治理方案 |
| `GET` | `/api/v1/archive-governance-schemes/{schemeId}/versions` | 版本列表 |
| `POST` | `/api/v1/archive-governance-schemes/{schemeId}/versions` | 创建版本 |
| `PATCH` | `/api/v1/archive-governance-scheme-versions/{versionId}` | 更新版本 |
| `POST` | `/api/v1/archive-governance-scheme-versions/{versionId}:publish` | 发布版本 |
| `POST` | `/api/v1/archive-governance-scheme-versions/{versionId}:freeze` | 冻结版本 |
| `POST` | `/api/v1/archive-governance-scheme-versions/{versionId}:retire` | 退役版本 |
| `GET` | `/api/v1/archive-governance-scheme-versions:resolveDefault` | 解析默认版本 |
| `GET` | `/api/v1/archive-governance-scheme-versions/{versionId}/scopes` | 版本适用范围 |
| `PUT` | `/api/v1/archive-governance-scheme-versions/{versionId}/scopes` | 覆盖适用范围 |
| `GET` | `/api/v1/archive-governance-scheme-versions/{versionId}/bindings` | 版本绑定 |
| `PUT` | `/api/v1/archive-governance-scheme-versions/{versionId}/bindings` | 覆盖版本绑定 |

本体和规则接口较多，资源包括：

- `/api/v1/archive-ontology-object-types`
- `/api/v1/archive-ontology-attribute-types`
- `/api/v1/archive-ontology-attribute-mappings`
- `/api/v1/archive-ontology-relation-types`
- `/api/v1/archive-ontology-event-types`
- `/api/v1/archive-rules`
- `/api/v1/archive-rule-traces:search`

本体内置初始化使用 custom method：

```http
POST /api/v1/archive-ontology-object-types:initializeBuiltins
POST /api/v1/archive-ontology-event-types:initializeBuiltins
```

规则动作：

```http
POST /api/v1/archive-rules/{ruleId}:publish
POST /api/v1/archive-rules/{ruleId}:enable
POST /api/v1/archive-rules/{ruleId}:disable
POST /api/v1/archive-rules:execute
```

## 文件短链和归档接收

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v1/file-links/{code}:download` | 登录下载短链 |
| `GET` | `/api/v1/public-file-links/{code}:download` | 公开下载短链 |
| `GET` | `/api/v1/intake` | 归档接收概览 |

## 预览服务 API

预览服务不是 `/api/v1` 主应用资源，默认监听 `:8088`。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/healthz` | 健康检查 |
| `GET` | `/v1/capabilities` | 能力清单 |
| `POST` | `/v1/preview:convert` | multipart 同步预览转换 |

详见 `../preview/README.md`。
