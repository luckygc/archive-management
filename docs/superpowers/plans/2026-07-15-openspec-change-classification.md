# OpenSpec 历史变更归档分类清单

## 范围与基线

本清单只建立后续归档的分类基线，不在本步骤执行归档、合并 delta 或修改业务合同。核对输入为当前 15 个活动 change、6 个当前规格、`openspec list --json`，以及每个 change 的 `proposal.md`、`design.md`、`tasks.md` 和全部 delta `spec.md`。

2026-07-15 基线结果：

- `openspec validate --all --strict --json --no-interactive`：21/21 通过，其中 15 个 change、6 个当前规格，失败数为 0。
- 14 个历史 change 的任务全部完成；`add-flowable-approval-workflow` 为 5/26，尚余 21 项，保持活动且不纳入本轮归档。
- 当前规格共 6 份：`api-contract`、`archive-metadata`、`archive-record-search`、`file-storage`、`intake`、`login-authentication`。

## 固定分类

| change | 处理方式 | 长期规格去向 |
| --- | --- | --- |
| add-archive-metadata-routing | 校准后归档 | archive-metadata、archive-record-routing |
| split-archive-item-volume-and-relations | 校准后归档 | archive-metadata、archive-record-search |
| add-auth-session-audit | 校准后归档 | login-authentication |
| add-archive-mvp-foundation | 跳过规格归档 | 里程碑，不创建当前 capability |
| add-archive-data-scope-permissions-mvp-closure | 先合并业务 delta，再跳过规格归档 | archive-data-scope、authorization-permissions、archive-import-export、archive-metadata、archive-record-search |
| add-organization-departments | 校准后归档 | organization-departments、archive-data-scope、login-authentication |
| add-preview-service | 校准后归档 | file-preview-service |
| add-archive-governance-foundation | 校准后归档 | archive-governance-scheme、archive-ontology-core、archive-local-rule-engine |
| add-fonds-classification-adoption | 校准后归档 | archive-classification-scheme |
| improve-archive-governance-workbench | 校准后归档 | archive-governance-workbench |
| add-archive-record-editing-layout | 校准后归档 | archive-metadata、archive-record-routing |
| close-archive-pc-mvp | 先合并搜索 delta，再跳过规格归档 | archive-record-search；不创建 archive-pc-mvp |
| simplify-code-and-enforce-bean-boundaries | 跳过规格归档 | 工程治理由架构测试和文档承载 |
| unify-s3-file-storage | 核对当前规格后跳过规格归档 | file-storage 已成为当前合同 |
| add-flowable-approval-workflow | 保持活动 | approval-workflow delta 保留在活动 change |

“校准后归档”表示后续归档步骤必须以当前代码、当前规格和后发生 change 为准消解历史表述，再执行严格校验；不得机械合并相互冲突的历史 delta。“跳过规格归档”表示保留归档记录，但不为阶段里程碑或已经由当前规格承载的合同创建重复 capability。

## 材料与完成证据

下表中的制品路径均已逐份核对；“delta 条数”按 `openspec show`/delta 文件中的 Requirement 计数。

