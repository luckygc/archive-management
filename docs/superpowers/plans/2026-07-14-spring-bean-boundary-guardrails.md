# Spring Bean Boundary Guardrails Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立并通过 Spring Bean public 自调用和单实现业务接口架构规则，修复当前后端源码中的全部 public 自调用违规。

**Architecture:** 规则实现放入现有 `ArchitectureRulesTest`，不新增生产运行时组件。public 入口通过 private 核心方法共享实现；具有独立业务边界的能力才拆成另一个具体 Spring Bean。业务接口检查只覆盖 `module..service..` 和 `module..manager..`，Repository、Mapper、基础设施端口和真实多实现策略不纳入此规则。

**Tech Stack:** Java 25、Spring Boot、ArchUnit 1.4.2、JUnit 5、Maven、Spotless。

## Global Constraints

- 始终使用中文文档和注释。
- 不修改现有 HTTP API、数据库结构、权限、错误或前端业务行为。
- 不通过 self 注入、`ApplicationContext`、暴露代理或手工代理规避 public 自调用规则。
- 只有一个实现且没有稳定端口价值的业务 Service/Manager 直接使用具体类。
- Repository、MyBatis Mapper、稳定基础设施端口和已有多实现策略允许使用接口。
- 不新增通用 Facade、Adapter、工厂、配置开关或兼容分支。
- Java 格式由 Spotless 的 google-java-format AOSP 风格处理。
- 保持用户现有 `server/src/main/resources/application-local.taml` 索引与工作区状态不变。

---

## File Structure

- Modify: `AGENTS.md`
  - 固化单实现业务能力和 Spring Bean public 自调用规则。
- Modify: `docs/architecture.md`
  - 解释公开用例入口、private 核心实现与跨 Bean 协作边界。
- Modify: `server/src/test/java/github/luckygc/am/architecture/ArchitectureRulesTest.java`
  - 增加两项架构检查及可定位错误输出。
- Modify: `server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveMetadataService.java`
  - 将 public 重载与内部调用收敛到 private 核心方法。
- Modify: `server/src/main/java/github/luckygc/am/module/archive/item/service/ArchiveItemRoutingService.java`
  - 将查询、详情、权限、关系和可编辑性内部复用收敛到 private 核心方法。
- Modify: `server/src/main/java/github/luckygc/am/module/archive/item/service/ArchiveItemLineTableService.java`
  - 避免 public 查询方法在本 Bean 内互调。
- Modify: `server/src/main/java/github/luckygc/am/module/archive/item/service/ArchiveVolumeService.java`
  - 避免 public 数据范围断言在本 Bean 内复用。
- Modify: `server/src/main/java/github/luckygc/am/module/authentication/service/PowChallengeService.java`
  - 将默认请求重载收敛到 private 核心方法。
- Modify: `server/src/main/java/github/luckygc/am/module/storage/service/StorageObjectService.java`
  - 将活动对象加载收敛到 private 核心方法。

### Task 1: 固化仓库协作规则

**Files:**
- Modify: `AGENTS.md:30`
- Modify: `docs/architecture.md`

**Interfaces:**
- Consumes: 已批准设计中的 Bean 和接口约束。
- Produces: 后续架构测试与代码审查使用的仓库级文字合同。

- [ ] **Step 1: 在后端包结构约定后增加 Bean 边界规则**

在 `AGENTS.md` 后端约定中加入：

```markdown
- 业务模块中的 Service、Manager 和领域协作能力如果只有一个实现，且不是框架声明式合同、稳定基础设施端口或已经发生的多实现策略，直接使用具体类；不要为了测试替换或未来可能扩展而新增接口、工厂或适配层。Jakarta Data Repository、MyBatis Mapper、稳定基础设施端口和已有多个实现的策略合同不受此限制。
- 同一 Spring Bean 的 public 方法不得调用本类另一个 public 方法。多个公开用例需要复用实现时提取 private 核心方法；被复用能力具有独立事务、权限或业务边界时拆到另一个具体 Spring Bean 并通过构造器注入。禁止使用 self 注入、`ApplicationContext` 查找本 Bean、暴露代理或手工代理绕过该规则。
```

- [ ] **Step 2: 在架构文档记录调用方向**

在 `docs/architecture.md` 的后端分层说明中加入：

```markdown
Spring Bean 的 public 方法表示可由 Controller、事件监听器或其他 Bean 调用的业务入口。本 Bean 内部共享逻辑使用 private 方法；跨事务、权限或业务边界的协作通过构造器注入另一个具体 Bean。项目不使用 Bean 自调用来复用 public 方法，也不为单实现业务能力预留接口。
```

- [ ] **Step 3: 检查文档差异**

Run: `git diff --check -- AGENTS.md docs/architecture.md`

