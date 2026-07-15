# OpenSpec 治理收口实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**目标：** 将已完成 change 收口为当前业务规格或历史记录，使活动区只保留真实进行中的 Flowable change，并为后续规格生成固化项目上下文。

**架构：** 先逐项校准 change delta 与当前规格、代码和测试，再使用 OpenSpec 标准归档命令。业务能力正常合并规格；里程碑、工程治理和已经人工合并的 change 使用 `--skip-specs`，避免制造伪 capability 或重复要求。

**技术栈：** OpenSpec CLI、Markdown、Git、现有 Java/Vue 测试与项目文档。

## 全局约束

- 始终使用中文编写 OpenSpec 制品和说明。
- 不实现 `add-flowable-approval-workflow` 的 21 个未完成任务。
- 不修改业务状态机、权限语义和项目自有 HTTP API 合同。
- 不机械合并与当前实现冲突的历史 delta；先以代码、测试和已确认设计校准。
- 工程、工具链、里程碑和纯文档 change 使用 `openspec archive --skip-specs`。
- 每归档一个 change 后立即运行严格校验并单独提交，禁止一次归档全部 change 后再排错。
- 保留用户未跟踪的 `target/`，不得加入提交。

---

### 任务 1： 建立归档基线与分类清单

**文件：**
- 新建： `docs/superpowers/plans/2026-07-15-openspec-change-classification.md`
- 阅读：每个活动 change 的 `proposal.md`、`design.md`、`tasks.md`，以及 `find openspec/changes -path '*/specs/*/spec.md'` 返回的 delta 文件
- 阅读：`find openspec/specs -mindepth 2 -maxdepth 2 -name spec.md` 返回的六份当前规格

**接口：**
- 输入： 当前 15 个 change、6 个当前规格和 `openspec list --json`。
- 输出： 每个已完成 change 的归档方式、规格去向和核对证据。

- [ ] **步骤 1： 记录严格校验和任务基线**

运行：

```bash
openspec list --json
openspec validate --all --strict --json --no-interactive
for d in openspec/changes/*; do
    printf '%s ' "$(basename "$d")"
    rg -c '^[-*] \[[ xX]\]' "$d/tasks.md"
    rg -c '^[-*] \[ \]' "$d/tasks.md" || true
done
```

预期： 21 个条目全部通过；除 `add-flowable-approval-workflow` 外，其余 change 未完成任务数均为 0。

- [ ] **步骤 2： 写入固定分类表**

使用下表创建分类文档：

```markdown
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
```

- [ ] **步骤 3： 核对混合 change 的每条 requirement**

运行：

```bash
for change in \
    add-archive-data-scope-permissions-mvp-closure \
    close-archive-pc-mvp \
    unify-s3-file-storage; do
    openspec show "$change" --type change --json
done
```

对每条 delta requirement，在分类文档中写入以下精确结论之一：`当前规格已覆盖`、`需要合并到当前规格`、`里程碑要求不保留`。每条结论后写对应当前规格路径或测试类路径。

- [ ] **步骤 4： 提交分类清单**

```bash
git add docs/superpowers/plans/2026-07-15-openspec-change-classification.md
git commit -m "docs: 分类 OpenSpec 历史变更"
```

### 任务 2： 归档纯业务 capability change

**文件：**
- 修改或新建： `openspec/specs/archive-metadata/spec.md`
- 修改或新建： `openspec/specs/archive-record-routing/spec.md`
- 修改或新建： `openspec/specs/archive-record-search/spec.md`
- 修改或新建： `openspec/specs/login-authentication/spec.md`
- 修改或新建： `openspec/specs/archive-data-scope/spec.md`
- 修改或新建： `openspec/specs/organization-departments/spec.md`
- 修改或新建： `openspec/specs/file-preview-service/spec.md`
- 修改或新建： `openspec/specs/archive-governance-scheme/spec.md`
- 修改或新建： `openspec/specs/archive-local-rule-engine/spec.md`
- 修改或新建： `openspec/specs/archive-ontology-core/spec.md`
- 修改或新建： `openspec/specs/archive-classification-scheme/spec.md`
- 修改或新建： `openspec/specs/archive-governance-workbench/spec.md`
- 由 OpenSpec 移动：将本任务命令中列出的九个业务 change 目录移入 OpenSpec 归档目录

