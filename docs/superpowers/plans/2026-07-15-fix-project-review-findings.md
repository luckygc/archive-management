# 项目审查问题修复实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复同步导出只读事务、GET 导出副作用、规则追踪先截断后过滤和前端生成声明漂移，并以 POST 下载和稳定 cursor 分页形成完整闭环。

**Architecture:** 导出继续同步执行，但使用可写事务并只暴露 POST custom method；前端核心 HTTP client 增加一个只负责 POST 二进制响应的最小方法。规则追踪由 Service 一次性编译当前用户的数据范围，MyBatis 在 PostgreSQL 中完成可见性过滤和 `(created_at, id)` 键集分页，Controller 与前端统一使用 `PageRequest` / `CursorPageResponse` 合同。

**Tech Stack:** Java 25、Spring Boot 4.1、Spring MVC、Spring Transaction、Jakarta Data、MyBatis、PostgreSQL 18/Testcontainers、Vue 3、TypeScript、Axios、Vite+、Vitest、OpenSpec。

## Global Constraints

- 始终使用中文交流、编写文档和注释；只修改本次审查确认的四个问题，不扩展相邻模块。
- 保留同步导出和现有 5000 行上限，不引入后台 Job、消息队列、适配层或配置开关。
- 项目自有导出仅保留 `POST /api/v1/archive-items:export`；不保留尚未发布的 GET 兼容分支。
- 规则追踪分页固定按 `created_at DESC, id DESC`，分页参数只使用 URL query 中的 `limit`、`cursor`、`requestTotal`。
- 动态档案表名和字段名只能来自已经校验的分类元数据与数据范围 SQL 条件，不接收客户端标识符。
- 固定实体审计仍通过 Jakarta Data Repository 写入；规则追踪的动态数据范围与复杂分页继续由 MyBatis 承担。
- Java 格式以 `server/` 下 Spotless 的 google-java-format AOSP 风格为准；前端使用 Vite+，不得启动开发服务器。
- 现有未跟踪 `target/` 不属于本次工作，任何提交都不得包含它。

---

## 文件结构

- 修改 `openspec/changes/add-archive-data-scope-permissions-mvp-closure/specs/archive-import-export/spec.md`：把导出验收合同从浏览器 GET 链接改为 POST JSON + 二进制响应。
- 修改 `openspec/changes/add-archive-governance-foundation/specs/archive-local-rule-engine/spec.md`：明确规则追踪使用数据范围过滤后的稳定 cursor 分页。
- 修改 `server/src/main/java/github/luckygc/am/module/archive/item/service/ArchiveItemImportExportService.java`：导出事务改为可写。
- 修改 `server/src/main/java/github/luckygc/am/module/archive/item/web/ArchiveItemImportExportController.java`：删除 GET 导出及 Base64 解码依赖。
- 修改 `server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveItemImportExportServiceTests.java`：固定导出方法必须使用可写事务。
- 创建 `server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveItemExportTransactionIntegrationTests.java`：在 PostgreSQL 中证明导出成功写入审计。
- 修改 `server/src/test/java/github/luckygc/am/module/archive/item/web/ArchiveItemImportExportControllerTests.java`：只验证 POST 请求体和认证用户。
- 修改 `frontend-core/src/api/client.ts`：增加 POST 二进制下载能力和 Content-Disposition 文件名解析。
- 修改 `frontend-core/src/api/client.test.ts`：覆盖 POST、CSRF、Blob URL、ProblemDetail 与文件名。
- 修改 `web/src/shared/api/archive-records.ts`：导出请求改为 POST JSON，不再 Base64 编码查询。
- 修改 `web/src/shared/api/archive.test.ts`：固定导出 URL、方法和请求体。
- 修改 `web/src/pages/archive-items/downloadFromLink.ts`：支持 `download` 文件名并在点击后释放 Blob URL。
- 修改 `web/src/pages/archive-items/ArchiveItemManagementPage.vue`：使用 POST 下载结果。
- 修改相关档案管理页面测试：验证导出触发和 URL 释放。
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
- Modify: `openspec/changes/add-archive-governance-foundation/specs/archive-local-rule-engine/spec.md:131-145`

**Interfaces:**
- Consumes: 已批准设计中的 POST 导出与规则追踪 cursor 分页合同。
- Produces: 后端 Controller、前端 client 和测试共同遵守的验收真相源。

- [ ] **Step 1: 运行当前严格校验建立基线**

Run: `openspec validate --all --strict --no-interactive`

Expected: 所有现有 specs 与 changes 均通过；若基线失败，只记录与本任务无关的既有失败，不修改相邻规格。

