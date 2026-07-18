# OpenSpec 索引

`openspec/` 是项目业务合同和 API 规则的真相源。说明文档可以解释如何使用，不能覆盖或绕开 OpenSpec 的验收场景。

## 目录结构

| 路径 | 职责 |
| --- | --- |
| `config.yaml` | OpenSpec 项目配置 |
| `specs/` | 当前作为业务和 API 合同真相源的能力规格 |
| `changes/` | 进行中的 change；归档历史位于 `changes/archive/` |

## 当前真相源规格

当前规格索引如下，校准时以 CLI 输出为准：

- `specs/api-contract/spec.md`
- `specs/approval-workflow/spec.md`
- `specs/unified-todo/spec.md`
- `specs/archive-classification-scheme/spec.md`
- `specs/archive-data-scope/spec.md`
- `specs/archive-governance-scheme/spec.md`
- `specs/archive-governance-workbench/spec.md`
- `specs/archive-import-export/spec.md`
- `specs/archive-local-rule-engine/spec.md`
- `specs/archive-metadata/spec.md`
- `specs/archive-record-routing/spec.md`
- `specs/archive-record-search/spec.md`
- `specs/archive-runtime-check-portability/spec.md`
- `specs/authorization-permissions/spec.md`
- `specs/cursor-pagination-summary/spec.md`
- `specs/file-preview-service/spec.md`
- `specs/file-storage/spec.md`
- `specs/intake/spec.md`
- `specs/login-authentication/spec.md`
- `specs/organization-departments/spec.md`

```bash
openspec list --specs
```

## 当前 change

活动 change 以 CLI 输出为准：

```bash
openspec list
```

`changes/` 只保留真实进行中的变更；任务全部完成后必须校准规格并及时归档。

## 使用规则

- 设计或修改 HTTP API 前，先查 `specs/api-contract/spec.md`。
- 改档案元数据、动态表、唯一规则或分类合同前，先查 `specs/archive-metadata/spec.md`。
- 改档案搜索、管理查询或全文 provider 前，先查 `specs/archive-record-search/spec.md`。
- 改文件存储、短链或对象存储前，先查 `specs/file-storage/spec.md`。
- 改登录、CAP、会话或认证用户前，先查 `specs/login-authentication/spec.md`。
- 业务或 API change 完成后，先将增量同步或校准至 `specs/`，再将 change 目录归档到 `changes/archive/`；里程碑或纯工程治理 change 可以跳过规格同步并直接归档。

## 与说明文档的关系

- `docs/api.md` 是当前接口使用索引，不替代 `api-contract`。
- `docs/architecture.md` 解释代码结构，不替代业务规格。
- `docs/user-guide/README.md` 解释页面使用，不作为权限或字段合同。
- `AGENTS.md` 约束协作和工程规则，OpenSpec 约束业务合同。
