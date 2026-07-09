# OpenSpec 索引

`openspec/` 是项目业务合同和 API 规则的真相源。说明文档可以解释如何使用，不能覆盖或绕开 OpenSpec 的验收场景。

## 目录结构

| 路径 | 职责 |
| --- | --- |
| `config.yaml` | OpenSpec 项目配置 |
| `specs/` | 已归档或当前作为真相源的能力规格 |
| `changes/` | 提案、设计、任务和规格增量 |

## 当前真相源规格

| 规格 | 文件 | 覆盖范围 |
| --- | --- | --- |
| API 合同 | `specs/api-contract/spec.md` | 资源建模、路径、分页、错误响应、ID、异步任务和第三方协议例外 |
| 档案元数据 | `specs/archive-metadata/spec.md` | 项目表命名、字段校验、逻辑删除唯一性、动态表、条目/案卷、明细表、字段检索和唯一规则 |
| 档案记录搜索 | `specs/archive-record-search/spec.md` | 管理查询、全文发现、全文 provider 和搜索投影 |
| 文件存储 | `specs/file-storage/spec.md` | 文件元数据、存储路由、S3 兼容协议、对象键、指纹和短链 |
| 归档接收 | `specs/intake/spec.md` | 归档接收入口和档案核心边界 |
| 登录认证 | `specs/login-authentication/spec.md` | CAP、账号密码登录、失败限制、用户认证数据、当前主体、退出登录和 PC 集成 |

## 当前 change

`changes/` 下的目录表示已经提出或实施过的变更。读取顺序建议：

1. `proposal.md`：为什么要改、改什么、不改什么。
2. `design.md`：关键设计、替代方案和风险。
3. `specs/*/spec.md`：对能力合同的增量。
4. `tasks.md`：实施任务和完成状态。
5. `.openspec.yaml`：change 元数据。

当前存在的 change：

| change | 主题 |
| --- | --- |
| `add-archive-data-scope-permissions-mvp-closure` | 档案数据范围和权限 MVP 收口 |
| `add-archive-governance-foundation` | 治理方案、本体核心和本地规则引擎 foundation |
| `add-archive-metadata-routing` | 档案元数据和路由基础 |
| `add-archive-mvp-foundation` | 档案系统 MVP 完成门槛 |
| `add-archive-record-editing-layout` | 档案记录详情、编辑和布局能力 |
| `add-auth-session-audit` | 登录会话和认证审计 |
| `add-flowable-approval-workflow` | Flowable 审批流 |
| `add-fonds-classification-adoption` | 分类方案和全宗可用分类范围 |
| `add-organization-departments` | 组织部门 |
| `add-preview-service` | 独立文件预览服务 |
| `improve-archive-governance-workbench` | 治理工作台完善 |
| `split-archive-item-volume-and-relations` | 条目、案卷和关联拆分 |

## 使用规则

- 设计或修改 HTTP API 前，先查 `specs/api-contract/spec.md`。
- 改档案元数据、动态表、唯一规则或分类合同前，先查 `specs/archive-metadata/spec.md`。
- 改档案搜索、管理查询或全文 provider 前，先查 `specs/archive-record-search/spec.md`。
- 改文件存储、短链或对象存储前，先查 `specs/file-storage/spec.md`。
- 改登录、CAP、会话或认证用户前，先查 `specs/login-authentication/spec.md`。
- change 完成并成为长期合同后，应归档到 `specs/`，并更新本文索引。

## 与说明文档的关系

- `docs/api.md` 是当前接口使用索引，不替代 `api-contract`。
- `docs/architecture.md` 解释代码结构，不替代业务规格。
- `docs/user-guide/README.md` 解释页面使用，不作为权限或字段合同。
- `AGENTS.md` 约束协作和工程规则，OpenSpec 约束业务合同。
