# 生产源码职责治理审查整改实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复生产源码行数门禁、统计准确性、后端纯转发边界、前端测试缺口和治理文档漂移。

**Architecture:** 行数检查采用软提示线与硬失败线双阈值，并用 Perl 契约测试锁定词法状态。元数据保留真实协调 Service，本体 Controller 直接依赖对象属性 Service 与关系事件 Service；前端只补关键跨组件行为测试，不改变页面合同。

**Tech Stack:** Perl 5、Test::More、Java 25、Spring Boot、JUnit 5、Vue 3、TypeScript、Element Plus、Vitest、Testing Library、Vite+。

## 全局约束

- 前端软提示线 300 行、硬失败线 500 行。
- 后端软提示线 500 行、硬失败线 700 行。
- 不修改 HTTP API、数据库结构、权限、事务、错误响应或页面交互合同。
- 不新增兼容门面、配置开关、第三方依赖或通用统计框架。
- Java 代码最终通过 Spotless AOSP 格式化检查。
- 前端继续使用 Element Plus 和现有 Testing Library 测试方式。

---

### 任务 1：修复生产源码行数门禁

**文件：**
- 创建：`scripts/source-lines.t`
- 修改：`scripts/source-lines.pl`
- 修改：`package.json`

**接口：**
- 输入：`perl scripts/source-lines.pl [--report] [扫描目录...]`
- 输出：超过软提示线的 `行数<TAB>文件路径`。
- 状态：默认仅在超过硬失败线时非零；`--report` 始终成功。

- [ ] **步骤 1：编写失败的阈值与跨行字符串契约测试**

使用 `Test::More`、`File::Temp` 和 `IPC::Open3` 创建临时 `web/src` 与 `server/src/main/java`，覆盖：

