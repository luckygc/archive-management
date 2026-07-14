# 生产源码职责收敛实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. 下方步骤复选框保留原始执行模板，实际完成状态以“执行状态”为准。

**Goal:** 将前端生产业务文件收敛到 300–500 个有效代码行、后端生产业务文件收敛到 500–700 个有效代码行，同时保持现有业务合同和行为不变。

**Architecture:** 后端按查询、写入和元数据子域拆成具体 Service，固定实体继续使用 Jakarta Data Repository，动态档案表和复杂查询继续使用 MyBatis。前端按完整业务区域提取组件与 composable，路由页面保留页面级选择、权限和跨区域刷新。

**Tech Stack:** Java 25、Spring Boot、Jakarta Data、Hibernate StatelessSession、MyBatis、Vue 3、TypeScript、Element Plus、Vite+、Vitest、JUnit 5、ArchUnit。

## 执行状态

- [x] 任务 1：建立有效代码行基线与回归检查
- [x] 任务 2：拆分档案记录查询职责
- [x] 任务 3：拆分档案记录写入与读取模型
- [x] 任务 4：收敛档案元数据 Service 边界
- [x] 任务 5：收敛其余后端热点
- [x] 任务 6：拆分档案管理与门类页面
- [x] 任务 7：拆分本体、治理和授权热点页面
- [x] 任务 8：完整验证与完成审计

### 最终验证结果（2026-07-14）

- `pnpm check`：通过。
- `pnpm test`：通过，共 24 个测试文件、56 项测试。
- `pnpm build`：通过；仅有第三方 `@vueuse/core` 的两条 Rolldown PURE 注解位置警告。
- `pnpm run check:source-lines`：通过；仅 `web/src/pages/archive-categories/useArchiveCategories.ts` 以 315 行触发前端软提示，无硬阈值违规。
- `server/` 下执行 `mise exec -- mvn -q spotless:check test`：通过。

## 全局约束

- 只统计生产业务源码；排除测试、迁移 SQL、Mapper XML、生成代码和第三方代码。
- 前端超过 300 行软提示、超过 500 行硬失败；后端超过 500 行软提示、超过 700 行硬失败。
- 不修改 HTTP API、数据库结构、权限、事务、错误响应和前端交互合同。
- 不新增空壳转发类、无收益接口、通用 CRUD 抽象、配置开关或兼容分支。
- 同一 Spring Bean 的 public 方法不得调用本类另一个 public 方法。
- 固定 CRUD 使用 Jakarta Data Repository；动态表、动态字段和复杂搜索继续使用 MyBatis。
- Java 变更交给 Spotless 使用 google-java-format AOSP 风格格式化。

---

### 任务 1：建立有效代码行基线与回归检查

**文件：**
- 创建：`scripts/source-lines.pl`
- 修改：`package.json`
- 测试：脚本自身通过已知混合注释输入验证

**接口：**
- 输入：仓库根目录下的生产源码路径。
- 输出：按有效代码行倒序的 `行数<TAB>文件路径`，支持前后端阈值筛选。

- [ ] **步骤 1：编写失败的脚本契约检查**

运行尚不存在的命令：

```bash
pnpm run check:source-lines
```

预期：失败，提示 `check:source-lines` 不存在。

- [ ] **步骤 2：实现最小统计脚本**

使用 Perl 逐字符维护块注释和 HTML 注释状态；对 Java/TypeScript/Vue/Go/CSS 排除空白、行注释和块注释。脚本默认扫描：

```text
server/src/main/java
web/src
frontend-core/src
preview
```

输出超过后端 500 行或前端 300 行软提示线的文件；只有超过后端 700 行或前端 500 行硬阈值时返回非零状态。允许通过 `--report` 只输出报告而不失败。

- [ ] **步骤 3：注册项目命令并验证统计结果**

在根 `package.json` 增加：

```json
"check:source-lines": "perl scripts/source-lines.pl"
```

运行：

```bash
pnpm run check:source-lines -- --report
```

预期：报告包含 `ArchiveItemRoutingService.java` 和现有超长 Vue 页面。

- [ ] **步骤 4：提交**

```bash
git add scripts/source-lines.pl package.json
git commit -m "build: 增加生产源码行数检查"
```

### 任务 2：拆分档案记录查询职责

**文件：**
- 创建：`server/src/main/java/github/luckygc/am/module/archive/item/service/ArchiveItemQueryService.java`
- 修改：`server/src/main/java/github/luckygc/am/module/archive/item/service/ArchiveItemRoutingService.java`
- 修改：所有调用档案列表、搜索、发现、回收站和关联筛选分类的 Controller/Service
- 测试：`server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveItemDataScopeQueryTests.java`
- 测试：`server/src/test/java/github/luckygc/am/module/archive/item/search/ArchiveFullTextSearchIntegrationTests.java`
- 测试：`server/src/test/java/github/luckygc/am/module/archive/item/web/ArchiveItemControllerTests.java`