| change | 任务基线 | delta 条数 | 核对证据 |
| --- | ---: | ---: | --- |
| add-archive-data-scope-permissions-mvp-closure | 43/43 | 17 | 变更制品：`openspec/changes/add-archive-data-scope-permissions-mvp-closure/proposal.md`、`openspec/changes/add-archive-data-scope-permissions-mvp-closure/design.md`、`openspec/changes/add-archive-data-scope-permissions-mvp-closure/tasks.md`；delta：`openspec/changes/add-archive-data-scope-permissions-mvp-closure/specs/archive-data-scope/spec.md`、`openspec/changes/add-archive-data-scope-permissions-mvp-closure/specs/archive-import-export/spec.md`、`openspec/changes/add-archive-data-scope-permissions-mvp-closure/specs/archive-metadata/spec.md`、`openspec/changes/add-archive-data-scope-permissions-mvp-closure/specs/archive-mvp-foundation/spec.md`、`openspec/changes/add-archive-data-scope-permissions-mvp-closure/specs/archive-record-search/spec.md`、`openspec/changes/add-archive-data-scope-permissions-mvp-closure/specs/authorization-permissions/spec.md` |
| add-archive-governance-foundation | 49/49 | 19 | 变更制品：`openspec/changes/add-archive-governance-foundation/proposal.md`、`openspec/changes/add-archive-governance-foundation/design.md`、`openspec/changes/add-archive-governance-foundation/tasks.md`；delta：`openspec/changes/add-archive-governance-foundation/specs/archive-governance-scheme/spec.md`、`openspec/changes/add-archive-governance-foundation/specs/archive-local-rule-engine/spec.md`、`openspec/changes/add-archive-governance-foundation/specs/archive-ontology-core/spec.md` |
| add-archive-metadata-routing | 22/22 | 9 | 变更制品：`openspec/changes/add-archive-metadata-routing/proposal.md`、`openspec/changes/add-archive-metadata-routing/design.md`、`openspec/changes/add-archive-metadata-routing/tasks.md`；delta：`openspec/changes/add-archive-metadata-routing/specs/archive-metadata/spec.md`、`openspec/changes/add-archive-metadata-routing/specs/archive-record-routing/spec.md` |
| add-archive-mvp-foundation | 22/22 | 7 | 变更制品：`openspec/changes/add-archive-mvp-foundation/proposal.md`、`openspec/changes/add-archive-mvp-foundation/design.md`、`openspec/changes/add-archive-mvp-foundation/tasks.md`；delta：`openspec/changes/add-archive-mvp-foundation/specs/archive-mvp-foundation/spec.md` |
| add-archive-record-editing-layout | 25/25 | 7 | 变更制品：`openspec/changes/add-archive-record-editing-layout/proposal.md`、`openspec/changes/add-archive-record-editing-layout/design.md`、`openspec/changes/add-archive-record-editing-layout/tasks.md`；delta：`openspec/changes/add-archive-record-editing-layout/specs/archive-metadata/spec.md`、`openspec/changes/add-archive-record-editing-layout/specs/archive-record-routing/spec.md` |
| add-auth-session-audit | 20/20 | 4 | 变更制品：`openspec/changes/add-auth-session-audit/proposal.md`、`openspec/changes/add-auth-session-audit/design.md`、`openspec/changes/add-auth-session-audit/tasks.md`；delta：`openspec/changes/add-auth-session-audit/specs/login-authentication/spec.md` |
| add-flowable-approval-workflow | 5/26 | 8 | 变更制品：`openspec/changes/add-flowable-approval-workflow/proposal.md`、`openspec/changes/add-flowable-approval-workflow/design.md`、`openspec/changes/add-flowable-approval-workflow/tasks.md`；delta：`openspec/changes/add-flowable-approval-workflow/specs/approval-workflow/spec.md`；保持活动 |
| add-fonds-classification-adoption | 20/20 | 4 | 变更制品：`openspec/changes/add-fonds-classification-adoption/proposal.md`、`openspec/changes/add-fonds-classification-adoption/design.md`、`openspec/changes/add-fonds-classification-adoption/tasks.md`；delta：`openspec/changes/add-fonds-classification-adoption/specs/archive-classification-scheme/spec.md` |
| add-organization-departments | 14/14 | 5 | 变更制品：`openspec/changes/add-organization-departments/proposal.md`、`openspec/changes/add-organization-departments/design.md`、`openspec/changes/add-organization-departments/tasks.md`；delta：`openspec/changes/add-organization-departments/specs/archive-data-scope/spec.md`、`openspec/changes/add-organization-departments/specs/login-authentication/spec.md`、`openspec/changes/add-organization-departments/specs/organization-departments/spec.md` |
| add-preview-service | 12/12 | 4 | 变更制品：`openspec/changes/add-preview-service/proposal.md`、`openspec/changes/add-preview-service/design.md`、`openspec/changes/add-preview-service/tasks.md`；delta：`openspec/changes/add-preview-service/specs/file-preview-service/spec.md` |
| close-archive-pc-mvp | 64/64 | 11 | 变更制品：`openspec/changes/close-archive-pc-mvp/proposal.md`、`openspec/changes/close-archive-pc-mvp/design.md`、`openspec/changes/close-archive-pc-mvp/tasks.md`；delta：`openspec/changes/close-archive-pc-mvp/specs/archive-pc-mvp/spec.md`、`openspec/changes/close-archive-pc-mvp/specs/archive-record-search/spec.md` |
| improve-archive-governance-workbench | 13/13 | 3 | 变更制品：`openspec/changes/improve-archive-governance-workbench/proposal.md`、`openspec/changes/improve-archive-governance-workbench/design.md`、`openspec/changes/improve-archive-governance-workbench/tasks.md`；delta：`openspec/changes/improve-archive-governance-workbench/specs/archive-governance-workbench/spec.md` |
| simplify-code-and-enforce-bean-boundaries | 25/25 | 7 | 变更制品：`openspec/changes/simplify-code-and-enforce-bean-boundaries/proposal.md`、`openspec/changes/simplify-code-and-enforce-bean-boundaries/design.md`、`openspec/changes/simplify-code-and-enforce-bean-boundaries/tasks.md`；delta：`openspec/changes/simplify-code-and-enforce-bean-boundaries/specs/code-maintainability/spec.md` |
| split-archive-item-volume-and-relations | 8/8 | 6 | 变更制品：`openspec/changes/split-archive-item-volume-and-relations/proposal.md`、`openspec/changes/split-archive-item-volume-and-relations/design.md`、`openspec/changes/split-archive-item-volume-and-relations/tasks.md`；delta：`openspec/changes/split-archive-item-volume-and-relations/specs/archive-metadata/spec.md`、`openspec/changes/split-archive-item-volume-and-relations/specs/archive-record-search/spec.md` |
| unify-s3-file-storage | 12/12 | 3 | 变更制品：`openspec/changes/unify-s3-file-storage/proposal.md`、`openspec/changes/unify-s3-file-storage/design.md`、`openspec/changes/unify-s3-file-storage/tasks.md`；delta：`openspec/changes/unify-s3-file-storage/specs/file-storage/spec.md` |