- [ ] **Step 2: 修改导出场景为 POST 二进制合同**

将“导出查询结果”场景中的前端 GET 链接约束替换为以下明确断言：

```markdown
- **AND** 客户端 SHALL 使用 `POST /api/v1/archive-items:export` 在 JSON 请求体中提交当前查询条件
- **AND** 服务端 SHALL 在同一成功事务中生成二进制 Excel 并写入导出审计
- **AND** 客户端 SHALL 读取成功响应为 `Blob`、触发对象 URL 下载并在触发后释放对象 URL
```

- [ ] **Step 3: 补充规则追踪分页验收场景**

在“查询规则追踪”后增加：

```markdown
#### Scenario: 数据范围内稳定翻页

- **WHEN** 当前用户查询规则追踪且更晚的记录中包含超过一页的不可见记录
- **THEN** 系统 SHALL 在数据库中先应用当前用户功能权限和数据范围，再按 `created_at DESC, id DESC` 返回 cursor 页
- **AND** 相邻页面 SHALL NOT 因不可见记录而漏掉可见追踪
- **AND** 相邻页面 SHALL NOT 返回重复追踪
```

- [ ] **Step 4: 验证规格并提交**

Run: `openspec validate --all --strict --no-interactive`

Expected: 输出显示所有 specs 与 changes 均有效，无 strict validation error。

```bash
git add openspec/changes/add-archive-data-scope-permissions-mvp-closure/specs/archive-import-export/spec.md \
  openspec/changes/add-archive-governance-foundation/specs/archive-local-rule-engine/spec.md
git commit -m "docs: 修正规则追踪与导出合同"
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

### Task 3: 删除 GET 导出并提供 POST 二进制下载

**Files:**
- Modify: `server/src/test/java/github/luckygc/am/module/archive/item/web/ArchiveItemImportExportControllerTests.java`
- Modify: `server/src/main/java/github/luckygc/am/module/archive/item/web/ArchiveItemImportExportController.java`
- Modify: `frontend-core/src/api/client.test.ts`
- Modify: `frontend-core/src/api/client.ts`
- Modify: `web/src/shared/api/archive.test.ts`
- Modify: `web/src/shared/api/archive-records.ts`
- Modify: `web/src/pages/archive-items/downloadFromLink.ts`
- Modify: `web/src/pages/archive-items/ArchiveItemManagementPage.vue`
- Modify: `web/src/pages/archive-items/ArchiveItemManagementPage.error-state.test.ts`

**Interfaces:**
- Consumes: `POST /api/v1/archive-items:export` 和 `SearchArchiveRecordsRequest`。
- Produces: `httpClient.postDownload(path, body): Promise<DownloadedFile>`，其中 `DownloadedFile` 为 `{ href: string; filename?: string }`。

- [ ] **Step 1: 把后端 Controller 测试改为 POST 请求体语义**

删除 Base64 测试，新增：

```java
@Test
@DisplayName("POST 导出使用请求体查询条件和认证用户")
void exportItemsShouldUseBodyAndAuthenticatedUser() {
    SearchArchiveItemsRequest request =
            new SearchArchiveItemsRequest(1L, "F001", null, null, null, null, null, null);
    when(importExportService.exportItems(request, 9L))
            .thenReturn(new ArchiveExcelFile("archive-export.xlsx", new byte[] {1, 2}));

    ResponseEntity<?> response = controller.exportItems(request, authentication(9L));

    assertThat(response.getHeaders().getContentDisposition().getFilename())
            .isEqualTo("archive-export.xlsx");
    verify(importExportService).exportItems(request, 9L);
}
```

- [ ] **Step 2: 写前端核心 POST 下载失败测试**

在 `frontend-core/src/api/client.test.ts` 使用 `window.fetch` mock 返回带 Content-Disposition 的 XLSX 响应，并断言：

```ts
const createObjectURL = vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:archive-export");
document.cookie = "XSRF-TOKEN=csrf-token";

await expect(httpClient.postDownload("/api/v1/archive-items:export", { categoryId: 1 }))
    .resolves.toEqual({ href: "blob:archive-export", filename: "archive-export.xlsx" });
expect(fetchSpy).toHaveBeenCalledWith(
    expect.any(Request),
);
expect(createObjectURL).toHaveBeenCalledWith(expect.any(Blob));
```

从传给 fetch adapter 的请求中同时断言 method 为 `POST`、JSON body 为 `{"categoryId":1}`、`X-XSRF-TOKEN` 已设置。当前 `postDownload` 不存在，因此测试必须先失败。

- [ ] **Step 3: 写 web API 失败测试**

把 `exportArchiveRecords` 加入 `web/src/shared/api/archive.test.ts` import，并断言：

```ts
httpClientMock.postDownload.mockResolvedValue({
    href: "blob:archive-export",
    filename: "archive-export.xlsx",
});

