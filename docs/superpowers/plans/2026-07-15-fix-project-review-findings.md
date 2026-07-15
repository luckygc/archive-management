# 项目审查问题修复实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复同步导出只读事务、直接文件流下载、规则追踪先截断后过滤和前端生成声明漂移，并以用户绑定临时短链和稳定 cursor 分页形成完整闭环。

**Architecture:** 导入模板和导出结果由 POST 同步生成到具有 10 分钟 TTL 的临时 S3 对象，再复用现有用户绑定 FileLink 返回安全 GET 短链；前端只用临时 `<a>` 打开短链，不读取 Blob。规则追踪由 Service 一次性编译当前用户的数据范围，MyBatis 在 PostgreSQL 中完成可见性过滤和 `(created_at, id)` 键集分页，Controller 与前端统一使用 `PageRequest` / `CursorPageResponse` 合同。

**Tech Stack:** Java 25、Spring Boot 4.1、Spring MVC、Spring Transaction、Jakarta Data、MyBatis、PostgreSQL 18/Testcontainers、Vue 3、TypeScript、Axios、Vite+、Vitest、OpenSpec。

## Global Constraints

- 始终使用中文交流、编写文档和注释；只修改本次审查确认的四个问题，不扩展相邻模块。
- 保留同步导出和现有 5000 行上限，不引入后台 Job、消息队列、适配层或配置开关。
- 导入模板和导出仅保留 `POST ...:createImportTemplateDownloadLink` 与 `POST ...:createExportDownloadLink`；不保留尚未发布的直接文件流兼容分支。
- 临时 S3 对象与用户绑定短链统一使用 10 分钟 TTL；前端不得通过 XHR/fetch 读取 Blob。
- 过期对象清理必须先幂等删除 S3 对象，成功后再硬删除本地存储记录；S3 失败时保留记录供重试。
- 规则追踪分页固定按 `created_at DESC, id DESC`，分页参数只使用 URL query 中的 `limit`、`cursor`、`requestTotal`。
- 动态档案表名和字段名只能来自已经校验的分类元数据与数据范围 SQL 条件，不接收客户端标识符。
- 固定实体审计仍通过 Jakarta Data Repository 写入；规则追踪的动态数据范围与复杂分页继续由 MyBatis 承担。
- Java 格式以 `server/` 下 Spotless 的 google-java-format AOSP 风格为准；前端使用 Vite+，不得启动开发服务器。
- 现有未跟踪 `target/` 不属于本次工作，任何提交都不得包含它。

---

## 文件结构

- 修改 `openspec/changes/add-archive-data-scope-permissions-mvp-closure/specs/archive-import-export/spec.md`：明确模板和导出先生成临时对象与用户短链，再由浏览器直接打开安全 GET。
- 修改 `openspec/specs/file-storage/spec.md`：补充 10 分钟临时对象与 S3/数据库统一清理合同。
- 修改 `openspec/changes/add-archive-governance-foundation/specs/archive-local-rule-engine/spec.md`：明确规则追踪使用数据范围过滤后的稳定 cursor 分页。
- 修改 `server/src/main/java/github/luckygc/am/module/archive/item/service/ArchiveItemImportExportService.java`：导出事务改为可写。
- 修改 `server/src/main/java/github/luckygc/am/module/storage/service/StorageObjectService.java`：保存可过期对象并注册事务回滚 S3 补偿。
- 修改 `server/src/main/java/github/luckygc/am/module/storage/repository/StorageObjectDataRepository.java`：用固定 HQL 分批查询过期对象。
- 创建 `server/src/main/java/github/luckygc/am/module/storage/StorageObjectExpiredDataCleaner.java`：先清 S3、成功后删本地记录。
- 创建 `server/src/test/java/github/luckygc/am/module/storage/StorageObjectExpiredDataCleanerTests.java`：覆盖顺序、失败保留与批量上限。
- 修改 `server/src/main/java/github/luckygc/am/module/archive/item/service/ArchiveItemImportExportService.java`：生成模板和导出后保存临时对象并创建用户短链。
- 修改 `server/src/main/java/github/luckygc/am/module/archive/item/web/ArchiveItemImportExportController.java`：只返回短链响应，删除所有直接文件流入口。
- 修改 `server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveItemImportExportServiceTests.java`：固定导出方法必须使用可写事务。
- 创建 `server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveItemExportTransactionIntegrationTests.java`：在 PostgreSQL 中证明导出成功写入审计。
- 修改 `server/src/test/java/github/luckygc/am/module/archive/item/web/ArchiveItemImportExportControllerTests.java`：验证两个创建短链接口和认证用户。
- 修改 `web/src/shared/api/archive-records.ts` 与 `web/src/shared/api/archive.test.ts`：模板和导出先 POST 创建短链，不再 Base64 编码查询。
- 修改 `web/src/pages/archive-items/ArchiveItemManagementPage.vue` 及相关测试：通过临时 `<a>` 打开返回短链，断言不使用 Blob API。
- 修改 `server/src/main/java/github/luckygc/am/module/archive/mapper/ArchiveRuleTraceSearchCriteria.java`：承载筛选、可见范围和分页窗口。
- 修改 `server/src/main/java/github/luckygc/am/module/archive/mapper/ArchiveRuleMapper.java` 与 `server/src/main/resources/mapper/archive/ArchiveRuleMapper.xml`：数据库内过滤并稳定键集分页。
- 修改 `server/src/main/java/github/luckygc/am/module/archive/rule/service/ArchiveRuleTraceService.java`：编译一次数据范围并组装 cursor 页。
- 修改 `server/src/main/java/github/luckygc/am/module/archive/rule/service/ArchiveLocalRuleService.java`：查询请求体移除 `limit`，Service 转发 `PageRequest`。
- 修改 `server/src/main/java/github/luckygc/am/module/archive/rule/web/ArchiveRuleController.java`：接收 `PageRequest` 并返回 `CursorPageResponse`。
- 修改 `server/src/test/java/github/luckygc/am/module/archive/rule/service/ArchiveLocalRuleServiceTests.java`：验证范围编译、limit+1 与双字段 cursor，不再逐条读档案。
- 创建 `server/src/test/java/github/luckygc/am/module/archive/rule/service/ArchiveRuleTraceMapperIntegrationTests.java`：验证超过 500 条不可见追踪时不漏可见记录，以及前后翻页无重复。
- 修改 `server/src/test/java/github/luckygc/am/infrastructure/web/ArchiveRuleControllerProblemDetailTests.java`：适配 PageRequest 合同并验证认证用户覆盖。
- 修改 `web/src/shared/types/archive-rules.ts`、`web/src/shared/api/archive-rules.ts`：拆分业务筛选与分页 query，返回 `CursorPageResponse`。
- 修改 `web/src/pages/archive-rule-traces/ArchiveRuleTracesPage.vue`：草稿/已提交查询、错误状态和 `CursorPagination`。
- 修改 `web/src/pages/archive-governance/ArchiveGovernanceFoundationPages.test.ts`：覆盖新查询、下一页、页大小重置和失败重试。
- 修改 `web/src/components.d.ts`：接受生成器当前稳定输出。

### Task 1: 固化 OpenSpec 合同