```perl
subtest 'soft limit reports without failing' => sub {
    write_lines("$root/web/src/Soft.ts", 301);
    my ($status, $output) = run_script("$root/web/src");
    is($status, 0, 'soft limit does not fail');
    like($output, qr/^301\t/m, 'soft limit is reported');
};

subtest 'hard limit fails unless report-only' => sub {
    write_lines("$root/web/src/Hard.ts", 501);
    is((run_script("$root/web/src"))[0], 1, 'hard limit fails');
    is((run_script('--report', "$root/web/src"))[0], 0, 'report-only succeeds');
};

subtest 'comment markers inside multiline literals are code' => sub {
    write_file("$root/web/src/Literal.ts", "const value = `\n/* literal */\n// literal\n`;\nconst after = 1;\n");
    my (undef, $output) = run_script('--report', "$root/web/src");
    like($output, qr/^5\t/m, 'template literal lines remain counted');
};
```

- [ ] **步骤 2：运行测试并确认失败**

运行：`prove -v scripts/source-lines.t`

预期：旧脚本不接受显式目录、301 行返回失败、跨行模板字符串统计错误。

- [ ] **步骤 3：实现双阈值和文件级词法状态**

在 `effective_lines` 中持久传递字符串状态；识别反引号模板字符串与 Java `"""` 文本块。将阈值收敛为：

```perl
my ($soft_limit, $hard_limit) =
    $file =~ m{(?:^|/)(?:web|frontend-core)(?:/|$)} ? (300, 500) : (500, 700);
```

位置参数存在时扫描指定目录，否则扫描默认生产源码目录。输出超过软提示线的结果，只把超过硬失败线的结果加入 `@violations`。

- [ ] **步骤 4：把契约测试接入行数检查命令**

将根脚本改为：

```json
"check:source-lines": "prove scripts/source-lines.t && perl scripts/source-lines.pl"
```

- [ ] **步骤 5：运行测试和真实仓库检查**

运行：

```bash
prove -v scripts/source-lines.t
pnpm run check:source-lines
```

预期：契约测试通过；真实仓库可报告软提示文件但退出 0。

### 任务 2：删除本体关系与事件纯转发路径

**文件：**
- 创建：`server/src/test/java/github/luckygc/am/module/archive/ontology/service/ArchiveOntologyServiceBoundaryTests.java`
- 修改：`server/src/main/java/github/luckygc/am/module/archive/ontology/service/ArchiveOntologyService.java`
- 修改：`server/src/main/java/github/luckygc/am/module/archive/ontology/web/ArchiveOntologyController.java`
- 修改：`server/src/test/java/github/luckygc/am/module/archive/ontology/service/ArchiveOntologyServiceTests.java`

**接口：**
- `ArchiveOntologyService` 只提供对象、属性和映射用例。
- `ArchiveOntologyRelationService` 直接提供关系和事件用例。
- HTTP 路径、请求、响应和权限校验保持不变。

- [ ] **步骤 1：编写失败的边界测试**

```java
@Test
@DisplayName("对象属性入口不转发关系和事件用例")
void objectAttributeEntryShouldNotForwardRelationUseCases() {
    assertThat(
                    Arrays.stream(ArchiveOntologyService.class.getDeclaredMethods())
                            .map(Method::getName))
            .doesNotContain(
                    "listRelationTypes",
                    "createRelationType",
                    "updateRelationType",
                    "deleteRelationType",
                    "listEventTypes",
                    "initializeBuiltInEventTypes",
                    "createEventType",
                    "updateEventType",
                    "deleteEventType");
}
```

- [ ] **步骤 2：运行测试并确认失败**

运行：

```bash
cd server
mise exec -- mvn -q -Dtest=ArchiveOntologyServiceBoundaryTests test
```

预期：断言列出的转发方法仍存在。

- [ ] **步骤 3：让 Controller 直接依赖关系事件 Service**

给 `ArchiveOntologyController` 增加 `ArchiveOntologyRelationService` 构造器依赖；关系和事件端点改为调用该 Service。删除 `ArchiveOntologyService` 的 `relationService` 字段、构造器参数和九个转发方法。

- [ ] **步骤 4：同步现有单元测试构造器并格式化**

删除 `ArchiveOntologyServiceTests` 中传入的 `ArchiveOntologyRelationService` mock；运行 `mise exec -- mvn -q spotless:apply`。

- [ ] **步骤 5：运行聚焦测试**

运行：

```bash
mise exec -- mvn -q -Dtest=ArchiveOntologyServiceBoundaryTests,ArchiveOntologyServiceTests,ArchitectureRulesTest test
```

预期：全部通过。

### 任务 3：补齐前端拆分边界测试

**文件：**
- 创建：`web/src/pages/archive-items/ArchiveItemComponents.test.ts`
- 修改：`web/src/pages/archive-categories/ArchiveCategoriesPage.test.ts`
- 修改：`web/src/pages/archive-governance/ArchiveGovernanceFoundationPages.test.ts`

**接口：**
- 仅测试已有 props、事件和公开 `show()` 合同，不导出新的测试专用 API。

- [ ] **步骤 1：增加档案动作与编辑抽屉测试**

测试 `ArchiveItemActions` 的 `onImportFile` 收到选择的 `.xlsx` 文件且 input 被清空；测试 `ArchiveItemEditorDrawer` 在必填全宗为空时不触发 `onSave`，填写后触发一次。

- [ ] **步骤 2：增加分类范围保存测试**

在现有页面测试中打开“全宗可用分类”，验证调用 `listArchiveFondsCategoryScopes("F001")`；点击确定后验证 `saveArchiveFondsCategoryScopes("F001", [])` 并显示成功反馈。

- [ ] **步骤 3：增加治理或本体语义事件测试**

在现有治理测试中挂载拆出的业务组件，点击新建或保存入口，验证父层使用的语义事件被发出，不断言 Element Plus 内部 DOM。

- [ ] **步骤 4：运行聚焦测试**

运行：

```bash
pnpm --filter @archive-management/web exec vp test run src/pages/archive-items/ArchiveItemComponents.test.ts src/pages/archive-categories/ArchiveCategoriesPage.test.ts src/pages/archive-governance/ArchiveGovernanceFoundationPages.test.ts
```

预期：全部通过。

### 任务 4：同步职责治理文档

**文件：**
- 修改：`docs/superpowers/specs/2026-07-14-production-source-size-governance-design.md`
- 修改：`docs/superpowers/plans/2026-07-14-production-source-size-governance.md`
- 修改：`docs/superpowers/specs/2026-07-14-source-size-governance-review-remediation-design.md`
- 修改：`docs/superpowers/plans/2026-07-14-source-size-governance-review-remediation.md`

**接口：**
- 文档统一使用“前端 300/500、后端 500/700”软硬阈值表述。
- 文档记录 `ArchiveMetadataService` 的最终保留职责。

- [x] **步骤 1：更新设计文档**

把单一目标改为软提示与硬失败；说明元数据协调 Service 保留字段、布局、动态表和唯一约束事务边界；本体关系与事件由 Controller 直接依赖专项 Service。

- [x] **步骤 2：更新原实施计划**

将 `ArchiveClassificationService` 改为 `ArchiveMetadataReferenceService`，把删除 `ArchiveMetadataService` 改为收敛其职责；勾选已经完成且能由仓库状态验证的步骤，保留未执行步骤为未完成。

- [x] **步骤 3：记录整改验证结果**

在整改设计与计划中写入最终命令和结果，不写无法复现的完成声明。

### 任务 5：完整验证

**文件：**
- 不新增生产文件。

- [x] **步骤 1：运行前端完整验证**

```bash
pnpm check
pnpm test
pnpm build
pnpm run check:source-lines
```

- [x] **步骤 2：运行后端完整验证**

```bash
cd server
mise exec -- mvn -q spotless:check test
```

- [x] **步骤 3：检查补丁与工作区**

```bash
git diff --check
git status --short
```

若构建只改写生成的 `web/src/components.d.ts` 格式，则恢复该验证副作用；不得还原用户原有改动。

## 执行结果（2026-07-14）

- `prove scripts/source-lines.t`：5 项契约测试全部通过。
- `pnpm check`：通过。
- `pnpm test`：通过，共 24 个测试文件、56 项测试。
- `pnpm build`：通过；仅有第三方 `@vueuse/core` 的两条 Rolldown PURE 注解位置警告。
- `pnpm run check:source-lines`：通过；仅报告 `web/src/pages/archive-categories/useArchiveCategories.ts` 315 行软提示，无硬阈值违规。
- `server/` 下执行 `mise exec -- mvn -q spotless:check test`：通过。
- 构建改写的 `web/src/components.d.ts` 分号已恢复，最终补丁不包含该生成文件副作用。