**接口：**
- `ArchiveItemQueryService` 提供 `listItems`、`searchItems`、`discoverItems`、`searchDeletedItems`、`listRelatedFilterCategories`。
- 查询 DTO 迁入独立顶层 record 或查询 Service 的稳定嵌套类型，HTTP 请求/响应字段保持不变。
- `ArchiveItemRoutingService` 不再持有查询分页、排序、游标和搜索条件构造代码。

- [ ] **步骤 1：运行现有查询测试建立绿色基线**

```bash
cd server
mise exec -- mvn -q -Dtest=ArchiveItemDataScopeQueryTests,ArchiveFullTextSearchIntegrationTests,ArchiveItemControllerTests test
```

预期：全部通过。

- [ ] **步骤 2：增加架构断言并确认失败**

在 `ArchitectureRulesTest` 增加聚焦规则：`ArchiveItemRoutingService` 不得依赖查询分页类型或声明 `searchItems`、`discoverItems`、`searchDeletedItems`。先运行规则，预期因现有方法存在而失败。

- [ ] **步骤 3：迁移查询实现及调用方**

把 `queryItems`、动态分页、排序、游标、搜索条件、关联条件、投影字段和查询结果规范化完整迁入 `ArchiveItemQueryService`。保留 MyBatis Mapper 和全文 provider 使用方式，不改变 SQL。

- [ ] **步骤 4：删除旧查询入口并验证**

```bash
cd server
mise exec -- mvn -q spotless:apply
mise exec -- mvn -q -Dtest=ArchitectureRulesTest,ArchiveItemDataScopeQueryTests,ArchiveFullTextSearchIntegrationTests,ArchiveItemControllerTests test
```

预期：全部通过，且 `ArchiveItemRoutingService` 不再包含查询职责。

- [ ] **步骤 5：提交**

```bash
git add server/src/main/java server/src/test/java
git commit -m "refactor: 拆分档案记录查询职责"
```

### 任务 3：拆分档案记录写入与读取模型

**文件：**
- 创建：`server/src/main/java/github/luckygc/am/module/archive/item/service/ArchiveItemCommandService.java`
- 创建或复用：`server/src/main/java/github/luckygc/am/module/archive/item/service/ArchiveItemDetailService.java`
- 删除：职责清空后的 `ArchiveItemRoutingService.java`
- 修改：档案记录 Controller、导入导出、电子文件、卷件和审计调用方
- 测试：`server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveNoUniquenessTests.java`
- 测试：`server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveVolumePermissionTests.java`
- 测试：`server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveItemAuditWriteTests.java`

**接口：**
- `ArchiveItemCommandService` 提供 `createItem`、`updateItem`、`deleteItem`。
- `ArchiveItemDetailService` 提供 `getItem`、`getItemDetail`、`assertItemInDataScope`、`ensureItemEditable`，仅在这些读取确实被多个写入协作者复用时创建；否则归入命令 Service。
- 动态字段转换和动态表写入留在唯一写入 Service 的 private 方法中。

- [ ] **步骤 1：运行写入与权限测试建立绿色基线**

```bash
cd server
mise exec -- mvn -q -Dtest=ArchiveNoUniquenessTests,ArchiveVolumePermissionTests,ArchiveItemAuditWriteTests test
```

- [ ] **步骤 2：增加旧门面消失的架构断言并确认失败**

断言生产代码不得依赖 `ArchiveItemRoutingService`，先运行并确认因现有调用方失败。

- [ ] **步骤 3：迁移完整写入用例**

移动创建、修改、删除、动态字段转换、唯一性检查和业务审计；事务注解保留在实际写入 public 方法上。调用方直接注入具体用例 Service。

- [ ] **步骤 4：删除旧门面并验证**

```bash
cd server
mise exec -- mvn -q spotless:apply
mise exec -- mvn -q -Dtest=ArchitectureRulesTest,ArchiveNoUniquenessTests,ArchiveVolumePermissionTests,ArchiveItemAuditWriteTests test
```

- [ ] **步骤 5：提交**

```bash
git add server/src/main/java server/src/test/java
git commit -m "refactor: 拆分档案记录写入职责"
```

### 任务 4：拆分档案元数据总 Service