**Files:**
- Modify: `openspec/changes/add-archive-data-scope-permissions-mvp-closure/specs/archive-import-export/spec.md:28-43`
- Modify: `openspec/specs/file-storage/spec.md:88-122`
- Modify: `openspec/changes/add-archive-governance-foundation/specs/archive-local-rule-engine/spec.md:131-145`

**Interfaces:**
- Consumes: 已批准设计中的临时 S3 短链、统一清理与规则追踪 cursor 分页合同。
- Produces: 后端 Controller、前端 client 和测试共同遵守的验收真相源。

- [ ] **Step 1: 运行当前严格校验建立基线**

Run: `openspec validate --all --strict --no-interactive`

Expected: 所有现有 specs 与 changes 均通过；若基线失败，只记录与本任务无关的既有失败，不修改相邻规格。

- [ ] **Step 2: 修改生成文件场景为创建短链合同**

在 archive-import-export 规格中把“导出查询结果”明确为：

```markdown
- **AND** 客户端 SHALL 使用 `POST /api/v1/archive-items:createExportDownloadLink` 在 JSON 请求体中提交当前查询条件
- **AND** 服务端 SHALL 在同一成功事务中生成 Excel、写入导出审计、保存 10 分钟有效的临时 S3 对象并创建当前用户绑定短链
- **AND** 客户端 SHALL 使用临时 `<a>` 打开返回的安全 GET 短链
- **AND** 客户端 SHALL NOT 通过 XHR 或 fetch 读取导出文件 Blob
```

- [ ] **Step 3: 补充模板和临时对象清理合同**

在同一规格增加模板场景：

```markdown
#### Scenario: 创建导入模板下载短链

- **WHEN** 已认证用户请求档案导入模板
- **THEN** 客户端 SHALL 调用 `POST /api/v1/archive-categories/{categoryId}/archive-items:createImportTemplateDownloadLink`
- **AND** 服务端 SHALL 校验创建或编辑权限与分类数据范围
- **AND** 服务端 SHALL 生成 10 分钟有效的临时 S3 对象和当前用户绑定短链
- **AND** 客户端 SHALL 使用临时 `<a>` 打开返回的安全 GET 短链
```

在 `file-storage` 规格补充：临时对象必须在 `am_storage_object.expires_at` 固化过期时间；事务回滚删除已上传 S3 对象；清理器先删除 S3，成功后硬删除本地记录，失败保留记录重试；永久对象 `expires_at` 为空不参与清理。

- [ ] **Step 4: 补充规则追踪分页验收场景**

在“查询规则追踪”后增加：

```markdown
#### Scenario: 数据范围内稳定翻页

- **WHEN** 当前用户查询规则追踪且更晚的记录中包含超过一页的不可见记录
- **THEN** 系统 SHALL 在数据库中先应用当前用户功能权限和数据范围，再按 `created_at DESC, id DESC` 返回 cursor 页
- **AND** 相邻页面 SHALL NOT 因不可见记录而漏掉可见追踪
- **AND** 相邻页面 SHALL NOT 返回重复追踪
```

- [ ] **Step 5: 验证规格并提交**

Run: `openspec validate --all --strict --no-interactive`

Expected: 输出显示所有 specs 与 changes 均有效，无 strict validation error。

```bash
git add openspec/changes/add-archive-data-scope-permissions-mvp-closure/specs/archive-import-export/spec.md \
  openspec/specs/file-storage/spec.md \
  openspec/changes/add-archive-governance-foundation/specs/archive-local-rule-engine/spec.md
git commit -m "docs: 统一生成文件短链合同"
```

### Task 2: 让同步导出在真实可写事务中记录审计

**Files:**
- Modify: `server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveItemImportExportServiceTests.java`
- Create: `server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveItemExportTransactionIntegrationTests.java`
- Modify: `server/src/main/java/github/luckygc/am/module/archive/item/service/ArchiveItemImportExportService.java:129-131`

**Interfaces:**
- Consumes: `ArchiveItemImportExportService.exportItems(SearchArchiveItemsRequest, Long)`。
- Produces: 可写事务中的同步 Excel 与 `ArchiveItemAudit(operationType="EXPORT")` 原子结果。

- [ ] **Step 1: 写入必然失败的事务注解测试**

在 `ArchiveItemImportExportServiceTests` 增加：

```java
@Test
@DisplayName("导出使用可写事务以记录操作审计")
void exportItemsShouldUseWritableTransaction() throws NoSuchMethodException {
    Transactional transactional =
            ArchiveItemImportExportService.class
                    .getMethod(
                            "exportItems",
                            ArchiveItemQueryService.SearchArchiveItemsRequest.class,
                            Long.class)
                    .getAnnotation(Transactional.class);

    assertThat(transactional).isNotNull();
    assertThat(transactional.readOnly()).isFalse();
}
```

- [ ] **Step 2: 运行测试并确认红灯**

Run: `cd server && mise exec -- mvn -q -Dtest=ArchiveItemImportExportServiceTests#exportItemsShouldUseWritableTransaction test`

Expected: FAIL，断言 `readOnly` 当前为 `true`。

- [ ] **Step 3: 写 PostgreSQL 集成测试**

创建继承 `PostgreSqlContainerTest` 的 `ArchiveItemExportTransactionIntegrationTests`，使用 `@SpringBootTest`、`@Testcontainers(disabledWithoutDocker = true)`、`@DirtiesContext`。通过 `@MockitoBean` 替换 `ArchiveItemQueryService` 与 `AuthorizationPermissionService`，测试中：

```java
when(permissionService.hasPermission(9L, AuthorizationPermissionCode.ARCHIVE_EXPORT.code()))
        .thenReturn(true);
when(queryService.searchItems(any(), eq(9L)))
        .thenReturn(
                new ArchiveItemQueryService.ArchiveItemListDto(
                        null,
                        List.of(),
                        CursorPageResponse.withCursorValues(
                                List.of(), 1000, null, null, null, null, null)));

ArchiveExcelFile file = importExportService.exportItems(null, 9L);

assertThat(file.bytes()).isNotEmpty();
assertThat(
                jdbcTemplate.queryForObject(
                        "select count(*) from am_archive_item_audit "
                                + "where operation_type = 'EXPORT' and operated_by = 9",
                        Long.class))
        .isEqualTo(1L);
```

在 `@AfterEach` 用未限定 schema 的 SQL 删除 `operated_by = 9 and operation_type = 'EXPORT'` 的测试数据。测试方法本身不得标注 `@Transactional`，避免外层可写事务掩盖 Service 的只读属性。

- [ ] **Step 4: 做最小生产修改**

```java
@Transactional
public ArchiveExcelFile exportItems(
        ArchiveItemQueryService.@Nullable SearchArchiveItemsRequest request, Long userId) {
```

- [ ] **Step 5: 运行单元和集成测试**

Run: `cd server && mise exec -- mvn -q -Dtest=ArchiveItemImportExportServiceTests,ArchiveItemExportTransactionIntegrationTests test`

Expected: 单元测试 PASS；Docker 可用时 PostgreSQL 测试 PASS，Docker 不可用时集成测试明确 SKIPPED，不能把 SKIPPED 描述为已验证数据库行为。

- [ ] **Step 6: 格式化并提交**

Run: `cd server && mise exec -- mvn -q spotless:apply && git diff --check`