await exportArchiveRecords({ categoryId: 1, keyword: "合同", limit: 100, cursor: "ignored" });

expect(httpClientMock.postDownload).toHaveBeenCalledWith("/api/v1/archive-items:export", {
    categoryId: 1,
    keyword: "合同",
});
expect(httpClientMock.download).not.toHaveBeenCalled();
```

把 mock 补上 `postDownload: vi.fn()`。`limit`、`cursor`、`requestTotal` 不属于同步导出查询体，继续由 `archiveRecordSearchRequest` 去除。

- [ ] **Step 4: 运行测试并确认红灯**

Run: `pnpm --filter @archive-management/frontend-core test -- client.test.ts && pnpm --filter @archive-management/web test -- src/shared/api/archive.test.ts`

Expected: FAIL，原因分别为 `postDownload is not a function` 或方法未被调用。

- [ ] **Step 5: 实现最小 POST 下载方法**

在 `frontend-core/src/api/client.ts` 增加：

```ts
export interface DownloadedFile extends DownloadLink {
    filename?: string;
}

postDownload(path: string, body?: RequestBody) {
    return requestDownload(path, withBody({}, "POST", body));
},

async function requestDownload(path: string, init: RequestInit): Promise<DownloadedFile> {
    try {
        const response = await axiosClient.request<Blob>({
            ...axiosConfig(path, init),
            responseType: "blob",
        });
        const blob = response.data instanceof Blob ? response.data : new Blob([response.data]);
        return {
            href: URL.createObjectURL(blob),
            filename: attachmentFilename(
                response.headers.get("content-disposition")?.toString(),
            ),
        };
    } catch (error) {
        throw await toDownloadHttpClientError(error);
    }
}

function attachmentFilename(value?: string) {
    if (!value) return undefined;
    const encoded = /filename\*=UTF-8''([^;]+)/i.exec(value)?.[1];
    if (encoded) return decodeURIComponent(encoded).split(/[\\/]/).at(-1);
    return /filename="?([^";]+)"?/i.exec(value)?.[1]?.trim().split(/[\\/]/).at(-1);
}

async function toDownloadHttpClientError(error: unknown) {
    if (axios.isAxiosError(error) && error.response?.data instanceof Blob) {
        const response = error.response;
        const body = problemDetailBody(await response.data.text());
        if (body) {
            const fieldViolations =
                body.fieldViolations?.filter((violation) => violation.message) ?? [];
            const message =
                fieldViolations.length > 0
                    ? fieldViolations.map((violation) => violation.message).join("；")
                    : body.detail || body.title;
            return new HttpClientError(
                message || `请求失败：${response.status}`,
                response.status,
                body.code,
                fieldViolations,
                body.traceId,
            );
        }
    }
    return toHttpClientError(error);
}
```

`attachmentFilename` 只解析标准 `filename=` 与 UTF-8 `filename*=`，去除包裹引号；`toDownloadHttpClientError` 在错误响应为 Blob 时先读取文本，再复用现有 ProblemDetail 转换。不要改变现有安全 GET `download()` 的同步链接语义。

- [ ] **Step 6: 删除后端 GET 合同**

从 `ArchiveItemImportExportController` 删除 `exportItemsFromLink`、`decodeExportQuery`、`JsonMapper` 字段和构造参数，以及 `Base64`、`StandardCharsets`、Jackson、GET 导出所需 import。构造器变为：

```java
public ArchiveItemImportExportController(ArchiveItemImportExportService importExportService) {
    this.importExportService = importExportService;
}
```

保留现有 POST 方法和 `excelResponse`。

- [ ] **Step 7: 切换 web API 与触发器**

`exportArchiveRecords` 返回 Promise，并只提交业务请求体：

```ts
export function exportArchiveRecords(query: SearchArchiveRecordsQuery) {
    return httpClient.postDownload(
        "/api/v1/archive-items:export",
        archiveRecordSearchRequest(query).body,
    );
}
```

删除 `encodeDownloadQuery`。把下载工具改为：

```ts
export function downloadFromLink(href: string, filename?: string) {
    const anchor = document.createElement("a");
    anchor.href = href;
    if (filename) anchor.download = filename;
    anchor.click();
    if (href.startsWith("blob:")) URL.revokeObjectURL(href);
}
```

页面导出调用改为：

```ts
const file = await exportArchiveRecords({
    ...query,
    orderBy: orderBy.value.length ? orderBy.value : undefined,
});
downloadFromLink(file.href, file.filename);
```

- [ ] **Step 8: 补页面 URL 释放测试并运行窄测试**

在 `ArchiveItemManagementPage.error-state.test.ts` 的导出成功用例中令 API 返回 `{ href: "blob:archive-export", filename: "archive-export.xlsx" }`，spy `HTMLAnchorElement.prototype.click` 与 `URL.revokeObjectURL`，断言点击、文件名和释放均发生。

Run: `cd server && mise exec -- mvn -q -Dtest=ArchiveItemImportExportControllerTests test`

Run: `pnpm --filter @archive-management/frontend-core test -- client.test.ts && pnpm --filter @archive-management/web test -- src/shared/api/archive.test.ts src/pages/archive-items/ArchiveItemManagementPage.error-state.test.ts`

Expected: 所有目标测试 PASS；后端测试源码中不再出现 `exportItemsFromLink`，前端导出 URL 中不再出现 `query=`。

- [ ] **Step 9: 格式化并提交**

Run: `cd server && mise exec -- mvn -q spotless:apply`

Run: `pnpm check:fix && git diff --check`

```bash
git add server/src/main/java/github/luckygc/am/module/archive/item/web/ArchiveItemImportExportController.java \
  server/src/test/java/github/luckygc/am/module/archive/item/web/ArchiveItemImportExportControllerTests.java \
  frontend-core/src/api/client.ts frontend-core/src/api/client.test.ts \
  web/src/shared/api/archive-records.ts web/src/shared/api/archive.test.ts \
  web/src/pages/archive-items/downloadFromLink.ts \
  web/src/pages/archive-items/ArchiveItemManagementPage.vue \
  web/src/pages/archive-items/ArchiveItemManagementPage.error-state.test.ts