## 混合 change 逐条 Requirement 核对

### add-archive-data-scope-permissions-mvp-closure

该 change 的业务 delta 必须先进入长期规格，再以跳过规格方式归档阶段里程碑。当前尚无 `archive-data-scope`、`archive-import-export` 和 `authorization-permissions` 三份当前规格；表中对它们只标注“计划目标（当前缺失）”，现有证据始终指向真实 delta，不把未创建路径冒充已有规格。后续合并时，`add-organization-departments` 对组织语义的修订优先于本 change 较早的 `org_unit` 固定维度表述：不得恢复档案主表固定组织字段，部门类业务条件继续通过动态字段表达，授权主体使用 `DEPARTMENT`。

| delta capability | Requirement | 结论 | 对应规格或证据 |
| --- | --- | --- | --- |
| archive-data-scope | 档案数据范围定义 | 需要合并到当前规格 | 现有证据：`openspec/changes/add-archive-data-scope-permissions-mvp-closure/specs/archive-data-scope/spec.md`；计划目标（当前缺失）：`archive-data-scope` |
| archive-data-scope | 固定维度范围条件 | 需要合并到当前规格 | 现有证据：`openspec/changes/add-archive-data-scope-permissions-mvp-closure/specs/archive-data-scope/spec.md`、`openspec/changes/add-organization-departments/specs/archive-data-scope/spec.md`；计划目标（当前缺失）：`archive-data-scope`；合并时删除 `org_unit` 固定维度历史语义 |
| archive-data-scope | 动态字段范围条件 | 需要合并到当前规格 | 现有证据：`openspec/changes/add-archive-data-scope-permissions-mvp-closure/specs/archive-data-scope/spec.md`；计划目标（当前缺失）：`archive-data-scope` |
| archive-data-scope | 授权主体绑定档案数据范围 | 需要合并到当前规格 | 现有证据：`openspec/changes/add-archive-data-scope-permissions-mvp-closure/specs/archive-data-scope/spec.md`、`openspec/changes/add-organization-departments/specs/archive-data-scope/spec.md`；计划目标（当前缺失）：`archive-data-scope`；主体语义按后续 `DEPARTMENT` delta 校准 |
| archive-data-scope | 档案接口应用数据范围 | 需要合并到当前规格 | 现有证据：`openspec/changes/add-archive-data-scope-permissions-mvp-closure/specs/archive-data-scope/spec.md`；计划目标（当前缺失）：`archive-data-scope` |
| archive-import-export | 标准 Excel 导入 | 需要合并到当前规格 | 现有证据：`openspec/changes/add-archive-data-scope-permissions-mvp-closure/specs/archive-import-export/spec.md`；计划目标（当前缺失）：`archive-import-export` |
| archive-import-export | 查询结果导出 | 需要合并到当前规格 | 现有证据：`openspec/changes/add-archive-data-scope-permissions-mvp-closure/specs/archive-import-export/spec.md`；计划目标（当前缺失）：`archive-import-export` |
| archive-metadata | 动态字段数据范围能力标记 | 需要合并到当前规格 | 现有证据：`openspec/changes/add-archive-data-scope-permissions-mvp-closure/specs/archive-metadata/spec.md`；已有目标：`openspec/specs/archive-metadata/spec.md` |
| archive-mvp-foundation | MVP 完成门槛 | 里程碑要求不保留 | 该结论是“不创建长期里程碑 capability”，不表示由单一测试证明全部 MVP；现有证据分层见下表 |
| archive-record-search | 管理查询 | 需要合并到当前规格 | 现有证据：`openspec/changes/add-archive-data-scope-permissions-mvp-closure/specs/archive-record-search/spec.md`；已有目标：`openspec/specs/archive-record-search/spec.md` |
| archive-record-search | 全文发现 | 需要合并到当前规格 | 现有证据：`openspec/changes/add-archive-data-scope-permissions-mvp-closure/specs/archive-record-search/spec.md`；已有目标：`openspec/specs/archive-record-search/spec.md` |
| archive-record-search | 档案详情读取 | 需要合并到当前规格 | 现有证据：`openspec/changes/add-archive-data-scope-permissions-mvp-closure/specs/archive-record-search/spec.md`；已有目标：`openspec/specs/archive-record-search/spec.md` |
| authorization-permissions | 功能权限点枚举 | 需要合并到当前规格 | 现有证据：`openspec/changes/add-archive-data-scope-permissions-mvp-closure/specs/authorization-permissions/spec.md`；计划目标（当前缺失）：`authorization-permissions` |
| authorization-permissions | 角色功能权限绑定 | 需要合并到当前规格 | 现有证据：`openspec/changes/add-archive-data-scope-permissions-mvp-closure/specs/authorization-permissions/spec.md`；计划目标（当前缺失）：`authorization-permissions` |
| authorization-permissions | 角色管理 | 需要合并到当前规格 | 现有证据：`openspec/changes/add-archive-data-scope-permissions-mvp-closure/specs/authorization-permissions/spec.md`；计划目标（当前缺失）：`authorization-permissions` |
| authorization-permissions | 用户管理 | 需要合并到当前规格 | 现有证据：`openspec/changes/add-archive-data-scope-permissions-mvp-closure/specs/authorization-permissions/spec.md`；计划目标（当前缺失）：`authorization-permissions` |
| authorization-permissions | 后端功能权限判断 | 需要合并到当前规格 | 现有证据：`openspec/changes/add-archive-data-scope-permissions-mvp-closure/specs/authorization-permissions/spec.md`；计划目标（当前缺失）：`authorization-permissions` |