```bash
git add server/src/main/java/github/luckygc/am/module/archive/item/service/ArchiveItemImportExportService.java \
  server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveItemImportExportServiceTests.java \
  server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveItemExportTransactionIntegrationTests.java
git commit -m "fix: 允许导出事务写入审计"
```

### Task 3: 为临时 S3 对象实现过期与可重试清理

**Files:**
- Modify: `server/src/test/java/github/luckygc/am/module/storage/service/StorageObjectServiceTests.java`
- Create: `server/src/test/java/github/luckygc/am/module/storage/StorageObjectExpiredDataCleanerTests.java`
- Modify: `server/src/main/java/github/luckygc/am/module/storage/service/StorageObjectService.java`
- Modify: `server/src/main/java/github/luckygc/am/module/storage/repository/StorageObjectDataRepository.java`
- Create: `server/src/main/java/github/luckygc/am/module/storage/StorageObjectExpiredDataCleaner.java`

**Interfaces:**
- Consumes: 现有 `StorageObjectService.storeObject(StoreStorageObjectCommand, Long)` 与 `FileStorageService.deleteObject(bucketName, objectKey)`。
- Produces: `StoreStorageObjectCommand(..., @Nullable LocalDateTime expiresAt)`、事务回滚补偿和 `StorageObjectExpiredDataCleaner`。

- [ ] **Step 1: 写临时对象过期时间和事务回滚补偿测试**

在 `StorageObjectServiceTests` 保留现有永久对象用例，并新增：

```java
@Test
@DisplayName("保存临时对象时固化过期时间")
void storeObjectShouldPersistExpiration() {
    LocalDateTime expiresAt = LocalDateTime.of(2026, 7, 15, 10, 10);
    service.storeObject(
            new StoreStorageObjectCommand(
                    "archive-export.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    3,
                    new ByteArrayInputStream(new byte[] {1, 2, 3}),
                    expiresAt),
            9L);

    ArgumentCaptor<StorageObject> captor = ArgumentCaptor.forClass(StorageObject.class);
    verify(repository).insert(captor.capture());
    assertThat(captor.getValue().getExpiresAt()).isEqualTo(expiresAt);
}
```

测试中调用 `TransactionSynchronizationManager.initSynchronization()` 后执行 `storeObject`，取出已注册的唯一 `TransactionSynchronization` 并分别调用 `afterCompletion(STATUS_ROLLED_BACK)` 与 `afterCompletion(STATUS_COMMITTED)`；断言只有回滚状态调用 `fileStorageService.deleteObject("archive", objectKey)`，最后在 `finally` 中 `clearSynchronization()`。当前 command 不含 expiresAt 且没有同步回调，因此先失败。

- [ ] **Step 2: 写清理顺序与失败保留测试**

创建 `StorageObjectExpiredDataCleanerTests`，mock Repository 和 `FileStorageService`：

```java
@Test
@DisplayName("清理器先删除 S3 对象再删除本地记录")
void cleanerShouldDeleteRecordOnlyAfterObject() throws IOException {
    StorageObject expired = expiredObject(31L, "archive", "tmp/export.xlsx");
    when(repository.findExpired(NOW, Limit.of(100))).thenReturn(List.of(expired));

    ExpiredDataCleanupResult result = cleaner.cleanupExpired(NOW);

    InOrder order = inOrder(fileStorageService, repository);
    order.verify(fileStorageService).deleteObject("archive", "tmp/export.xlsx");
    order.verify(repository).delete(expired);
    assertThat(result.deletedCount()).isEqualTo(1);
}

@Test
@DisplayName("S3 删除失败时保留本地记录供下次重试")
void cleanerShouldKeepRecordWhenObjectDeletionFails() throws IOException {
    StorageObject expired = expiredObject(32L, "archive", "tmp/failed.xlsx");
    when(repository.findExpired(NOW, Limit.of(100))).thenReturn(List.of(expired));
    doThrow(new IOException("S3 unavailable"))
            .when(fileStorageService)
            .deleteObject("archive", "tmp/failed.xlsx");

    ExpiredDataCleanupResult result = cleaner.cleanupExpired(NOW);

    verify(repository, never()).delete(expired);
    assertThat(result.deletedCount()).isZero();
}
```

再断言 repository 只请求 `Limit.of(100)`，永久对象不会由 `findExpired` 返回。

- [ ] **Step 3: 运行测试并确认红灯**

Run: `cd server && mise exec -- mvn -q -Dtest=StorageObjectServiceTests,StorageObjectExpiredDataCleanerTests test`

Expected: testCompile FAIL 或断言 FAIL，因为 command、findExpired 和 cleaner 尚不存在。

- [ ] **Step 4: 扩展存储命令并注册回滚补偿**

`StoreStorageObjectCommand` 增加第五个字段：

```java
public record StoreStorageObjectCommand(
        String originalFilename,
        @Nullable String contentType,
        long contentLength,
        InputStream inputStream,
        @Nullable LocalDateTime expiresAt) {}
```

现有电子文件调用明确传 `null`。`storeObject` 在 insert 前调用 `storageObject.setExpiresAt(command.expiresAt())`。S3 put 成功后，如果事务同步已激活，注册 `TransactionSynchronization`：仅当 `afterCompletion(STATUS_ROLLED_BACK)` 时调用 `deleteObject(objectInfo.bucketName(), objectInfo.objectKey())`；补偿失败记录 error 日志但不得覆盖原事务异常。无事务时若 repository insert 抛异常，catch 中立即删除该 S3 对象后再抛出。

- [ ] **Step 5: 增加固定 HQL 过期查询和清理器**

Repository 增加显式固定实体查询：

```java
@Transactional(readOnly = true)
@HQL("from StorageObject where expiresAt is not null and expiresAt <= ?1 order by expiresAt, id")
List<StorageObject> findExpired(@Nonnull LocalDateTime expiresAt, Limit limit);
```

`StorageObjectExpiredDataCleaner` 实现 `ExpiredDataCleaner`，名称固定为 `storage_object`，每次使用 `Limit.of(100)`。逐条调用 `fileStorageService.deleteObject`，成功后 `repository.delete(object)` 并计数；捕获 `IOException | RuntimeException` 后记录包含 storage object ID 的 error 日志并继续下一条，不删除失败记录。

- [ ] **Step 6: 运行测试并格式化**

Run: `cd server && mise exec -- mvn -q -Dtest=StorageObjectServiceTests,StorageObjectExpiredDataCleanerTests test`

Expected: 所有测试 PASS，失败 S3 用例验证本地记录保留。

Run: `cd server && mise exec -- mvn -q spotless:apply && git diff --check`

- [ ] **Step 7: 提交临时对象生命周期**

```bash
git add server/src/main/java/github/luckygc/am/module/storage/service/StorageObjectService.java \
  server/src/main/java/github/luckygc/am/module/storage/repository/StorageObjectDataRepository.java \
  server/src/main/java/github/luckygc/am/module/storage/StorageObjectExpiredDataCleaner.java \
  server/src/main/java/github/luckygc/am/module/archive/item/service/ArchiveItemElectronicFileService.java \
  server/src/test/java/github/luckygc/am/module/storage/service/StorageObjectServiceTests.java \
  server/src/test/java/github/luckygc/am/module/storage/StorageObjectExpiredDataCleanerTests.java
git commit -m "feat: 清理过期临时存储对象"
```