git commit -m "fix: 使用 POST 导出档案文件"
```

### Task 4: 将规则追踪可见性和 cursor 分页下推到 MyBatis

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

### Task 5: 对齐规则追踪 HTTP 与前端 cursor 交互

**Files:**
- Modify: `server/src/test/java/github/luckygc/am/infrastructure/web/ArchiveRuleControllerProblemDetailTests.java`
- Modify: `server/src/main/java/github/luckygc/am/module/archive/rule/web/ArchiveRuleController.java`
- Modify: `web/src/shared/types/archive-rules.ts`
- Modify: `web/src/shared/api/archive-rules.ts`
- Modify: `web/src/pages/archive-governance/ArchiveGovernanceFoundationPages.test.ts`
- Modify: `web/src/pages/archive-rule-traces/ArchiveRuleTracesPage.vue`

**Interfaces:**
- Consumes: Task 4 的 `listRuleTraces(request, pageRequest)`。
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

### Task 6: 固化前端生成声明

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

### Task 7: 全量验证与工作树审计

**Files:**
- Verify only: all files from Tasks 1-6

**Interfaces:**
- Consumes: 所有修复提交。
- Produces: 可复核的完整测试结果与只剩既有 `target/` 的工作树状态。

- [ ] **Step 1: 验证 OpenSpec**

Run: `openspec validate --all --strict --no-interactive`

Expected: 所有 specs 与 changes 通过。

- [ ] **Step 2: 验证后端目标测试和完整测试**

Run: `cd server && mise exec -- mvn -q -Dtest=ArchiveItemImportExportServiceTests,ArchiveItemExportTransactionIntegrationTests,ArchiveItemImportExportControllerTests,ArchiveLocalRuleServiceTests,ArchiveRuleTraceMapperIntegrationTests,ArchiveRuleControllerProblemDetailTests test`

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

Run: `rg -n 'GetMapping\("/api/v1/archive-items:export"|exportItemsFromLink|encodeDownloadQuery' server web frontend-core || true`

Expected: 无匹配。

Run: `rg -n 'limit 500|\.stream\(\).*filter|assertItemInDataScope|assertVolumeInDataScope' server/src/main/java/github/luckygc/am/module/archive/rule server/src/main/resources/mapper/archive/ArchiveRuleMapper.xml || true`

Expected: 规则追踪查询路径无固定 500 截断和逐条可见性调用。

Run: `git diff --check && git status --short`

Expected: 无已跟踪文件修改；仅允许显示任务开始前已有的 `?? target/`。如出现其他文件，逐项核对来源，不得用 destructive git 命令清理用户内容。

如果全量验证暴露本计划范围内的格式、测试或类型遗漏，回到对应 Task 的失败测试步骤，做最小修正并重跑该 Task 与本 Task 的验证；不得用一个泛化的收尾提交掩盖未完成的测试闭环。最终再次运行 `git status --short`，确认没有把 `target/` 纳入任何提交。