**文件：**
- 创建：`server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveFondsService.java`
- 创建：`server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveMetadataReferenceService.java`
- 创建：`server/src/main/java/github/luckygc/am/module/archive/metadata/service/ArchiveCategoryService.java`
- 修改：现有 `ArchiveFieldDefinitionService`、`ArchiveFieldLayoutService`、`ArchiveDynamicTableService`、`ArchiveUniqueConstraintService`
- 修改：`ArchiveMetadataService.java`，只保留字段、布局、动态表和唯一约束的事务协调
- 修改：`ArchiveMetadataController.java` 及跨模块调用方
- 测试：`server/src/test/java/github/luckygc/am/module/archive/metadata/ArchiveMetadataServiceTests.java`
- 测试：元数据目录下现有专项 Service 测试

**接口：**
- 全宗、分类方案、门类和全宗门类范围分别由对应具体 Service 持有。
- 字段、布局、动态表和唯一约束通过 `ArchiveMetadataService` 表达完整事务用例，内部复用现有专项 Service，不保留无业务语义的转发。
- HTTP Request/Response 迁至 web 包或稳定顶层业务类型，字段和 JSON 合同不变。

- [ ] **步骤 1：运行元数据测试建立绿色基线**

```bash
cd server
mise exec -- mvn -q -Dtest='ArchiveMetadataServiceTests,ArchiveFieldDefinitionServiceTests,ArchiveFieldLayoutServiceTests,ArchiveDynamicTableServiceTests,ArchiveUniqueConstraintServiceTests' test
```

- [ ] **步骤 2：增加旧总入口消失的架构断言并确认失败**

断言 `ArchiveMetadataService` 不再包含全宗、分类方案、门类和全宗门类范围用例，先确认现有聚合类触发失败。

- [ ] **步骤 3：按一个业务闭环逐次迁移**

依次迁移全宗、分类方案和门类范围；字段、布局、动态表和唯一约束留在具备真实事务协调职责的 `ArchiveMetadataService`。每次同步迁移 Controller、跨模块调用与测试，不保留新旧双入口。

- [ ] **步骤 4：验证元数据 Service 最终边界**

```bash
cd server
mise exec -- mvn -q spotless:apply
mise exec -- mvn -q -Dtest='ArchitectureRulesTest,ArchiveMetadataServiceTests,ArchiveFieldDefinitionServiceTests,ArchiveFieldLayoutServiceTests,ArchiveDynamicTableServiceTests,ArchiveUniqueConstraintServiceTests' test
```

- [ ] **步骤 5：提交**

```bash
git add server/src/main/java server/src/test/java
git commit -m "refactor: 拆分档案元数据业务入口"
```

### 任务 5：收敛其余后端热点

**文件：**
- 修改或拆分：`ArchiveDataScopeService.java`
- 修改或拆分：`ArchiveOntologyService.java`
- 修改或拆分：`ArchiveLocalRuleService.java`
- 测试：各 Service 现有测试及 `ArchitectureRulesTest`

**接口：**
- 数据范围查询与写入按权限和事务边界分离。
- 本体的概念、属性映射和关系维护仅在存在独立用例时分离。
- 规则校验算法保持 private 或无状态协作者，不新增通用规则框架。

- [ ] **步骤 1：运行三个热点的现有测试**

```bash
cd server
mise exec -- mvn -q -Dtest='ArchiveDataScopeServiceTests,ArchiveOntologyServiceTests,ArchiveLocalRuleServiceTests' test
```

预期：全部通过。

- [ ] **步骤 2：先约束数据范围拆分边界**

在 `ArchitectureRulesTest` 增加断言：数据范围查询 Service 不声明写入方法，写入 Service 不直接构造查询筛选条件。先运行并确认现有聚合类触发失败，再迁移列表/详情查询和创建/修改/删除用例及全部调用方。

- [ ] **步骤 3：按本体业务对象迁移独立用例**

只有当概念、属性映射或关系维护各自具备独立 Repository/Mapper 协作和 public 用例时创建具体 Service；否则将无状态校验留为原 Service 的 private 方法。先为选定边界增加架构断言并确认失败，再迁移调用方。

- [ ] **步骤 4：收敛本地规则 Service**

将规则条件验证放入已有 `ArchiveRuleConditionValidator`，规则 CRUD 和执行跟踪留在各自真实用例 Service；不得创建通用规则引擎接口。运行：

```bash
cd server
mise exec -- mvn -q spotless:apply
mise exec -- mvn -q -Dtest='ArchitectureRulesTest,ArchiveDataScopeServiceTests,ArchiveOntologyServiceTests,ArchiveLocalRuleServiceTests' test
```

- [ ] **步骤 5：提交后端第二批收敛**

```bash
git add server/src/main/java server/src/test/java
git commit -m "refactor: 收敛档案授权本体与规则职责"
```

### 任务 6：拆分档案管理与门类页面

