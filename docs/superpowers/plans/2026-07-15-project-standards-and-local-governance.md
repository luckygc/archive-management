# 项目规范与本地治理门禁实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**目标：** 将项目规范收敛为“单一真相源 + 薄路由 + 可执行门禁”，消除产品、设计、架构、持久化和运行命令之间的冲突，并在本地阻止已完成 change 未归档、规格索引漂移和历史资料冒充当前规范。

**架构：** OpenSpec 承载业务与 API 合同，`PRODUCT.md`/`DESIGN.md` 承载产品与视觉规则，`docs/architecture.md` 承载稳定技术边界，Taskfile 与构建配置承载命令。`AGENTS.md` 只做协作原则和真相源路由；项目技能只保留任务决策流程并链接当前规范。一个有测试的 Perl 脚本完成仓库结构检查，`task governance-check` 组合 OpenSpec 严格校验和本地检查，不引入 GitHub Actions。

**技术栈：** Markdown、OpenSpec CLI、Taskfile、Perl 5/Test::More、Git、现有 Maven 与 Vite+ 验证命令。

## 依赖与约束

- 先完成 `2026-07-15-openspec-governance-closure.md`，否则活动 change 和规格索引的期望状态尚未稳定。
- Repository 与审计最终措辞必须匹配 `2026-07-15-repository-and-auditing-unification.md` 的实际实现。
- 不修改项目自有 HTTP API、业务状态机、权限边界、Flowable 未完成任务或前端业务代码。
- 不新增 GitHub Actions、其他 CI 平台配置、文档生成器或新的脚本运行时。
- 历史计划和设计只增加统一免责声明，不逐份重写，也不让治理脚本扫描其过时技术内容。
- 文档中的命令必须来自 `Taskfile.yml`、`package.json` 或现有构建配置；不得复制一套不执行的命令清单。
- 保留用户未跟踪的 `target/`，不得加入提交。

---

### 任务 1： 先用测试定义本地治理检查器

**文件：**
- 新建： `scripts/governance-check.t`
- 阅读：任务 2 定义的 `scripts/governance-check.pl` CLI 合同

**接口：**
- CLI：`perl scripts/governance-check.pl [repository-root]`。
- 退出码 0：全部结构治理检查通过。
- 退出码 1：将所有违规打印到 stderr 后统一失败。
- 检查项：已完成的活动 change、当前规格索引一致性、历史资料声明。

- [ ] **步骤 1： 为完成但未归档的 change 写失败测试**

创建包含以下内容的临时夹具：

```text
openspec/changes/completed/tasks.md  # 只有 [x]
openspec/changes/active/tasks.md     # 至少一个 [ ]
```

断言脚本只报告 `completed`，消息为 `任务已全部完成但仍位于活动 change`，并以退出码 1 结束。

- [ ] **步骤 2： 为规格索引漂移写失败测试**

在 `openspec/specs/` 下创建 `alpha/spec.md` 和 `beta/spec.md` 两个夹具，但在 `openspec/README.md` 中只列出反引号包裹的 `specs/alpha/spec.md`，并额外列出不存在的 `specs/stale/spec.md`。断言缺失项和过期项都会报告，只有顺序不同时不会失败。

- [ ] **步骤 3： 为历史资料声明写失败测试**

断言 `docs/superpowers/README.md` 必须存在并包含精确短语 `历史资料，非当前规范`。覆盖文件缺失、措辞错误和措辞正确三种情况。

- [ ] **步骤 4： 运行红灯**

```bash
prove -v scripts/governance-check.t
```

预期：由于 `scripts/governance-check.pl` 不存在，测试失败。

### 任务 2： 实现治理检查器并接入 Taskfile

**文件：**
- 新建： `scripts/governance-check.pl`
- 修改： `Taskfile.yml`
- 新建： `docs/superpowers/README.md`
- 修改： `docs/README.md`

**接口：**
- Task：`task governance-check`。
- 命令顺序：OpenSpec 严格校验、Perl 测试套件、仓库治理扫描。

- [ ] **步骤 1： 实现最小 Perl 检查器**

只使用 `scripts/source-lines.pl` 和 `scripts/source-lines.t` 已采用的 Perl 核心模块。脚本必须：