Expected: exit 0，无空白错误。

### Task 2: 以 TDD 增加 Spring Bean public 自调用规则

**Files:**
- Modify: `server/src/test/java/github/luckygc/am/architecture/ArchitectureRulesTest.java`

**Interfaces:**
- Consumes: `JavaClass#getMethodCallsFromSelf()`、`JavaMethodCall#getTarget().resolveMember()`。
- Produces: `spring_component_public_methods_should_not_call_other_public_methods(JavaClasses)` 架构测试。

- [ ] **Step 1: 写入架构检查并保持生产违规未修复**

增加 `JavaModifier` import，并在现有 Spring 构造器规则之后加入：

```java
@ArchTest
static void spring_component_public_methods_should_not_call_other_public_methods(
        JavaClasses classes) {
    List<String> violations =
            classes.stream()
                    .filter(ArchitectureRulesTest::isSpringComponentClass)
                    .flatMap(javaClass -> javaClass.getMethodCallsFromSelf().stream())
                    .filter(
                            call ->
                                    call.getOrigin()
                                            .getModifiers()
                                            .contains(JavaModifier.PUBLIC))
                    .filter(call -> call.getOriginOwner().equals(call.getTargetOwner()))
                    .flatMap(
                            call ->
                                    call.getTarget().resolveMember().stream()
                                            .filter(
                                                    target ->
                                                            target.getModifiers()
                                                                    .contains(
                                                                            JavaModifier.PUBLIC))
                                            .filter(target -> !target.equals(call.getOrigin()))
                                            .map(
                                                    target ->
                                                            call.getOrigin().getFullName()
                                                                    + " -> "
                                                                    + target.getFullName()
                                                                    + " @"
                                                                    + call.getLineNumber()))
                    .sorted()
                    .toList();

    assertTrue(
            violations.isEmpty(),
            () -> "Spring Bean 的 public 方法不得调用本类其他 public 方法: " + violations);
}
```

- [ ] **Step 2: 运行测试确认 RED**

Run:

```bash
cd server && mise exec -- mvn -q \
  -Dtest=github.luckygc.am.architecture.ArchitectureRulesTest \
  -Dspotless.check.skip=true test
```

Expected: FAIL，失败信息包含 `ArchiveMetadataService`、`ArchiveItemRoutingService` 等具体源方法和目标方法。

- [ ] **Step 3: 保存失败清单**

将测试输出中的每个唯一 `Owner#origin -> Owner#target` 作为 Task 4 的修复清单；不得通过测试白名单跳过生产类。

### Task 3: 以 TDD 增加单实现业务接口规则

**Files:**
- Modify: `server/src/test/java/github/luckygc/am/architecture/ArchitectureRulesTest.java`

**Interfaces:**
- Consumes: `JavaClass#getAllSubclasses()`。
- Produces: `module_service_and_manager_interfaces_should_have_multiple_implementations(JavaClasses)`。

- [ ] **Step 1: 增加业务接口识别 helper**

```java
private static boolean isBusinessServiceOrManagerInterface(JavaClass javaClass) {
    return javaClass.isInterface()
            && javaClass.getPackageName().startsWith("github.luckygc.am.module.")
            && (javaClass.getPackageName().contains(".service")
                    || javaClass.getPackageName().contains(".manager"))
            && (javaClass.getSimpleName().endsWith("Service")
                    || javaClass.getSimpleName().endsWith("Manager"));
}
```

- [ ] **Step 2: 增加架构检查**

```java
@ArchTest
static void module_service_and_manager_interfaces_should_have_multiple_implementations(
        JavaClasses classes) {
    List<String> violations =
            classes.stream()
                    .filter(ArchitectureRulesTest::isBusinessServiceOrManagerInterface)
                    .map(
                            contract ->
                                    java.util.Map.entry(
                                            contract,
                                            contract.getAllSubclasses().stream()
                                                    .filter(implementation -> !implementation.isInterface())
                                                    .filter(
                                                            implementation ->
                                                                    implementation
                                                                            .getPackageName()
                                                                            .startsWith(
                                                                                    "github.luckygc.am."))
                                                    .map(JavaClass::getName)
                                                    .sorted()
                                                    .toList()))
                    .filter(entry -> entry.getValue().size() < 2)
                    .map(
                            entry ->
                                    entry.getKey().getName()
                                            + " 项目内实现="
                                            + entry.getValue())
                    .sorted()
                    .toList();

    assertTrue(
            violations.isEmpty(),
            () -> "单实现业务 Service/Manager 应直接使用具体类: " + violations);
}
```

- [ ] **Step 3: 运行架构测试**

Run:

```bash
cd server && mise exec -- mvn -q \
  -Dtest=github.luckygc.am.architecture.ArchitectureRulesTest \
  -Dspotless.check.skip=true test
```

Expected: 仍因 Task 2 的 public 自调用规则 FAIL；单实现接口规则不应报告 Repository、Mapper、`FileLinkTargetResolver`、`ExpiredDataCleaner`、`FileStorageService` 或 `FullTextSearchProvider`。

### Task 4: 修复元数据 Bean public 自调用

**Files:**
- Modify: `server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveMetadataService.java`
- Test: `server/src/test/java/github/luckygc/am/module/archive/metadata/ArchiveMetadataServiceTests.java`
- Test: `server/src/test/java/github/luckygc/am/module/archive/metadata/service/ArchiveFieldLayoutServiceTests.java`

**Interfaces:**
- Consumes: 现有 Repository 和专项 Service，不改变公开 HTTP DTO。
- Produces: 保持现有 public 签名，但所有本类复用转到 private `load*`、`list*Internal`、`buildTableInternal` 方法。

- [ ] **Step 1: 用 private 加载方法替代内部 public getter 调用**

保留 `getCategory(Long)`、`getField(Long)`、`getUniqueConstraint(Long)` 作为公开入口，并分别委托新增 private 方法：

```java
public ArchiveCategoryDto getCategory(Long id) {
    return loadCategory(id);
}

private ArchiveCategoryDto loadCategory(Long id) {
    requireId(id);
    return categoryRepository
            .findById(id)
            .map(this::mapCategory)
            .orElseThrow(() -> notFound("档案分类不存在"));
}
```

`getField` 与 `getUniqueConstraint` 使用同一结构提取 `loadField`、`loadUniqueConstraint`，并把本类内部调用全部替换为 private 加载方法。

- [ ] **Step 2: 收敛字段列表和有效字段重载**

保留现有 public 签名用于调用方兼容，但让每个 public 方法直接调用 private 核心方法：

```java
private List<ArchiveFieldDto> listEnabledFieldsInternal(
        Long categoryId, ArchiveLevel archiveLevel, ArchiveFieldScope fieldScope) {
    requireId(categoryId);
    return fieldRepository.list(categoryId, archiveLevel, fieldScope, true).stream()
            .map(this::mapField)
            .toList();
}
```

`listEffectiveFields` 提取接受 `categoryId`、`archiveLevel`、`fieldScope`、`surface` 和 `userId` 的 `listEffectiveFieldsInternal`；`getFieldLayout` 提取接受 `categoryId`、`archiveLevel`、`fieldScope` 和 `surface` 的 `getFieldLayoutInternal`；`savePublicFieldLayout` 保存后直接调用 private layout 核心方法。

- [ ] **Step 3: 收敛动态表构建重载**

四个 public `buildTable` 入口都调用：

```java
private ArchiveCategoryDto buildTableInternal(
        Long categoryId,
        ArchiveLevel requestedLevel,
        ArchiveFieldScope fieldScope,
        @Nullable Long userId) {
    // 移入当前最完整 public buildTable 的原实现
}
```

内部分类加载和字段列表使用 private 核心方法，不再调用 public 方法。

- [ ] **Step 4: 运行元数据测试**

Run:

```bash
cd server && mise exec -- mvn -q \
  -Dtest=github.luckygc.am.module.archive.metadata.ArchiveMetadataServiceTests,github.luckygc.am.module.archive.metadata.service.ArchiveFieldLayoutServiceTests \
  -Dspotless.check.skip=true test
```

Expected: PASS。

### Task 5: 修复档案记录 Bean public 自调用

**Files:**
- Modify: `server/src/main/java/github/luckygc/am/module/archive/item/service/ArchiveItemRoutingService.java`
- Modify: `server/src/main/java/github/luckygc/am/module/archive/item/service/ArchiveItemLineTableService.java`
- Modify: `server/src/main/java/github/luckygc/am/module/archive/item/service/ArchiveVolumeService.java`
- Test: `server/src/test/java/github/luckygc/am/module/archive/item/service/*Tests.java`

**Interfaces:**
- Consumes: 现有 DTO、Repository、Mapper、权限和数据范围 Service。
- Produces: public HTTP 用例签名不变，内部使用 private 核心方法。

- [ ] **Step 1: 收敛 ArchiveItemRoutingService 查询入口**

`listItems`、两个 `searchItems`、两个 `discoverItems`、两个 `searchDeletedItems` 均直接调用已有 private `queryItems`，传入各自的 `allowKeyword`、`deleted` 和 `PageRequest`；`listItems` 不再调用 public `searchItems`。

- [ ] **Step 2: 提取档案加载、详情、权限和可编辑 private 核心方法**