MVP 完成门槛是跨能力里程碑，没有单个“MVP 全部完成”自动化测试。本清单只用下列分层证据说明为何不把里程碑本身升格为长期业务规格，不用其替代后续逐 capability 的规格合并与验收。

| 门槛分组 | 证据层级 | 现有证据 |
| --- | --- | --- |
| 多全宗、分类、动态字段/建表、字段布局、档案与案卷、关联、明细行、唯一校验、电子文件和基础审计 | 原始里程碑任务清单已勾选；关键子能力有独立测试，但无单一端到端全量验收 | `openspec/changes/add-archive-mvp-foundation/tasks.md`、`server/src/test/java/github/luckygc/am/module/archive/metadata/ArchiveMetadataServiceTests.java`、`server/src/test/java/github/luckygc/am/module/archive/metadata/service/ArchiveDynamicTableServiceTests.java`、`server/src/test/java/github/luckygc/am/module/archive/metadata/service/ArchiveFieldLayoutServiceTests.java`、`server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveVolumePermissionTests.java`、`server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveItemRelationServiceTests.java`、`server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveItemLineRowServiceTests.java`、`server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveNoUniquenessTests.java`、`server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveItemElectronicFileServiceTests.java`、`server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveItemAuditWriteTests.java` |
| 管理查询、全文发现、详情读取及上述读写/下载的功能权限与数据范围 | closure 任务清单已勾选；查询、范围、读取、文件下载与审计拒绝有直接服务测试 | `openspec/changes/add-archive-data-scope-permissions-mvp-closure/tasks.md`、`server/src/test/java/github/luckygc/am/module/archive/item/search/ArchiveFullTextSearchIntegrationTests.java`、`server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveItemDataScopeQueryTests.java`、`server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveItemElectronicFileServiceTests.java`、`server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveItemAuditSearchServiceTests.java` |
| 功能权限枚举/判断、数据范围定义/绑定/编译以及导入导出 | closure 任务清单已勾选；对应服务测试直接覆盖权限目录、授权、范围条件/主体及导入导出失败路径 | `server/src/test/java/github/luckygc/am/module/authorization/service/AuthorizationPermissionServiceTests.java`、`server/src/test/java/github/luckygc/am/module/archive/authorization/service/ArchiveDataScopeServiceTests.java`、`server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveItemImportExportServiceTests.java` |
| “基础/完整能力不作为 MVP 前置”的范围边界 | 规格决策证据，属需求分类而非运行时行为，不存在直接自动化测试 | `openspec/changes/add-archive-data-scope-permissions-mvp-closure/specs/archive-mvp-foundation/spec.md`、`openspec/changes/add-archive-data-scope-permissions-mvp-closure/design.md` |