1. 检查 `openspec/changes/` 下的直接子目录，存在 `archive` 时排除它；
2. 仅当 `tasks.md` 至少包含一个复选框，且不存在未完成的 `- [ ]` 或 `* [ ]` 时，才判定 change 已完成；
3. 比较排序后的实际 `openspec/specs/*/spec.md` 集合与 `openspec/README.md` 中反引号包裹的 `specs/*/spec.md` 路径；
4. 要求 `docs/superpowers/README.md` 包含 `历史资料，非当前规范`；
5. 退出前收集全部错误，使一次运行暴露所有漂移。

- [ ] **步骤 2： 写入历史资料说明**

创建 `docs/superpowers/README.md` 并写入以下明确规则：

- 本目录是历史资料，非当前规范；
- `specs/` 记录特定时间点已批准的设计决策；
- `plans/` 记录特定时间点的执行计划；
- 当前业务/API 合同位于 `openspec/specs/` 和活动 change delta；
- 当前架构和命令位于路由指向的真相源；
- 实现演进时不追溯重写历史文件。

从 `docs/README.md` 链接该声明。

- [ ] **步骤 3： 接入 Taskfile**

增加：

```yaml
governance-check:
    desc: 检查 OpenSpec 与项目规范治理状态
    cmds:
        - openspec validate --all --strict --no-interactive
        - prove -v scripts/governance-check.t
        - perl scripts/governance-check.pl
```

不得增加 GitHub workflow，也不得在 package scripts 中复制这组命令。

- [ ] **步骤 4： 运行绿灯并提交**

```bash
prove -v scripts/governance-check.t
task governance-check
git diff --check
git add scripts/governance-check.pl scripts/governance-check.t \
  Taskfile.yml docs/superpowers/README.md docs/README.md
git commit -m "feat: 增加本地项目治理检查"
```

预期：测试和任务在完成归档的仓库上均以退出码 0 结束。

### 任务 3： 将 AGENTS.md 收敛为薄路由

**文件：**
- 修改： `AGENTS.md`

**接口：**
- Routes by change type to truth sources.
- 保留协作安全边界和最小验证要求。
- Removes duplicated framework/API/persistence implementation manuals.

- [ ] **步骤 1： 保留仓库级总原则**

Keep concise rules for:

- 始终中文交流、文档和注释；
- 最小闭环、奥卡姆剃刀、维护成本、失败路径和业务不变量；
- 保护用户工作树，不处理无关改动；
- 不主动启动长期占用端口的开发服务；
- 不把受商业限制的 Enterprise/Pro 能力当作默认基础能力。

- [ ] **步骤 2： 用路由表替代详细规则复制**

增加一张包含以下映射的表：

| 改动类型 | 必读真相源/技能 |
| --- | --- |
| 业务、状态机、权限、验收 | 对应 `openspec/specs/` 与活动 change |
| 项目自有 API | `openspec/specs/api-contract/spec.md` + `archive-api-design-strategy` |
| 产品定位 | `PRODUCT.md` |
| 前端界面 | `PRODUCT.md`、`DESIGN.md` + `impeccable` |
| 稳定架构和包边界 | `docs/architecture.md` + ArchUnit |
| 持久化、实体、Repository、Mapper、审计 | `docs/architecture.md` + `archive-persistence-strategy` |
| 开发、验证、部署、运维 | Taskfile/build config + matching `docs/` |
| OpenSpec 变更 | `openspec/config.yaml` 和对应 OpenSpec 工作流技能 |

- [ ] **步骤 3： 保留不可机械表达的强边界**

只保留直接影响自动化贡献者的简洁边界：包归属、模块依赖方向、单实现具体 Bean、禁止同 Bean public 自调用、项目 SQL 只面向 PostgreSQL、禁止硬编码 `public` schema、JSpecify 默认规则、Spotless 真相源和 Vite+ 禁止主动启动开发服务。

从 `AGENTS.md` 移出详细 DTO 示例、分页字段、缓存 provider 矩阵、Flowable 参数、Repository 方法配方、审计实现细节和第三方表清单；这些内容已有其他真相源。

- [ ] **步骤 4： 检查体量和路由完整性**

```bash
wc -l AGENTS.md
rg -n 'api-contract|PRODUCT\.md|DESIGN\.md|architecture\.md|archive-persistence-strategy|governance-check' AGENTS.md
rg -n 'CrudRepository|CreationTimestamp|UpdateTimestamp|flowable\.|spring\.cache\.|SPRING_SESSION|QRTZ_' AGENTS.md && exit 1 || true
```