**接口：**
- 输入：任务 1 分类清单。
- 输出： 当前业务 capability 规格和标准归档记录。

- [ ] **步骤 1： 按依赖顺序归档基础业务 change**

按以下顺序逐条运行命令：

```bash
openspec archive add-archive-metadata-routing -y
openspec validate --all --strict --no-interactive
git diff --check
git add openspec
git commit -m "docs: 归档档案元数据路由变更"

openspec archive split-archive-item-volume-and-relations -y
openspec validate --all --strict --no-interactive
git diff --check
git add openspec
git commit -m "docs: 归档条目案卷关系拆分变更"

openspec archive add-auth-session-audit -y
openspec validate --all --strict --no-interactive
git diff --check
git add openspec
git commit -m "docs: 归档认证会话审计变更"
```

每次归档后的预期：严格校验通过，且只有该 change 及其影响的当前规格发生移动或修改。

- [ ] **步骤 2： 归档组织、预览和治理 capability**

每个 change 分别执行归档、校验和提交：

```bash
openspec archive add-organization-departments -y
openspec validate --all --strict --no-interactive
git add openspec && git commit -m "docs: 归档组织部门变更"

openspec archive add-preview-service -y
openspec validate --all --strict --no-interactive
git add openspec && git commit -m "docs: 归档文件预览服务变更"

openspec archive add-archive-governance-foundation -y
openspec validate --all --strict --no-interactive
git add openspec && git commit -m "docs: 归档档案治理基础变更"

openspec archive add-fonds-classification-adoption -y
openspec validate --all --strict --no-interactive
git add openspec && git commit -m "docs: 归档全宗分类方案变更"

openspec archive improve-archive-governance-workbench -y
openspec validate --all --strict --no-interactive
git add openspec && git commit -m "docs: 归档档案治理工作台变更"

openspec archive add-archive-record-editing-layout -y
openspec validate --all --strict --no-interactive
git add openspec && git commit -m "docs: 归档档案编辑布局变更"
```

预期： 新 capability 进入 `openspec/specs/`，历史 change 离开活动目录。

### 任务 3： 合并混合 change 的长期业务 delta

**文件：**
- 修改： `openspec/specs/archive-metadata/spec.md`
- 修改： `openspec/specs/archive-record-search/spec.md`
- 修改： `openspec/specs/archive-data-scope/spec.md`
- 新建： `openspec/specs/authorization-permissions/spec.md`
- 新建： `openspec/specs/archive-import-export/spec.md`
- 阅读： `openspec/changes/add-archive-data-scope-permissions-mvp-closure/specs/archive-data-scope/spec.md`
- 阅读： `openspec/changes/add-archive-data-scope-permissions-mvp-closure/specs/authorization-permissions/spec.md`
- 阅读： `openspec/changes/add-archive-data-scope-permissions-mvp-closure/specs/archive-import-export/spec.md`
- 阅读： `openspec/changes/add-archive-data-scope-permissions-mvp-closure/specs/archive-metadata/spec.md`
- 阅读： `openspec/changes/add-archive-data-scope-permissions-mvp-closure/specs/archive-record-search/spec.md`
- 阅读： `openspec/changes/close-archive-pc-mvp/specs/archive-record-search/spec.md`

**接口：**
- 输入：任务 1 对每条 requirement 的分类结论。
- 输出： 不包含 MVP 里程碑语句的长期业务规格。

- [ ] **步骤 1： 合并数据范围、权限和导入导出规格**

对 closure change 下每个 `## ADDED Requirements`，将完整的 `### Requirement` 块复制到匹配的当前 capability。对 `## MODIFIED Requirements`，使用完整修改块替换当前 requirement。不得创建 `openspec/specs/archive-mvp-foundation/`。