### close-archive-pc-mvp

`archive-pc-mvp` 是阶段验收集合，不创建长期 capability；其中的实现证据继续由既有业务测试承载。下表区分直接测试、任务清单/实现证据和已识别的测试缺口；“里程碑要求不保留”只决定不创建 `archive-pc-mvp` 长期规格，不将任务勾选冒充为比实际更强的测试证据。唯一必须进入长期规格的业务 delta 是 `archive-record-search` 的 `volumeId` 固定筛选合同。

| delta capability | Requirement | 结论 | 对应规格或证据 |
| --- | --- | --- | --- |
| archive-pc-mvp | 档案列表游标分页闭环 | 里程碑要求不保留 | 服务端直接集成测试覆盖“用户排序+唯一兜底排序”和“101 条重复排序值连续翻页不重复不遗漏”：`server/src/test/java/github/luckygc/am/module/archive/item/search/ArchiveFullTextSearchIntegrationTests.java`；前端直接测试覆盖已提交查询、草稿隔离、前后翻页、刷新和游标失效恢复：`web/src/pages/archive-items/useArchiveItemSearch.test.ts`、`web/src/pages/archive-library/ArchiveLibraryPage.test.ts` |
| archive-pc-mvp | 档案生命周期操作 | 里程碑要求不保留 | 前端直接测试覆盖锁定原因、解锁/删除确认、成功刷新已提交查询与 ProblemDetail 原因保留：`web/src/pages/archive-items/useArchiveItemLifecycle.test.ts`；服务端成功写入/审计证据：`server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveItemAuditWriteTests.java`、`server/src/test/java/github/luckygc/am/module/archive/item/search/ArchiveFullTextSearchIntegrationTests.java`；统一 ProblemDetail 映射直接测试：`server/src/test/java/github/luckygc/am/infrastructure/web/GlobalExceptionHandlerTests.java`；当前未发现针对 `:lock`、`:unlock` 和档案 `DELETE` 逐动作覆盖“权限+数据范围+当前状态/并发拒绝”的直接服务测试，此处明确记为验收证据缺口，不以已勾选任务替代 |
| archive-pc-mvp | 档案完整字段编辑 | 里程碑要求不保留 | `web/src/pages/archive-items/ArchiveItemManagementPage.test.ts` |
| archive-pc-mvp | 案卷管理闭环 | 里程碑要求不保留 | `server/src/test/java/github/luckygc/am/module/archive/item/web/ArchiveVolumeControllerTests.java`、`web/src/pages/archive-volumes/ArchiveVolumesPage.test.ts` |
| archive-pc-mvp | 档案关系维护 | 里程碑要求不保留 | `server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveItemRelationServiceTests.java`、`web/src/pages/archive-items/ArchiveItemRelationsDrawer.test.ts` |
| archive-pc-mvp | 分类明细表定义 | 里程碑要求不保留 | `server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveItemLineTableServiceTests.java`、`web/src/pages/archive-categories/ArchiveLineTablePanel.test.ts` |
| archive-pc-mvp | 档案明细行数据 | 里程碑要求不保留 | `server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveItemLineRowServiceTests.java`、`web/src/pages/archive-items/ArchiveItemLineRows.test.ts` |
| archive-pc-mvp | 统一功能权限体验 | 里程碑要求不保留 | 权限 Store 的超级管理员、普通权限码、深只读快照、并发去重/过期失败关闭有直接测试：`web/src/stores/permissionStore.test.ts`；菜单分组、空 `children`、无权直达守卫与校验失败路由有直接测试：`web/src/app/routes.test.ts`；AppShell 的 60 秒/focus/visible 检查、五分钟 `validUntil`、单一在途请求、节流、到期计时器重排、后台恢复及卸载清理有直接测试：`web/src/layout/AppShell.test.ts`；授权管理单能力/能力变化有直接测试：`web/src/pages/authorization-management/AuthorizationManagementPage.test.ts`；服务端最终授权有代表性直接测试：`server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveItemDataScopeQueryTests.java`、`server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveVolumePermissionTests.java`、`server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveItemLineRowServiceTests.java`；这些是代表性服务边界证据，不声称单一测试覆盖全部受保护资源 |
| archive-pc-mvp | 真实工作台摘要 | 里程碑要求不保留 | `server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveWorkspaceServiceTests.java`、`server/src/test/java/github/luckygc/am/module/archive/item/web/ArchiveWorkspaceControllerTests.java` |
| archive-pc-mvp | 关键读取区域原位错误恢复 | 里程碑要求不保留 | `web/src/shared/components/RequestErrorState.test.ts`、`web/src/pages/archive-items/ArchiveItemManagementPage.error-state.test.ts` |
| archive-record-search | 档案条目高级查询 | 需要合并到当前规格 | 现有证据：`openspec/changes/close-archive-pc-mvp/specs/archive-record-search/spec.md`；已有目标：`openspec/specs/archive-record-search/spec.md`；补入 `volumeId` 固定筛选及 cursor 查询摘要语义 |