### Task 4: 统一模板和导出短链

**Files:**
- Modify: `server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveItemImportExportServiceTests.java`
- Modify: `server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveItemExportTransactionIntegrationTests.java`
- Modify: `server/src/test/java/github/luckygc/am/module/archive/item/web/ArchiveItemImportExportControllerTests.java`
- Modify: `server/src/test/java/github/luckygc/am/module/storage/web/FileLinkDownloadControllerTests.java`
- Modify: `server/src/main/java/github/luckygc/am/module/archive/item/service/ArchiveItemImportExportService.java`
- Modify: `server/src/main/java/github/luckygc/am/module/archive/item/web/ArchiveItemImportExportController.java`
- Modify: `web/src/shared/api/archive.test.ts`
- Modify: `web/src/shared/api/archive-records.ts`
- Modify: `web/src/pages/archive-items/ArchiveItemManagementPage.vue`
- Modify: `web/src/pages/archive-items/ArchiveItemManagementPage.error-state.test.ts`

**Interfaces:**
- Consumes: Task 3 的可过期 `StoreStorageObjectCommand`、`StorageObjectService.storeObject`、现有 `FileLinkService.createUserLink(STORAGE_OBJECT, ...)`。
- Produces: `ArchiveItemDownloadLinkResponse(url, expiresAt)`，以及两个 POST custom methods。

- [ ] **Step 1: 写 Service 创建临时对象和用户短链失败测试**

在 `ArchiveItemImportExportServiceTests` 为模板和导出分别断言：生成的 `ArchiveExcelFile` 被包装为 `ByteArrayInputStream`，`StoreStorageObjectCommand.expiresAt()` 为固定 Clock 当前时间加 10 分钟；随后调用：

```java
fileLinkService.createUserLink(
        FileLinkTargetType.STORAGE_OBJECT,
        null,
        storageObject.id(),
        Duration.ofMinutes(10),
        userId);
```

返回值为：

```java
public record DownloadLinkCreated(String code, LocalDateTime expiresAt) {}
```

导出用例同时 verify audit repository insert；当 storage 或 link 抛异常时方法抛出，外层事务回滚审计。

- [ ] **Step 2: 写 Controller custom method 测试**

删除直接 `ResponseEntity<ByteArrayResource>` 测试，新增模板与导出两个测试，断言完整路径的方法分别调用 `createImportTemplateDownloadLink(categoryId, userId)` 和 `createExportDownloadLink(request, userId)`，并返回：

```java
new ArchiveItemDownloadLinkResponse(
        "/api/v1/file-links/short-code:download",
        LocalDateTime.of(2026, 7, 15, 10, 10))
```

在 `FileLinkDownloadControllerTests` 增加内部短链测试：认证用户 9 访问 `downloadInternal("short-code", authentication)`，verify `fileLinkService.resolveInternal("short-code", 9L)`，`STORAGE_OBJECT` resolver 返回 `StorageObjectDownload`，响应保持 `application/octet-stream` 与附件文件名。

- [ ] **Step 3: 写前端 API 与临时 a 标签测试**

`web/src/shared/api/archive.test.ts` 断言：

```ts
await downloadArchiveImportTemplate(11);
expect(httpClientMock.post).toHaveBeenCalledWith(
    "/api/v1/archive-categories/11/archive-items:createImportTemplateDownloadLink",
);

await exportArchiveRecords({ categoryId: 1, keyword: "合同", limit: 100, cursor: "ignored" });
expect(httpClientMock.post).toHaveBeenCalledWith(
    "/api/v1/archive-items:createExportDownloadLink",
    { categoryId: 1, keyword: "合同" },
);
expect(httpClientMock.download).toHaveBeenCalledWith(
    "/api/v1/file-links/export-code:download",
);
```

页面测试令两个 API 返回 `{ href: "/api/v1/file-links/code:download" }`，spy `HTMLAnchorElement.prototype.click`，断言模板和导出均点击临时 `<a>`；spy `URL.createObjectURL` 并断言从未调用。

- [ ] **Step 4: 运行测试并确认红灯**

Run: `cd server && mise exec -- mvn -q -Dtest=ArchiveItemImportExportServiceTests,ArchiveItemImportExportControllerTests test`

Run: `pnpm --filter @archive-management/web test -- src/shared/api/archive.test.ts src/pages/archive-items/ArchiveItemManagementPage.error-state.test.ts`

Expected: 后端编译失败或调用不匹配；前端仍调用直接 GET/Base64 URL，测试 FAIL。

- [ ] **Step 5: 实现创建短链 Service**

在 `ArchiveItemImportExportService` 注入 `StorageObjectService`、`FileLinkService`、`Clock`，常量固定：

```java
private static final Duration DOWNLOAD_LINK_TTL = Duration.ofMinutes(10);
private static final String XLSX_CONTENT_TYPE =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
```

保留原生成 Excel 私有核心逻辑，公开用例为：

```java
@Transactional
public DownloadLinkCreated createImportTemplateDownloadLink(Long categoryId, Long userId)

@Transactional
public DownloadLinkCreated createExportDownloadLink(
        ArchiveItemQueryService.@Nullable SearchArchiveItemsRequest request, Long userId)
```

两个方法生成 `ArchiveExcelFile` 后调用私有 `createDownloadLink(file, userId)`：以 `LocalDateTime.now(clock).plus(DOWNLOAD_LINK_TTL)` 写临时对象，创建 `STORAGE_OBJECT` 用户短链并返回 code/expiresAt。多个公开方法不得互相调用；共享 Excel 生成提取为 private 方法。导出审计仍位于同一外层事务内。

同步更新 Task 2 的事务测试：反射断言目标改为 `createExportDownloadLink(SearchArchiveItemsRequest, Long)`；PostgreSQL 集成测试 mock `StorageObjectService` 与 `FileLinkService`，调用 `createExportDownloadLink(null, 9L)` 后仍断言真实 `am_archive_item_audit` 写入成功。再令 `fileLinkService.createUserLink` 抛异常，断言请求失败且本次 EXPORT 审计计数仍为 0，证明外层事务回滚。旧公开 `exportItems` 不再保留。

- [ ] **Step 6: 实现 Controller 和前端 API**

Controller 只保留导入 POST 与两个创建短链 POST，删除 `excelResponse`、直接模板 GET、导出文件流 POST/GET、Base64/Jackson 依赖。响应 record：

```java
public record ArchiveItemDownloadLinkResponse(String url, LocalDateTime expiresAt) {}
```

用统一私有 mapper 生成 `/api/v1/file-links/{code}:download`。前端接口：

```ts
interface ArchiveItemDownloadLinkResponse {
    url: string;
    expiresAt: string;
}

export async function downloadArchiveImportTemplate(categoryId: number): Promise<DownloadLink> {
    const response = await httpClient.post<ArchiveItemDownloadLinkResponse>(
        `/api/v1/archive-categories/${categoryId}/archive-items:createImportTemplateDownloadLink`,
    );
    return httpClient.download(response.url);
}

export async function exportArchiveRecords(query: SearchArchiveRecordsQuery): Promise<DownloadLink> {
    const response = await httpClient.post<ArchiveItemDownloadLinkResponse>(
        "/api/v1/archive-items:createExportDownloadLink",
        archiveRecordSearchRequest(query).body,
    );
    return httpClient.download(response.url);
}
```