**文件：**
- 修改：`web/src/pages/archive-items/ArchiveItemManagementPage.vue`
- 创建或修改：同目录查询、编辑、电子文件和审计业务组件/composable
- 修改：`web/src/pages/archive-categories/ArchiveCategoriesPage.vue`
- 创建：同目录门类树、字段定义、布局和唯一约束业务组件
- 测试：两个页面现有测试及新增子组件测试

**接口：**
- 页面持有主对象选择和跨区域刷新。
- 子组件通过业务事件表达 `created`、`updated`、`deleted`、`selection-change`，不访问路由或页面 Store。

- [ ] **步骤 1：运行两个页面测试建立绿色基线**

```bash
pnpm test -- web/src/pages/archive-items/ArchiveItemManagementPage.test.ts web/src/pages/archive-categories/ArchiveCategoriesPage.test.ts
```

- [ ] **步骤 2：为档案查询区域增加交互测试并确认失败**

新增 `ArchiveItemQueryPanel.test.ts`，断言筛选提交发出规范化查询事件、重置恢复默认条件。测试先因组件不存在失败。

- [ ] **步骤 3：迁移档案页面业务区域**

依次提取查询结果、记录编辑、电子文件和操作审计区域；每个组件同时迁移其模板和局部状态，页面只处理当前记录与成功后的跨区域刷新。每提取一个区域都运行对应测试。

- [ ] **步骤 4：迁移门类页面业务区域**

新增门类树、字段定义、字段布局和唯一约束区域组件。先为门类选择与保存成功事件增加测试，再移动局部状态；页面保留当前门类和跨区域刷新。

- [ ] **步骤 5：运行检查和页面测试后提交**

```bash
pnpm check
pnpm test -- web/src/pages/archive-items web/src/pages/archive-categories
git add web/src/pages/archive-items web/src/pages/archive-categories
git commit -m "refactor: 拆分档案管理与门类页面"
```

### 任务 7：拆分本体、治理和授权热点页面

**文件：**
- 修改：`ArchiveOntologyPage.vue`
- 修改：`ArchiveGovernancePage.vue`
- 修改：`AuthorizationManagementPage.vue`
- 修改：`ArchiveDataScopesPage.vue`
- 创建或复用：各页面同目录业务组件与 composable
- 测试：对应现有页面测试和业务区域测试

**接口：**
- 本体页面按概念、属性映射和关系区域拆分。
- 治理页面复用现有 `useArchiveGovernanceWorkbench.ts`，按方案、版本、范围和绑定区域拆分。
- 授权页面按用户/角色选择与权限配置区域拆分。
- 数据范围页面按范围列表与编辑弹层拆分。

- [ ] **步骤 1：逐页运行现有测试建立绿色基线**

```bash
pnpm test -- web/src/pages/archive-ontology web/src/pages/archive-governance web/src/pages/authorization-management web/src/pages/archive-data-scopes
```

- [ ] **步骤 2：先写各独立区域的事件测试**

分别覆盖本体概念选择、治理方案版本选择、授权对象选择和数据范围保存成功事件；测试先因目标子组件或事件合同不存在而失败。

- [ ] **步骤 3：迁移完整业务区域**

本体按概念/属性映射/关系，治理按方案/版本/范围/绑定，授权按对象选择/权限配置，数据范围按列表/编辑弹层迁移。子组件不读取路由或页面 Store，页面保留跨区域联动。

- [ ] **步骤 4：运行检查、聚焦测试并提交**

```bash
pnpm check
pnpm test -- web/src/pages/archive-ontology web/src/pages/archive-governance web/src/pages/authorization-management web/src/pages/archive-data-scopes
git add web/src/pages/archive-ontology web/src/pages/archive-governance web/src/pages/authorization-management web/src/pages/archive-data-scopes
git commit -m "refactor: 拆分治理本体与授权热点页面"
```

### 任务 8：完整验证与完成审计

**文件：**
- 修改：`scripts/source-lines.pl`，仅在扫描发现语言边界误判时修正
- 修改：架构文档，仅在最终边界与设计有差异时同步

- [ ] **步骤 1：运行后端完整验证**

```bash
cd server
mise exec -- mvn -q spotless:check test
```

- [ ] **步骤 2：运行前端完整验证**

```bash
pnpm check
pnpm test
pnpm build
```

- [ ] **步骤 3：运行生产源码行数检查**

```bash
pnpm run check:source-lines
```

预期：超过软提示线的文件仅作为职责复核清单；不存在后端超过 700 行或前端超过 500 行的生产业务文件。

- [ ] **步骤 4：检查空壳和旧入口**

搜索旧 Service 引用、只转发单行 public 方法、无实现价值接口和重复状态源；发现问题则回到对应任务修复。

- [ ] **步骤 5：提交验证收尾**

```bash
git add .
git commit -m "refactor: 完成生产源码职责收敛"
```