预期：路由搜索覆盖所有真相源类别；实现细节搜索不输出内容。结果文件应明显短于原文件，但不设置武断的行数门禁。

- [ ] **步骤 5： 提交薄路由**

```bash
git diff --check
git add AGENTS.md
git commit -m "docs: 收敛仓库协作规则为薄路由"
```

### 任务 4： 校准产品、设计和工程文档

**文件：**
- 修改： `PRODUCT.md`
- 修改： `DESIGN.md`
- 修改： `README.md`
- 修改： `CONTRIBUTING.md`
- 修改： `docs/README.md`
- 修改： `docs/architecture.md`
- 修改： `docs/development.md`
- 修改： `docs/deployment.md`
- 修改： `docs/api.md`
- 修改： `docs/security.md`

**接口：**
- 产品：只保留用户、用途、产品界面和产品原则。
- 设计：只保留 Element Plus 设计系统。
- Architecture: stable technical boundaries, not page implementation snapshots.
- 运维：可执行命令和仅 S3 存储。

- [ ] **步骤 1： 精简 PRODUCT.md**

保留产品定位、用户群体、产品界面、用途、性格和原则。删除构建/部署行为，以及把技术部署差异描述为通过“本地配置”处理的表述。链接 `DESIGN.md`，不重复详细组件样式。

- [ ] **步骤 2： 清除 DESIGN.md 的 Ant Design 残留**

将 `.ant-*` 表述替换为禁止覆盖 Element Plus 内部 DOM 类的框架中立规则。确认所有组件名和 CSS token 示例使用 Element Plus 术语。本任务不修改前端源码。

运行：

```bash
rg -n 'Ant Design|\.ant-' PRODUCT.md DESIGN.md
```

预期：无匹配项。

- [ ] **步骤 3： 收敛架构文档**

在 `docs/architecture.md` 中：

- 保留顶层组件、模块/包方向、持久化选型、运行时框架和外部边界；
- 记录直接且窄的 Jakarta Data Repository，以及 Hibernate/MyBatis 拦截器共享的审计上下文；
- 删除轮询间隔、具体 composable 名称、页面请求状态和草稿 API 合同细节；
- 说明实际默认缓存为 Caffeine，并将 `application.yaml` 链接为运行时配置真相源；
- 说明文件内容只使用 S3 兼容存储。

- [ ] **步骤 4： 校准开发、部署和贡献命令**

在 `docs/development.md` 中删除机器专属路径 `D:\dev\archive-management`，改为仓库相对命令。在 `docs/deployment.md` 中删除本地目录存储 adapter/降级表述，只保留外部 S3 兼容存储。在 `CONTRIBUTING.md` 中保留贡献流程，要求运行 `task governance-check` 和对应范围的构建/测试任务；通过链接 `AGENTS.md` 引用规则，不再复制。

更新根 README 和 docs README，使命令和真相源链接与修订后的文件一致。

- [ ] **步骤 5： 执行冲突扫描**

```bash
rg -n 'Ant Design|\.ant-|本地目录存储|local storage adapter|spring\.cache\.type=none|默认.*NoOpCacheManager|D:\\dev\\archive-management' \
  PRODUCT.md DESIGN.md README.md CONTRIBUTING.md docs AGENTS.md \
  --glob '!docs/superpowers/**'
```

预期：不存在过期规范表述。“本地开发”和“本地规则”等合法短语不匹配这些精确模式，保持不变。

- [ ] **步骤 6： 提交当前说明文档**

```bash
git diff --check
git add PRODUCT.md DESIGN.md README.md CONTRIBUTING.md docs
git commit -m "docs: 校准产品设计与工程真相源"
```

### 任务 5： 将项目技能改为决策流程而非第二套规范

**文件：**
- 修改： `.codex/skills/archive-persistence-strategy/SKILL.md`
- 修改： `.codex/skills/archive-persistence-strategy/references/jakarta-data.md`
- 修改： `.codex/skills/archive-persistence-strategy/references/jakarta-data-repository-features.md`
- 修改： `.codex/skills/archive-persistence-strategy/references/hibernate-auditing.md`
- 修改： `.codex/skills/archive-api-design-strategy/SKILL.md`