删除 `encodeDownloadQuery`。页面继续调用现有 `downloadFromLink(link.href)`，不新增 Blob、filename 或 revoke 逻辑。

- [ ] **Step 7: 运行目标测试并审计直接流入口**

Run: `cd server && mise exec -- mvn -q -Dtest=ArchiveItemImportExportServiceTests,ArchiveItemExportTransactionIntegrationTests,ArchiveItemImportExportControllerTests,StorageObjectServiceTests,FileLinkDownloadControllerTests test`

Run: `pnpm --filter @archive-management/web test -- src/shared/api/archive.test.ts src/pages/archive-items/ArchiveItemManagementPage.error-state.test.ts`

Run: `rg -n 'archive-items:export|archive-items:importTemplate|encodeDownloadQuery|createObjectURL' server/src/main web/src/shared/api/archive-records.ts web/src/pages/archive-items || true`

Expected: 测试 PASS；rg 不匹配旧直接接口、Base64 编码或 Blob API。

- [ ] **Step 8: 格式化并提交**

Run: `cd server && mise exec -- mvn -q spotless:apply && cd .. && pnpm check:fix && git diff --check`

```bash
git add server/src/main/java/github/luckygc/am/module/archive/item/service/ArchiveItemImportExportService.java \
  server/src/main/java/github/luckygc/am/module/archive/item/web/ArchiveItemImportExportController.java \
  server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveItemImportExportServiceTests.java \
  server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveItemExportTransactionIntegrationTests.java \
  server/src/test/java/github/luckygc/am/module/archive/item/web/ArchiveItemImportExportControllerTests.java \
  server/src/test/java/github/luckygc/am/module/storage/web/FileLinkDownloadControllerTests.java \
  web/src/shared/api/archive-records.ts web/src/shared/api/archive.test.ts \
  web/src/pages/archive-items/ArchiveItemManagementPage.vue \
  web/src/pages/archive-items/ArchiveItemManagementPage.error-state.test.ts
git commit -m "fix: 统一生成文件用户短链"
```

### Task 5: 将规则追踪可见性和 cursor 分页下推到 MyBatis

**Files:**
- Modify: `server/src/test/java/github/luckygc/am/module/archive/rule/service/ArchiveLocalRuleServiceTests.java`
- Create: `server/src/test/java/github/luckygc/am/module/archive/rule/service/ArchiveRuleTraceMapperIntegrationTests.java`
- Modify: `server/src/main/java/github/luckygc/am/module/archive/mapper/ArchiveRuleTraceSearchCriteria.java`
- Modify: `server/src/main/java/github/luckygc/am/module/archive/mapper/ArchiveRuleMapper.java`
- Modify: `server/src/main/resources/mapper/archive/ArchiveRuleMapper.xml`
- Modify: `server/src/main/java/github/luckygc/am/module/archive/rule/service/ArchiveRuleTraceService.java`
- Modify: `server/src/main/java/github/luckygc/am/module/archive/rule/service/ArchiveLocalRuleService.java`

**Interfaces:**
- Consumes: `ArchiveDataScopeService.buildItemFilter(Long userId, Long categoryId, String fondsCode)`、`ArchiveCategoryService.listCategories(null)`、`PageRequest`。
- Produces: `ArchiveLocalRuleService.listRuleTraces(SearchArchiveRuleTracesRequest, PageRequest): CursorPageResponse<Map<String,Object>>`。

- [ ] **Step 1: 重写 Service 单元测试表达目标行为**

把逐行 `assertItemInDataScope` 测试替换为三组测试：

```java
@Test
@DisplayName("规则追踪一次编译分类范围并使用 limit 加一查询")
void listRuleTracesShouldCompileScopeBeforePaging() {
    when(dataScopeService.resolveUserDataScope(7L)).thenReturn(ResolvedArchiveDataScope.none());
    when(categoryService.listCategories(null)).thenReturn(List.of(category(11L, "DOC")));
    when(dataScopeService.buildItemFilter(7L, 11L, null))
            .thenReturn(ArchiveDataScopeFilter.fondsCodes(List.of("F001")));
    when(ruleMapper.listRuleTraces(any())).thenReturn(List.of(trace(3L), trace(2L), trace(1L)));

    CursorPageResponse<Map<String, Object>> page =
            service.listRuleTraces(searchRequest(7L), PageRequest.ofSize(2));

    assertThat(page.items()).extracting(row -> row.get("id")).containsExactly(3L, 2L);
    ArgumentCaptor<ArchiveRuleTraceSearchCriteria> captor =
            ArgumentCaptor.forClass(ArchiveRuleTraceSearchCriteria.class);
    verify(ruleMapper).listRuleTraces(captor.capture());
    assertThat(captor.getValue().page().rowLimit()).isEqualTo(3);
    assertThat(captor.getValue().itemScopes()).hasSize(1);
}
```

另加测试验证 `(LocalDateTime, Long)` cursor 被解析；含动态条件的 group 进入 itemScopes 但不进入 volumeScopes；全量用户不枚举分类。当前类型和方法不存在，测试编译必须先失败。

- [ ] **Step 2: 创建 Mapper PostgreSQL 集成测试数据**

创建 `ArchiveRuleTraceMapperIntegrationTests extends PostgreSqlContainerTest`，直接注入 `ArchiveRuleMapper` 与 `JdbcTemplate`。每个测试使用唯一 ID 段并在 `@AfterEach` 删除。至少覆盖：

1. 插入 501 条较新的、由其他用户创建的 `PROCESS` 追踪，再插入一条较旧、当前用户创建的追踪，`rowLimit=101` 时仍能返回旧追踪。
2. 插入相同 `created_at` 的三条可见追踪，第一页 limit+1 后以 `(created_at,id)` 查询下一页，断言无重复无遗漏。
3. `ARCHIVE_ITEM` 关联 `am_archive_item`，固定全宗范围可见；另一个全宗不可见。
4. 动态字段 scope 用经过测试构造的动态表与 `ArchiveSqlCondition` 过滤条目。
5. `ARCHIVE_VOLUME` 仅使用无动态条件的 group；只有动态条件的 group 不授予可见性。

测试构造条件使用新的精确接口：

```java
new ArchiveRuleTraceSearchCriteria(
        null,
        null,
        null,
        null,
        null,
        false,
        7L,
        itemScopes,
        volumeScopes,
        new ArchiveRuleTracePageWindow(false, null, null, 101))
```

- [ ] **Step 3: 运行测试并确认红灯**

Run: `cd server && mise exec -- mvn -q -Dtest=ArchiveLocalRuleServiceTests,ArchiveRuleTraceMapperIntegrationTests test`

Expected: FAIL 或 testCompile FAIL，因为 criteria、分页窗口和 Service 签名尚不存在；不得先修改生产代码绕过红灯。

- [ ] **Step 4: 定义 Mapper 条件对象**

把 `ArchiveRuleTraceSearchCriteria` 改为：