- [ ] **步骤 2： 合并 PC 收口中的搜索合同**

只应用 `close-archive-pc-mvp` 的 `archive-record-search` delta，不得创建 `openspec/specs/archive-pc-mvp/`。

- [ ] **步骤 3： 验证业务规格**

```bash
openspec validate --specs --strict --json --no-interactive
rg -n 'archive-mvp-foundation|archive-pc-mvp' openspec/specs && exit 1 || true
```

预期：全部规格通过；第二条命令不输出内容。

- [ ] **步骤 4： 提交长期业务规格**

```bash
git add openspec/specs
git commit -m "docs: 收口档案权限与 PC 业务规格"
```

### 任务 4： 跳过规格归档里程碑和已合并 change

**文件：**
- 由 OpenSpec 移动： five completed change directories to archive

**接口：**
- 输入：任务 1–3 的分类和已合并规格。
- 输出： 不污染当前 capability 的历史归档。

- [ ] **步骤 1： 归档五个 change**

逐个运行并分别提交：

```bash
openspec archive add-archive-mvp-foundation --skip-specs -y
openspec validate --all --strict --no-interactive
git add openspec && git commit -m "docs: 归档档案 MVP 里程碑"

openspec archive add-archive-data-scope-permissions-mvp-closure --skip-specs -y
openspec validate --all --strict --no-interactive
git add openspec && git commit -m "docs: 归档档案权限 MVP 收口"

openspec archive close-archive-pc-mvp --skip-specs -y
openspec validate --all --strict --no-interactive
git add openspec && git commit -m "docs: 归档档案 PC MVP 收口"

openspec archive simplify-code-and-enforce-bean-boundaries --skip-specs -y
openspec validate --all --strict --no-interactive
git add openspec && git commit -m "docs: 归档代码边界治理变更"

openspec archive unify-s3-file-storage --skip-specs -y
openspec validate --all --strict --no-interactive
git add openspec && git commit -m "docs: 归档 S3 存储统一变更"
```

- [ ] **步骤 2： 确认活动区只剩 Flowable**

```bash
find openspec/changes -mindepth 1 -maxdepth 1 -type d ! -name archive -printf '%f\n'
```

预期：

```text
add-flowable-approval-workflow
```

### 任务 5： 固化 OpenSpec 项目上下文与索引

**文件：**
- 修改： `openspec/config.yaml`
- 修改： `openspec/README.md`

**接口：**
- 输入： 收口后的当前规格目录和唯一活动 change。
- 输出： OpenSpec 制品规则与不易过期的入口文档。

- [ ] **步骤 1： 写入 OpenSpec context**

在 `context: |` 中写入以下明确事实：PC 前端使用 Vue 3、TypeScript 和 Element Plus；后端是 Spring Boot 模块化单体；数据库使用 PostgreSQL；固定实体使用 Jakarta Data；动态表和复杂 SQL 使用 MyBatis；OpenSpec 制品和注释使用中文；业务/API 合同位于 `openspec/specs/`。

- [ ] **步骤 2： 写入 artifact rules**

增加制品规则：proposal 必须包含目标、非目标和受影响真相源；design 必须包含决策、替代方案、失败路径和迁移；spec 只承载可测试的业务/API 要求；tasks 必须包含精确验证命令。

- [ ] **步骤 3：精简 README 的 change 章节**

将手工维护的 change 表替换为：

````markdown
## 当前 change

活动 change 以 CLI 输出为准：

```bash
openspec list
```

`changes/` 只保留真实进行中的变更；任务全部完成后必须校准规格并及时归档。
````

- [ ] **步骤 4： 验证并提交**

```bash
openspec list --json
openspec validate --all --strict --no-interactive
git diff --check
git add openspec/config.yaml openspec/README.md
git commit -m "docs: 固化 OpenSpec 项目治理规则"
```

预期：只有 Flowable change 处于活动状态，且每份当前规格均通过校验。