**接口：**
- 持久化技能：先在 Jakarta Data 与 MyBatis 之间选择，再路由到当前项目边界。
- API skill: review workflow around `api-contract`, not a copied API manual.

- [ ] **步骤 1： 修正持久化技能的 Repository 合同**

删除所有继承 `CrudRepository`、`BasicRepository`、Jakarta `DataRepository` 或项目基类的建议。技能必须要求：

- direct `@Repository` annotation;
- 只声明当前用例需要的方法；
- 使用具体实体签名和显式操作注解；
- 不使用 `save`、upsert 或 query-by-method-name 合同；
- 固定表使用 Jakarta Data，动态标识符、复杂 SQL、批处理和报表路径使用 MyBatis。

同步更新所有代码示例。

- [ ] **步骤 2： 修正审计参考**

将 `AuditContextProvider` 记录为唯一时间/用户来源；固定实体使用 Hibernate 拦截；MyBatis 注入 `_audit`；未认证用户 ID 可空；业务操作人显式传入。删除允许保留 `@CreationTimestamp`/`@UpdateTimestamp` 或要求 Service 预填通用审计字段的建议。

- [ ] **步骤 3： 压缩 API 技能**

将 `openspec/specs/api-contract/spec.md` 设为明确真相源，只保留：

1. 技能触发时机；
2. 需要共同检查的文件/合同；
3. 简短的资源、custom method、长耗时操作判定流程；
4. 覆盖 spec、Controller、DTO、前端类型/客户端和测试的审查清单；
5. 第三方协议隔离。

删除已存在于 OpenSpec 的分页默认值、错误体示例和其他重复细节。

- [ ] **步骤 4： 扫描技能冲突并提交**

```bash
rg -n 'extends (CrudRepository|BasicRepository|DataRepository)|@CreationTimestamp|@UpdateTimestamp|Service.*审计.*赋值' \
  .codex/skills/archive-persistence-strategy && exit 1 || true
rg -n 'openspec/specs/api-contract/spec.md' .codex/skills/archive-api-design-strategy/SKILL.md
git diff --check
git add .codex/skills/archive-persistence-strategy .codex/skills/archive-api-design-strategy
git commit -m "docs: 校准项目 API 与持久化技能"
```

预期：第一次搜索不输出内容；API 技能指向当前合同。

### 任务 6： 完成治理闭环验证

**文件：**
- 阅读并验证：任务 1–5 修改的文件；最终验证期间不得引入新的改动范围。

**接口：**
- 验证：当前 OpenSpec、本地治理、文档/技能一致性、后端实现，以及没有新增 CI。

- [ ] **步骤 1： 运行规范与文档检查**

```bash
task governance-check
git diff --check
find . -path './.git' -prune -o -path './.github/workflows/*' -type f -print
```

预期：治理和空白检查通过；workflow 搜索不输出本次变更新增的文件。

- [ ] **步骤 2： 运行后端闭环**

```bash
task server-format-check
task server-compile
task server-test
```

预期：所有命令退出码为 0。如果集成测试依赖不可用的 Docker/PostgreSQL，记录准确失败信息，不得描述为全量通过。

- [ ] **步骤 3： 核对前端未被改动**

```bash
git diff --name-only HEAD~4..HEAD -- web frontend-core
```

预期：无输出。由于本计划只修改前端相关文档，不要求运行 `pnpm check` 或 `pnpm test`。

- [ ] **步骤 4： 最终真相源审查**

人工验证每个类别的一条代表性规则只有一个 owner，其他位置只提供链接：

| 类别 | 真相源 |
| --- | --- |
| API pagination/error/ID | `openspec/specs/api-contract/spec.md` |
| fixed-vs-dynamic persistence | `docs/architecture.md` |
| Repository implementation workflow | persistence skill + ArchUnit |
| product positioning | `PRODUCT.md` |
| UI visual system | `DESIGN.md` |
| 命令 | `Taskfile.yml` 和构建配置 |
| 历史决策 | `docs/superpowers/README.md` 声明 |

- [ ] **步骤 5： 记录最终状态**

```bash
git status --short
git log --oneline -12
```

预期：没有非预期的已跟踪改动；用户原有的未跟踪 `target/` 保持不变。本计划不创建 GitHub Actions workflow，也不向远端发布改动。