```java
public record ArchiveRuleTraceSearchCriteria(
        @Nullable Long schemeVersionId,
        @Nullable String triggerCode,
        @Nullable String objectTypeCode,
        @Nullable Long objectId,
        @Nullable String ruleType,
        boolean allData,
        Long userId,
        List<ArchiveRuleTraceTargetScope> itemScopes,
        List<ArchiveRuleTraceTargetScope> volumeScopes,
        ArchiveRuleTracePageWindow page) {

    public ArchiveRuleTraceSearchCriteria {
        itemScopes = List.copyOf(itemScopes);
        volumeScopes = List.copyOf(volumeScopes);
    }

    public record ArchiveRuleTraceTargetScope(
            String categoryCode, String tableName, List<ArchiveDataScopeSqlGroup> groups) {
        public ArchiveRuleTraceTargetScope {
            groups = List.copyOf(groups);
        }
    }

    public record ArchiveRuleTracePageWindow(
            boolean previous,
            @Nullable LocalDateTime cursorCreatedAt,
            @Nullable Long cursorId,
            int rowLimit) {}
}
```

- [ ] **Step 5: 实现数据库可见性和稳定窗口**

`ArchiveRuleMapper.listRuleTraces` 签名保持一个 criteria 参数。XML 使用别名 `t`，左连接 `am_archive_item i` 和 `am_archive_volume v`，并在筛选后加入：

```xml
<if test="!criteria.allData">
  and (
    (t.object_type_code = 'ARCHIVE_ITEM' and (
      <foreach collection="criteria.itemScopes" item="scope" separator=" or ">
        (i.category_code = #{scope.categoryCode} and (
          <foreach collection="scope.groups" item="group" separator=" or ">
            (
              <if test="group.fondsCodes != null and group.fondsCodes.size > 0">
                i.fonds_code in
                <foreach collection="group.fondsCodes" item="fondsCode"
                         open="(" separator="," close=")">#{fondsCode}</foreach>
              </if>
              <if test="group.securityLevelIds != null and group.securityLevelIds.size > 0">
                <if test="group.fondsCodes != null and group.fondsCodes.size > 0">and</if>
                i.security_level_id in
                <foreach collection="group.securityLevelIds" item="securityLevelId"
                         open="(" separator="," close=")">#{securityLevelId}</foreach>
              </if>
              <if test="group.retentionPeriodIds != null and group.retentionPeriodIds.size > 0">
                <if test="(group.fondsCodes != null and group.fondsCodes.size > 0)
                    or (group.securityLevelIds != null and group.securityLevelIds.size > 0)">and</if>
                i.retention_period_id in
                <foreach collection="group.retentionPeriodIds" item="retentionPeriodId"
                         open="(" separator="," close=")">#{retentionPeriodId}</foreach>
              </if>
              <if test="group.conditions != null and group.conditions.size > 0">
                <if test="(group.fondsCodes != null and group.fondsCodes.size > 0)
                    or (group.securityLevelIds != null and group.securityLevelIds.size > 0)
                    or (group.retentionPeriodIds != null and group.retentionPeriodIds.size > 0)">and</if>
                exists (
                  select 1 from ${scope.tableName} d
                  where d.id = i.id and d.deleted_flag = false
                  <foreach collection="group.conditions" item="condition">
                    <choose>
                      <when test="condition.operatorName == 'EQ'">
                        and d.${condition.columnName} = #{condition.value}
                      </when>
                      <when test="condition.operatorName == 'IN'">
                        and d.${condition.columnName} in
                        <foreach collection="condition.values" item="conditionValue"
                                 open="(" separator="," close=")">#{conditionValue}</foreach>
                      </when>
                      <when test="condition.operatorName == 'IS_NULL'">
                        and d.${condition.columnName} is null
                      </when>
                      <when test="condition.operatorName == 'IS_NOT_NULL'">
                        and d.${condition.columnName} is not null
                      </when>
                    </choose>
                  </foreach>
                )
              </if>
              <if test="(group.fondsCodes == null or group.fondsCodes.size == 0)
                  and (group.securityLevelIds == null or group.securityLevelIds.size == 0)
                  and (group.retentionPeriodIds == null or group.retentionPeriodIds.size == 0)
                  and (group.conditions == null or group.conditions.size == 0)">true</if>
            )
          </foreach>
        ))
      </foreach>
      <if test="criteria.itemScopes == null or criteria.itemScopes.size == 0">false</if>
    ))
    or (t.object_type_code = 'ARCHIVE_VOLUME' and (
      <foreach collection="criteria.volumeScopes" item="scope" separator=" or ">
        (v.category_code = #{scope.categoryCode} and (
          <foreach collection="scope.groups" item="group" separator=" or ">
            (
              <if test="group.fondsCodes != null and group.fondsCodes.size > 0">
                v.fonds_code in
                <foreach collection="group.fondsCodes" item="fondsCode"
                         open="(" separator="," close=")">#{fondsCode}</foreach>
              </if>
              <if test="group.securityLevelIds != null and group.securityLevelIds.size > 0">
                <if test="group.fondsCodes != null and group.fondsCodes.size > 0">and</if>
                v.security_level_id in
                <foreach collection="group.securityLevelIds" item="securityLevelId"
                         open="(" separator="," close=")">#{securityLevelId}</foreach>
              </if>
              <if test="group.retentionPeriodIds != null and group.retentionPeriodIds.size > 0">
                <if test="(group.fondsCodes != null and group.fondsCodes.size > 0)
                    or (group.securityLevelIds != null and group.securityLevelIds.size > 0)">and</if>
                v.retention_period_id in
                <foreach collection="group.retentionPeriodIds" item="retentionPeriodId"
                         open="(" separator="," close=")">#{retentionPeriodId}</foreach>
              </if>
              <if test="(group.fondsCodes == null or group.fondsCodes.size == 0)
                  and (group.securityLevelIds == null or group.securityLevelIds.size == 0)
                  and (group.retentionPeriodIds == null or group.retentionPeriodIds.size == 0)">true</if>
            )
          </foreach>
        ))
      </foreach>
      <if test="criteria.volumeScopes == null or criteria.volumeScopes.size == 0">false</if>
    ))
    or (t.object_type_code not in ('ARCHIVE_ITEM', 'ARCHIVE_VOLUME')
        and t.created_by = #{criteria.userId})
  )
</if>
```

itemScopes 复用 `ArchiveMapper.xml#listItemRelations` 已验证的 fonds/security/retention/dynamic `exists` 结构，动态表引用 `${scope.tableName}`、动态列引用 `${condition.columnName}`；两者均只能来自服务端元数据。volumeScopes 只生成 fonds/security/retention 条件，不生成动态表 `exists`。空 scopes 必须输出 `false`，空 group 必须输出 `true`，保持 fail closed。

cursor 与排序使用：

```xml
<if test="criteria.page.cursorCreatedAt != null and criteria.page.cursorId != null">
  <choose>
    <when test="criteria.page.previous">
      and (t.created_at, t.id) &gt; (#{criteria.page.cursorCreatedAt}, #{criteria.page.cursorId})
    </when>
    <otherwise>
      and (t.created_at, t.id) &lt; (#{criteria.page.cursorCreatedAt}, #{criteria.page.cursorId})
    </otherwise>
  </choose>
</if>
order by t.created_at
<choose><when test="criteria.page.previous">asc</when><otherwise>desc</otherwise></choose>,
         t.id
<choose><when test="criteria.page.previous">asc</when><otherwise>desc</otherwise></choose>
limit #{criteria.page.rowLimit}
```