### unify-s3-file-storage

当前 `file-storage` 已直接体现统一 S3 兼容目标合同，因此归档时跳过规格写入，避免重复应用同一 delta。

| delta capability | Requirement | 结论 | 对应规格或证据 |
| --- | --- | --- | --- |
| file-storage | 文件元数据本地落库 | 当前规格已覆盖 | `openspec/specs/file-storage/spec.md` 的“文件元数据本地落库” |
| file-storage | 统一 S3 兼容存储 | 当前规格已覆盖 | `openspec/specs/file-storage/spec.md` 的“统一 S3 兼容存储” |
| file-storage | 文件存储路由（REMOVED） | 当前规格已覆盖 | `openspec/specs/file-storage/spec.md` 已删除该 Requirement，并在“统一 S3 兼容存储”中明确禁止本地目录和供应商 adapter |

## 后续归档守则

1. 先处理表中“需要合并到当前规格”的业务 delta，并在每次合并后运行 OpenSpec strict 校验。
2. `org_unit`、旧 `archive-record`、旧本地/供应商存储路由等历史语义不得因归档重新进入当前规格；后发生且已实现的合同优先。
3. 里程碑 capability 和纯工程治理 capability 使用跳过规格归档，长期事实分别由业务规格、测试、架构规则和文档承载。
4. `add-flowable-approval-workflow` 在 26 项任务全部完成前保持活动；本治理计划不实现或归档该 change。