新增 `loadItem(Long)`，其方法体完整使用当前 `getItem(Long)` 的参数校验、Mapper 查询和 DTO 映射语句；新增 `loadItemDetail(Long, Long, ArchiveLayoutSurface)`，其方法体完整使用当前 `getItemDetail` 的分类、权限、字段布局和动态记录组装语句，并把档案加载改为 `loadItem`。

新增 `assertItemIdInDataScope(Long, Long)`：先用 `loadItem` 加载记录，再用 `archiveMetadataService.getCategory(record.categoryId())` 加载分类，最后调用现有 private `assertItemInDataScope(Long, ArchiveCategoryDto, ArchiveItemDto)`。新增 `ensureItemIdEditable(Long)`，调用 `ensureItemEditable(loadItem(id))`。

public `getItem`、`getItemDetail`、`assertItemInDataScope`、`ensureItemEditable` 委托对应 private 方法，本类其他方法只调用 private 版本。这里移动现有语句，不改变异常文本、权限顺序或 DTO 字段。

- [ ] **Step 3: 收敛关系列表重载**

保留三个 public 签名，但让它们直接调用 private：

```java
private List<ArchiveItemRelationDto> listRelationsInternal(
        Long itemId, @Nullable Integer depth, @Nullable Long userId) {
    // 移入当前最完整 listRelations 实现
}
```

- [ ] **Step 4: 收敛 LineTable 和 Volume 内部查询**

`ArchiveItemLineTableService` 提取 `loadLineTable` 和 `listLineFieldsInternal`；`ArchiveVolumeService` 将 id 版本数据范围检查提取为 private `assertVolumeIdInDataScope`，本类内部不调用 public 断言入口。

- [ ] **Step 5: 运行档案记录测试**

Run:

```bash
cd server && mise exec -- mvn -q \
  -Dtest='github.luckygc.am.module.archive.item.service.*Tests,github.luckygc.am.module.archive.item.web.*Tests' \
  -Dspotless.check.skip=true test
```

Expected: PASS。

### Task 6: 修复其余 Bean 并完成架构验证

**Files:**
- Modify: `server/src/main/java/github/luckygc/am/module/authentication/service/PowChallengeService.java`
- Modify: `server/src/main/java/github/luckygc/am/module/storage/service/StorageObjectService.java`
- Modify: 仅限 Task 2 架构测试实际报告的其他文件。
- Test: 对应现有 `*Tests.java`。

**Interfaces:**
- Consumes: Task 2 的完整违规输出。
- Produces: 当前生产 Spring Bean 中不存在 public 方法调用同类其他 public 方法。

- [ ] **Step 1: 修复已知重载**

`PowChallengeService.createChallenge()` 直接调用 private `createChallengeInternal(@Nullable CapChallengeRequest)`；`StorageObjectService` 提取 private `loadActiveObject(Long)` 并让 public 入口与内部下载入口共同使用。

- [ ] **Step 2: 修复架构测试报告的剩余项**

对每个剩余违规严格采用以下二选一：

```java
public Result publicEntry(Input input) {
    return executeInternal(input);
}

private Result executeInternal(Input input) {
    // 唯一实现
}
```

或当逻辑具有独立事务/权限边界时，移动到另一个已有或职责明确的具体 Spring Bean 并通过构造器调用。不得新增单实现接口。

- [ ] **Step 3: 运行聚焦测试和架构测试**

Run:

```bash
cd server && mise exec -- mvn -q \
  -Dtest=github.luckygc.am.module.authentication.service.PowChallengeServiceTests,github.luckygc.am.module.storage.service.StorageObjectServiceTests,github.luckygc.am.architecture.ArchitectureRulesTest \
  -Dspotless.check.skip=true test
```

Expected: PASS，架构测试无 public 自调用和单实现业务接口违规。

- [ ] **Step 4: 格式化并复验**

Run:

```bash
cd server && mise exec -- mvn -q spotless:apply
cd server && mise exec -- mvn -q \
  -Dtest=github.luckygc.am.architecture.ArchitectureRulesTest test
```

Expected: 两条命令 exit 0。

- [ ] **Step 5: 更新 OpenSpec 任务状态并提交本阶段**

将 `openspec/changes/simplify-code-and-enforce-bean-boundaries/tasks.md` 的 1.1 至 2.4 勾选完成，随后仅提交本阶段文件：

```bash
git add AGENTS.md docs/architecture.md \
  docs/superpowers/plans/2026-07-14-spring-bean-boundary-guardrails.md \
  openspec/changes/simplify-code-and-enforce-bean-boundaries \
  server/src/main/java server/src/test/java
git commit -m "refactor: enforce Spring Bean method boundaries"
```

Expected: 提交不包含 `server/src/main/resources/application-local.taml`。