- [ ] **Step 6: 实现 Service 的一次范围编译和 cursor 页组装**

`ArchiveRuleTraceService` 删除 `ArchiveItemReadService`、`ArchiveVolumeService` 及逐行异常过滤依赖，增加 `ArchiveCategoryService`。实现：

```java
@Transactional(readOnly = true)
public CursorPageResponse<Map<String, Object>> listRuleTraces(
        SearchArchiveRuleTracesRequest request, PageRequest pageRequest) {
    Long userId = AuthenticatedUsers.requireResolvedUserId(request.userId());
    boolean allData = dataScopeService.resolveUserDataScope(userId).allData();
    TraceScopes scopes = allData ? TraceScopes.empty() : traceScopes(userId);
    List<Map<String, Object>> rows =
            ruleMapper.listRuleTraces(criteria(request, pageRequest, userId, allData, scopes));
    return toCursorPage(rows, pageRequest);
}
```

`traceScopes` 对每个分类只调用一次 `buildItemFilter`：all filter 立即产生该分类空 group；empty 跳过；普通 filter 的全部 group 进入 itemScopes，只有 `conditions().isEmpty()` 的 group 进入 volumeScopes。动态表名使用：

```java
ArchiveDynamicTableNames.tableName(category, ArchiveLevel.ITEM)
```

cursor 必须恰好包含 `LocalDateTime createdAt` 和数值 `id`，否则抛：

```java
new BadRequestException("分页 cursor 无效", "cursor", "规则追踪 cursor 必须包含创建时间和数值 ID")
```

取 `pageRequest.size() + 1`，previous 查询结果反转后再映射；`self`、`prev`、`next` 的 values 均为 `[createdAt, id]`，由现有 `CursorPageResponseAdvice` 绑定请求指纹并编码。

- [ ] **Step 7: 更新业务 Service 转发签名**

在 `ArchiveLocalRuleService` 中：

```java
public CursorPageResponse<Map<String, Object>> listRuleTraces(
        SearchArchiveRuleTracesRequest request, PageRequest pageRequest) {
    return traceService.listRuleTraces(request, pageRequest);
}

public record SearchArchiveRuleTracesRequest(
        @Nullable Long schemeVersionId,
        @Nullable String triggerCode,
        @Nullable String objectTypeCode,
        @Nullable Long objectId,
        @Nullable ArchiveRuleType ruleType,
        @Nullable Long userId) {}
```

请求 record 删除 `limit`；分页只通过 `PageRequest` 传递。

- [ ] **Step 8: 运行目标测试并检查 SQL 安全**

Run: `cd server && mise exec -- mvn -q -Dtest=ArchiveLocalRuleServiceTests,ArchiveRuleTraceMapperIntegrationTests test`

Expected: 单元测试 PASS；Docker 可用时 mapper 集成测试全部 PASS。随后运行：

Run: `rg -n '\$\{' server/src/main/resources/mapper/archive/ArchiveRuleMapper.xml`

Expected: 只出现 `${scope.tableName}` 与 `${condition.columnName}`，二者来源均在 Service 中被分类元数据/数据范围编译，不出现客户端字符串拼接。

- [ ] **Step 9: 格式化并提交**

Run: `cd server && mise exec -- mvn -q spotless:apply && git diff --check`

```bash
git add server/src/main/java/github/luckygc/am/module/archive/mapper/ArchiveRuleTraceSearchCriteria.java \
  server/src/main/java/github/luckygc/am/module/archive/mapper/ArchiveRuleMapper.java \
  server/src/main/resources/mapper/archive/ArchiveRuleMapper.xml \
  server/src/main/java/github/luckygc/am/module/archive/rule/service/ArchiveRuleTraceService.java \
  server/src/main/java/github/luckygc/am/module/archive/rule/service/ArchiveLocalRuleService.java \
  server/src/test/java/github/luckygc/am/module/archive/rule/service/ArchiveLocalRuleServiceTests.java \
  server/src/test/java/github/luckygc/am/module/archive/rule/service/ArchiveRuleTraceMapperIntegrationTests.java
git commit -m "fix: 在数据库内分页规则追踪"
```

### Task 6: 对齐规则追踪 HTTP 与前端 cursor 交互

**Files:**
- Modify: `server/src/test/java/github/luckygc/am/infrastructure/web/ArchiveRuleControllerProblemDetailTests.java`
- Modify: `server/src/main/java/github/luckygc/am/module/archive/rule/web/ArchiveRuleController.java`
- Modify: `web/src/shared/types/archive-rules.ts`
- Modify: `web/src/shared/api/archive-rules.ts`
- Modify: `web/src/pages/archive-governance/ArchiveGovernanceFoundationPages.test.ts`
- Modify: `web/src/pages/archive-rule-traces/ArchiveRuleTracesPage.vue`

**Interfaces:**
- Consumes: Task 5 的 `listRuleTraces(request, pageRequest)`。
- Produces: `searchArchiveRuleTraces(query): Promise<CursorPageResponse<ArchiveRuleTraceDto>>` 与页面 cursor 导航。

- [ ] **Step 1: 先更新 Controller 测试**

在权限错误测试中传入 `PageRequest.ofSize(100)`；新增认证用户覆盖测试：mock Service 返回 `CursorPageResponse.withCursorValues(List.of(), 100, null, null, null, null, null)`，捕获 request 并断言 `userId=9`，同时 verify 传入原 `PageRequest`。当前 Controller 签名不接受 PageRequest，因此先编译失败。

- [ ] **Step 2: 先更新前端 API 与页面测试**

定义：

```ts
export interface SearchArchiveRuleTracesRequest {
    schemeVersionId?: number;
    triggerCode?: string;
    objectTypeCode?: string;
    objectId?: number;
    ruleType?: ArchiveRuleType;
}

export interface SearchArchiveRuleTracesQuery extends SearchArchiveRuleTracesRequest {
    limit?: number;
    cursor?: string;
    requestTotal?: boolean;
}
```

在 `ArchiveGovernanceFoundationPages.test.ts` 先写以下交互断言：首次点击查询调用业务 body + `limit=100` query；mock 返回 `next: "next-token"` 后点击下一页只改变 cursor；修改“每页条数”为 200 后清除 cursor；API reject 时显示 `RequestErrorState` 且点击重试复用已提交条件。

- [ ] **Step 3: 运行测试并确认红灯**

Run: `cd server && mise exec -- mvn -q -Dtest=ArchiveRuleControllerProblemDetailTests test`

Run: `pnpm --filter @archive-management/web test -- src/pages/archive-governance/ArchiveGovernanceFoundationPages.test.ts`

Expected: 后端编译或断言失败，前端找不到分页按钮/错误状态或调用参数不匹配。

- [ ] **Step 4: 更新 Controller 合同**

```java
@PostMapping("/api/v1/archive-rule-traces:search")
public CursorPageResponse<Map<String, Object>> searchRuleTraces(
        @RequestBody SearchArchiveRuleTracesRequest request,
        PageRequest page,
        Authentication authentication) {
    Long userId = requireManage(authentication);
    return ruleService.listRuleTraces(withUserId(request, userId), page);
}
```

`withUserId` 不再复制 `limit`。导入 `jakarta.data.page.PageRequest` 与 `CursorPageResponse`，删除该方法对 `CollectionResponse` 的使用（其他小列表仍保留 CollectionResponse）。

- [ ] **Step 5: 更新前端 API client**

```ts
export function searchArchiveRuleTraces(query: SearchArchiveRuleTracesQuery) {
    const { limit, cursor, requestTotal, ...body } = query;
    return httpClient.post<CursorPageResponse<ArchiveRuleTraceDto>>(
        `/api/v1/archive-rule-traces:search${queryString({ limit, cursor, requestTotal })}`,
        body,
    );
}
```

- [ ] **Step 6: 实现草稿、已提交请求与 CursorPagination**

页面状态固定为：

```ts
const limit = ref(100);
const committedQuery = ref<SearchArchiveRuleTracesRequest>();
const result = ref<CursorPageResponse<ArchiveRuleTraceDto>>();
const loadError = ref<string>();
```

`submitSearch` 校验后复制并 trim 表单业务字段到 `committedQuery`，调用 `load(undefined)`；`page(cursor)` 调用 `load(cursor)`；`limitChange(value)` 更新 limit 并调用 `load(undefined)`；`load` 只读取 committedQuery，不读取后续草稿变化。成功替换 result 并清空错误；失败保留上次 result、设置 `loadError` 并显示统一错误消息。

模板移除“条数”输入，表格下加入：

```vue
<RequestErrorState v-if="loadError" :message="loadError" @retry="load(undefined)" />
<CursorPagination
    v-if="result"
    :limit="limit"
    :prev="result.prev"
    :next="result.next"
    :loading="loading"
    @limit-change="limitChange"
    @page="page"
/>
```

- [ ] **Step 7: 运行目标测试并提交**

Run: `cd server && mise exec -- mvn -q -Dtest=ArchiveRuleControllerProblemDetailTests,ArchiveLocalRuleServiceTests test`

Run: `pnpm --filter @archive-management/web test -- src/pages/archive-governance/ArchiveGovernanceFoundationPages.test.ts src/shared/components/CursorPagination.test.ts`

Expected: 全部 PASS。

Run: `cd server && mise exec -- mvn -q spotless:apply && cd .. && pnpm check:fix && git diff --check`

```bash
git add server/src/main/java/github/luckygc/am/module/archive/rule/web/ArchiveRuleController.java \
  server/src/test/java/github/luckygc/am/infrastructure/web/ArchiveRuleControllerProblemDetailTests.java \
  web/src/shared/types/archive-rules.ts web/src/shared/api/archive-rules.ts \
  web/src/pages/archive-rule-traces/ArchiveRuleTracesPage.vue \
  web/src/pages/archive-governance/ArchiveGovernanceFoundationPages.test.ts
git commit -m "fix: 为规则追踪接入游标分页"
```

### Task 7: 固化前端生成声明

**Files:**
- Modify: `web/src/components.d.ts`

**Interfaces:**
- Consumes: `unplugin-vue-components` 在 `web/vite.config.ts` 中配置的 `dts: "src/components.d.ts"`。
- Produces: 连续运行 Vite+ 检查后不再变化的生成声明。

- [ ] **Step 1: 运行生成检查并观察当前漂移**

Run: `pnpm --filter @archive-management/web check`

Expected: 命令 PASS，但 `git diff -- web/src/components.d.ts` 显示生成器把 `export {};` 改为 `export {}`，并移除源码已不再使用的 `ElStatistic`。

- [ ] **Step 2: 接受生成器实际输出**

只保留生成器产生的 `components.d.ts` 修改，不手工补回分号或过期组件声明。检查差异中没有其他组件被意外删除。

- [ ] **Step 3: 连续验证稳定性**

Run: `pnpm --filter @archive-management/web check && cp web/src/components.d.ts /tmp/am-components.d.ts && pnpm --filter @archive-management/web check && cmp /tmp/am-components.d.ts web/src/components.d.ts`

Expected: 两次 check PASS，`cmp` 退出码为 0。

- [ ] **Step 4: 提交生成声明**

```bash
git add web/src/components.d.ts
git commit -m "chore: 同步前端组件声明"
```

### Task 8: 全量验证与工作树审计

**Files:**
- Verify only: all files from Tasks 1-7

**Interfaces:**
- Consumes: 所有修复提交。
- Produces: 可复核的完整测试结果与只剩既有 `target/` 的工作树状态。

- [ ] **Step 1: 验证 OpenSpec**

Run: `openspec validate --all --strict --no-interactive`

Expected: 所有 specs 与 changes 通过。

- [ ] **Step 2: 验证后端目标测试和完整测试**

Run: `cd server && mise exec -- mvn -q -Dtest=StorageObjectServiceTests,StorageObjectExpiredDataCleanerTests,ArchiveItemImportExportServiceTests,ArchiveItemExportTransactionIntegrationTests,ArchiveItemImportExportControllerTests,ArchiveLocalRuleServiceTests,ArchiveRuleTraceMapperIntegrationTests,ArchiveRuleControllerProblemDetailTests test`

Expected: 目标测试 PASS；记录 Testcontainers 测试是 PASS 还是因 Docker 不可用 SKIPPED。

Run: `cd server && mise exec -- mvn -q test`

Expected: Maven 退出码 0，无失败测试。

- [ ] **Step 3: 验证 Java 格式与编译**

Run: `cd server && mise exec -- mvn -q spotless:check -DskipTests test-compile`

Expected: Spotless 和 testCompile 均 PASS。

- [ ] **Step 4: 验证前端**

Run: `pnpm check`

Run: `pnpm test`

Run: `pnpm build`

Expected: 三条命令退出码均为 0；构建允许记录第三方 Rolldown pure annotation warning，但不得有项目源码错误。

- [ ] **Step 5: 验证源码约束和生成文件稳定性**

Run: `pnpm check:source-lines`

Run: `cp web/src/components.d.ts /tmp/am-components-final.d.ts && pnpm --filter @archive-management/web check && cmp /tmp/am-components-final.d.ts web/src/components.d.ts`

Expected: source-lines PASS，components 声明无二次漂移。

- [ ] **Step 6: 审计 API、SQL 和工作树**

Run: `rg -n 'archive-items:export|archive-items:importTemplate|exportItemsFromLink|encodeDownloadQuery|postDownload|createObjectURL' server/src/main web/src frontend-core/src || true`

Expected: 无匹配。

Run: `rg -n 'limit 500|\.stream\(\).*filter|assertItemInDataScope|assertVolumeInDataScope' server/src/main/java/github/luckygc/am/module/archive/rule server/src/main/resources/mapper/archive/ArchiveRuleMapper.xml || true`

Expected: 规则追踪查询路径无固定 500 截断和逐条可见性调用。

Run: `git diff --check && git status --short`

Expected: 无已跟踪文件修改；仅允许显示任务开始前已有的 `?? target/`。如出现其他文件，逐项核对来源，不得用 destructive git 命令清理用户内容。

如果全量验证暴露本计划范围内的格式、测试或类型遗漏，回到对应 Task 的失败测试步骤，做最小修正并重跑该 Task 与本 Task 的验证；不得用一个泛化的收尾提交掩盖未完成的测试闭环。最终再次运行 `git status --short`，确认没有把 `target/` 纳入任何提交。
