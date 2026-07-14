# Close Archive PC MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 关闭档案搜索、档案管理、案卷、关系、明细行、权限和工作台从后端到 PC 的 MVP 使用缺口。

**Architecture:** 保持 Vue PC → Spring Boot 模块化单体 → PostgreSQL/S3 的既有架构。已有后端合同直接接入 PC；只有明细行数据和真实工作台摘要缺少后端合同，需要在 archive/item 子域内补齐 Service、Controller 和 MyBatis SQL。所有敏感操作继续由服务端权限与数据范围守护。

**Tech Stack:** Vue 3、TypeScript、Element Plus、Pinia、Vite+ Test、Spring Boot、Jakarta Data、MyBatis、PostgreSQL、JUnit 5、Mockito、Testcontainers、OpenSpec。

## Global Constraints

- 依赖版本只允许升级，不因 Beta、Milestone、RC 或预发布标签降级、替换或回退依赖。
- 不新增通用 CRUD 框架、第二套组件体系、前端权限真相源或项目级基础设施适配层。
- HTTP 请求 DTO 使用动作明确的 `Request`，响应使用视图明确的 `Response`；分页使用现有游标合同。
- 固定表继续使用 Jakarta Data，动态明细表和统计继续使用 MyBatis。
- Java 变更经 Spotless AOSP 格式化；字符串判空使用 `StringUtils`；新增可空类型使用 JSpecify。
- 每项行为变更必须先观察定向测试按预期失败，再写最小实现并观察测试通过。
- 前端不启动开发服务器；使用 `pnpm check`、`pnpm test` 和生产构建验证。
- 当前环境 `.git` 只读时保留未提交改动并记录建议提交信息，不尝试绕过文件系统限制。

---

### Task 1: 建立 `close-archive-pc-mvp` OpenSpec 合同

**Files:**
- Create: `openspec/changes/close-archive-pc-mvp/proposal.md`
- Create: `openspec/changes/close-archive-pc-mvp/design.md`
- Create: `openspec/changes/close-archive-pc-mvp/specs/archive-pc-mvp/spec.md`
- Create: `openspec/changes/close-archive-pc-mvp/tasks.md`
- Reference: `docs/superpowers/specs/2026-07-14-archive-management-full-closure-design.md`

**Interfaces:**
- Consumes: 已确认总设计中的 `close-archive-pc-mvp` 范围。
- Produces: 可由 `openspec instructions apply --change close-archive-pc-mvp --json` 驱动的任务合同。

- [ ] **Step 1: 创建变更并读取制品指令**

```bash
openspec new change close-archive-pc-mvp
openspec status --change close-archive-pc-mvp --json
openspec instructions proposal --change close-archive-pc-mvp --json
```

Expected: 新变更存在，proposal 制品状态为 ready。

- [ ] **Step 2: 按指令写入 proposal、design、spec 和 tasks**

规格必须显式包含以下可验证场景：超过 100 条游标翻页、查询条件变更清游标、锁定解锁删除、物理字段与参考数据编辑、案卷管理、关系维护、明细表定义与行数据、权限菜单与直接访问、真实工作台摘要、原位错误重试。

- [ ] **Step 3: 运行严格校验**

```bash
openspec validate close-archive-pc-mvp --strict
```

Expected: 变更校验通过且无 warning。

- [ ] **Step 4: 提交规格**

```bash
git add openspec/changes/close-archive-pc-mvp
git commit -m "docs: 定义 PC MVP 收口合同"
```

### Task 2: 档案搜索与管理接入游标分页

**Files:**
- Modify: `web/src/pages/archive-items/useArchiveItemSearch.ts`
- Create: `web/src/pages/archive-items/useArchiveItemSearch.test.ts`
- Modify: `web/src/pages/archive-items/ArchiveItemManagementPage.vue`
- Modify: `web/src/pages/archive-items/ArchiveItemManagementPage.test.ts`
- Modify: `web/src/pages/archive-library/ArchiveLibraryPage.vue`
- Create: `web/src/pages/archive-library/ArchiveLibraryPage.test.ts`
- Modify: `web/src/pages/archive-library/ArchiveResultTable.vue`
- Modify: `web/src/pages/archive-library/ArchiveResultTable.test.ts`
- Reuse: `web/src/shared/components/CursorPagination.vue`

**Interfaces:**
- Consumes: `SearchArchiveRecordsQuery.limit/cursor` 和 `ArchiveRecordListDto.prev/next`。
- Produces: `limit: Ref<number>`、`page(cursor: string)`、`limitChange(limit: number)`、`loadError: Ref<string | undefined>`。

- [ ] **Step 1: 写管理页组合式函数失败测试**

```ts
it("翻页只改变游标并复用已提交查询和排序", async () => {
    mocks.searchArchiveRecords.mockResolvedValue({ fields: [], items: [], next: "next-2" });
    const search = useArchiveItemSearch();
    search.submit({ categoryId: 7, conditions: [], relatedGroups: [] });
    await flushPromises();
    search.orderResults([{ field: "archiveYear", direction: "DESC" }]);
    await flushPromises();
    search.page("next-2");
    await flushPromises();
    expect(mocks.searchArchiveRecords).toHaveBeenLastCalledWith({
        categoryId: 7,
        conditions: undefined,
        relatedGroups: undefined,
        keyword: undefined,
        orderBy: [{ field: "archiveYear", direction: "DESC" }],
        limit: 100,
        cursor: "next-2",
    });
});

it("修改页大小从已提交查询第一页重新加载", async () => {
    const search = useArchiveItemSearch();
    search.limitChange(200);
    expect(search.limit.value).toBe(200);
    expect(mocks.searchArchiveRecords).toHaveBeenLastCalledWith(
        expect.objectContaining({ limit: 200, cursor: undefined }),
    );
});
```

- [ ] **Step 2: 运行测试并确认按预期失败**

```bash
pnpm --filter @archive-management/web test -- useArchiveItemSearch.test.ts
```

Expected: FAIL，原因是 `page`、`limitChange` 或 `limit` 尚不存在。

- [ ] **Step 3: 实现显式分页状态**

```ts
const limit = ref(100);
const cursor = ref<string>();
const loadError = ref<string>();

async function execute(query: SearchArchiveRecordsQuery, nextCursor?: string) {
    loading.value = true;
    loadError.value = undefined;
    try {
        cursor.value = nextCursor;
        result.value = await searchArchiveRecords({
            ...query,
            orderBy: orderBy.value.length ? orderBy.value : undefined,
            limit: limit.value,
            cursor: nextCursor,
        });
    } catch (error) {
        loadError.value = errorMessage(error, "查询失败");
    } finally {
        loading.value = false;
    }
}

function page(nextCursor: string) {
    if (committedQuery.value) void execute(committedQuery.value, nextCursor);
}

function limitChange(nextLimit: number) {
    limit.value = nextLimit;
    if (committedQuery.value) void execute(committedQuery.value);
}
```

在搜索页实现相同的显式状态，不抽取带 loader 参数的通用 helper。两个页面的查询语义分别是管理搜索和全文发现，保持各自可读控制流。

- [ ] **Step 4: 在结果表下渲染分页与原位错误**

```vue
<el-alert v-if="loadError" :title="loadError" type="error" show-icon>
    <template #default><el-button link @click="refresh">重试</el-button></template>
</el-alert>
<ArchiveResultTable v-else-if="result" ... />
<CursorPagination
    v-if="result"
    :limit="limit"
    :prev="result.prev"
    :next="result.next"
    :loading="loading"
    @page="page"
    @limit-change="limitChange"
/>
```

- [ ] **Step 5: 运行定向与全体前端测试**

```bash
pnpm --filter @archive-management/web test -- useArchiveItemSearch.test.ts ArchiveLibraryPage.test.ts ArchiveItemManagementPage.test.ts ArchiveResultTable.test.ts
pnpm test
```

Expected: 定向测试和现有前端测试全部通过。

- [ ] **Step 6: 提交分页闭环**

```bash
git add web/src/pages/archive-items web/src/pages/archive-library
git commit -m "fix: 接通档案列表游标分页"
```

### Task 3: 补齐删除、锁定和解锁操作

**Files:**
- Modify: `web/src/shared/api/archive-records.ts`
- Modify: `web/src/shared/api/archive.test.ts`
- Create: `web/src/pages/archive-items/useArchiveItemLifecycle.ts`
- Create: `web/src/pages/archive-items/useArchiveItemLifecycle.test.ts`
- Create: `web/src/pages/archive-items/ArchiveItemRowActions.vue`
- Modify: `web/src/pages/archive-items/ArchiveItemManagementPage.vue`
- Modify: `web/src/pages/archive-items/ArchiveItemManagementPage.test.ts`

**Interfaces:**
- Produces: `deleteArchiveRecord(id, reason?)`、`lock(id)`、`unlock(id)`、`remove(id)` 和 `busyAction`。

- [ ] **Step 1: 写 API 与生命周期失败测试**

```ts
it("删除档案使用资源 DELETE 和原因请求体", async () => {
    await deleteArchiveRecord(9, "重复数据");
    expect(httpClient.request).toHaveBeenCalledWith("/api/v1/archive-items/9", {
        method: "DELETE",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ reason: "重复数据" }),
    });
});

it("锁定确认后刷新当前查询", async () => {
    mocks.messageBox.prompt.mockResolvedValue({ value: "整理期间冻结" });
    const lifecycle = useArchiveItemLifecycle(refresh);
    await lifecycle.lock(9);
    expect(mocks.lockArchiveRecord).toHaveBeenCalledWith(9, "整理期间冻结");
    expect(refresh).toHaveBeenCalledTimes(1);
});
```

- [ ] **Step 2: 运行并观察缺失函数失败**

```bash
pnpm --filter @archive-management/web test -- archive.test.ts useArchiveItemLifecycle.test.ts
```

Expected: FAIL，原因是删除 API 和生命周期 composable 尚不存在。

- [ ] **Step 3: 实现最小生命周期调用**

```ts
export function deleteArchiveRecord(id: number, reason?: string) {
    return httpClient.request<void>(`/api/v1/archive-items/${id}`, {
        method: "DELETE",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ reason }),
    });
}
```

`useArchiveItemLifecycle` 使用 `ElMessageBox.prompt/confirm`，成功后调用传入的 `refresh()`；错误统一通过 `errorMessage` 展示，不自行推断 HTTP 状态。

- [ ] **Step 4: 用行操作组件表达状态和权限**

```vue
<el-button v-if="locked" link :disabled="!canLock || busy" @click="$emit('unlock')">解锁</el-button>
<el-button v-else link :disabled="!canLock || busy" @click="$emit('lock')">锁定</el-button>
<el-button link type="danger" :disabled="!canDelete || locked || busy" @click="$emit('delete')">删除</el-button>
```

- [ ] **Step 5: 验证生命周期测试**

```bash
pnpm --filter @archive-management/web test -- archive.test.ts useArchiveItemLifecycle.test.ts ArchiveItemManagementPage.test.ts
```

Expected: 删除、锁定、解锁、权限禁用和刷新测试通过。

- [ ] **Step 6: 提交生命周期操作**

```bash
git add web/src/shared/api web/src/pages/archive-items
git commit -m "feat: 补齐档案生命周期操作"
```

### Task 4: 编辑物理字段、密级和保管期限

**Files:**
- Modify: `web/src/pages/archive-items/ArchiveItemManagementPage.vue`
- Modify: `web/src/pages/archive-items/ArchiveItemEditorDrawer.vue`
- Modify: `web/src/pages/archive-items/ArchiveItemComponents.test.ts`
- Modify: `web/src/pages/archive-items/ArchiveItemManagementPage.test.ts`
- Reuse: `web/src/pages/archive-library/DynamicArchiveFields.vue`
- Reuse: `web/src/shared/api/archive-metadata.ts`

**Interfaces:**
- Consumes: `ArchiveRecordDetailDto.physicalFields/physicalFieldValues`、`listArchiveSecurityLevels(true)`、`listArchiveRetentionPeriods(true)`。
- Produces: 编辑表单字段 `securityLevelId`、`retentionPeriodId`、`physicalFields`。

- [ ] **Step 1: 写失败测试**

```ts
it("编辑时回填并提交物理字段、密级和保管期限", async () => {
    mocks.getArchiveRecord.mockResolvedValue(detailWithPhysicalFields);
    await user.click(screen.getByRole("button", { name: "编辑" }));
    await user.clear(screen.getByLabelText("盒号"));
    await user.type(screen.getByLabelText("盒号"), "BOX-009");
    await user.click(screen.getByRole("button", { name: "保存" }));
    expect(mocks.updateArchiveRecord).toHaveBeenCalledWith(
        9,
        expect.objectContaining({
            securityLevelId: 2,
            retentionPeriodId: 3,
            physicalFields: { box_no: "BOX-009" },
        }),
    );
});
```

- [ ] **Step 2: 运行并确认当前固定空对象导致失败**

```bash
pnpm --filter @archive-management/web test -- ArchiveItemManagementPage.test.ts ArchiveItemComponents.test.ts
```

Expected: FAIL，提交值仍为 `physicalFields: {}` 或控件不存在。

- [ ] **Step 3: 扩充表单与编辑器**

```ts
const editorForm = reactive({
    categoryId: undefined as number | undefined,
    fondsCode: "",
    archiveNo: "",
    archiveYear: new Date().getFullYear(),
    electronicStatus: "DRAFT" as ArchiveElectronicStatus,
    securityLevelId: undefined as number | undefined,
    retentionPeriodId: undefined as number | undefined,
    physicalFields: {} as Record<string, unknown>,
    dynamicFields: {} as Record<string, unknown>,
});
```

编辑 Drawer 用两个 `el-select` 维护参考数据，并用 `DynamicArchiveFields` 分别渲染物理字段和动态业务字段；详情模式禁用全部控件。

- [ ] **Step 4: 提交规范化后的两组字段**

```ts
const common = {
    ...fixedValues,
    securityLevelId: editorForm.securityLevelId,
    retentionPeriodId: editorForm.retentionPeriodId,
    physicalFields: normalizeArchiveRecordFormValues({
        dynamicFields: editorForm.physicalFields,
    }).dynamicFields,
    dynamicFields: normalizeArchiveRecordFormValues({
        dynamicFields: editorForm.dynamicFields,
    }).dynamicFields,
};
```

- [ ] **Step 5: 验证并提交**

```bash
pnpm --filter @archive-management/web test -- ArchiveItemManagementPage.test.ts ArchiveItemComponents.test.ts DynamicArchiveFields.test.ts
git add web/src/pages/archive-items
git commit -m "feat: 完整维护档案固定参考与物理字段"
```

### Task 5: 开发案卷管理 PC 页面

**Files:**
- Create: `web/src/shared/types/archive-volumes.ts`
- Create: `web/src/shared/api/archive-volumes.ts`
- Create: `web/src/shared/api/archive-volumes.test.ts`
- Create: `web/src/pages/archive-volumes/ArchiveVolumesPage.vue`
- Create: `web/src/pages/archive-volumes/ArchiveVolumesPage.test.ts`
- Create: `web/src/pages/archive-volumes/ArchiveVolumeEditorDrawer.vue`
- Create: `web/src/pages/archive-volumes/ArchiveVolumeItemsDrawer.vue`
- Modify: `web/src/app/routes.ts`
- Modify: `web/src/app/routes.test.ts`

**Interfaces:**
- Consumes: `GET/POST /api/v1/archive-volumes`、`GET /api/v1/archive-volumes/{id}`、`POST /api/v1/archive-volumes/{id}:addItem`。
- Produces: `ArchiveVolumeResponse`、`CreateArchiveVolumeRequest`、`AddArchiveItemToVolumeRequest` 前端类型和案卷路由 `/archive/volumes`。

- [ ] **Step 1: 写 API 失败测试**

```ts
it("将档案加入指定案卷", async () => {
    await addArchiveItemToVolume(12, 91);
    expect(httpClient.post).toHaveBeenCalledWith("/api/v1/archive-volumes/12:addItem", {
        archiveItemId: 91,
    });
});
```

- [ ] **Step 2: 运行并确认 API 缺失**

```bash
pnpm --filter @archive-management/web test -- archive-volumes.test.ts
```

Expected: FAIL，`addArchiveItemToVolume` 尚未导出。

- [ ] **Step 3: 实现薄 API 客户端和语义类型**

```ts
export function addArchiveItemToVolume(volumeId: number, archiveItemId: number) {
    return httpClient.post<ArchiveVolumeResponse>(
        `/api/v1/archive-volumes/${volumeId}:addItem`,
        { archiveItemId },
    );
}
```

- [ ] **Step 4: 写页面失败测试并实现工作流**

```ts
it("按全宗和分类筛选、创建案卷并打开案卷档案", async () => {
    renderPage();
    await user.selectOptions(screen.getByLabelText("全宗"), "F001");
    await user.click(screen.getByRole("button", { name: "查询" }));
    expect(mocks.listArchiveVolumes).toHaveBeenCalledWith({ fondsCode: "F001" });
    expect(await screen.findByText("V-2026-001")).toBeInTheDocument();
});
```

页面使用现有 `am-page`、`el-form`、`el-table` 和 Drawer 模式；创建后刷新当前筛选。案卷内档案通过现有档案查询按 `volumeId` 筛选；若现有搜索合同不支持 `volumeId`，在本任务同步给 `SearchArchiveItemsRequest` 增加该业务筛选并补 Controller/Service/Mapper 测试。

- [ ] **Step 5: 添加路由并验证**

```ts
route(
    "archive/volumes",
    "archive-volumes",
    "案卷管理",
    FolderOpened,
    () => import("@/pages/archive-volumes/ArchiveVolumesPage.vue"),
    { permission: "archive:item:read" },
)
```

```bash
pnpm --filter @archive-management/web test -- archive-volumes.test.ts ArchiveVolumesPage.test.ts routes.test.ts
```

- [ ] **Step 6: 提交案卷页面**

```bash
git add web/src/shared web/src/pages/archive-volumes web/src/app
git commit -m "feat: 开发案卷管理工作台"
```

### Task 6: 开发档案关系维护

**Files:**
- Modify: `web/src/shared/types/archive-records.ts`
- Modify: `web/src/shared/api/archive-records.ts`
- Modify: `web/src/shared/api/archive.test.ts`
- Create: `web/src/pages/archive-items/ArchiveItemRelationsDrawer.vue`
- Create: `web/src/pages/archive-items/ArchiveItemRelationsDrawer.test.ts`
- Modify: `web/src/pages/archive-items/useArchiveItemResources.ts`
- Modify: `web/src/pages/archive-items/ArchiveItemResourcesDrawer.vue`
- Modify: `web/src/pages/archive-items/ArchiveItemManagementPage.vue`

**Interfaces:**
- Consumes: `GET/POST /api/v1/archive-items/{id}/relations` 和 `DELETE /api/v1/archive-items/{id}/relations/{relationId}`。
- Produces: `ArchiveItemRelationResponse`、`listArchiveItemRelations`、`createArchiveItemRelation`、`deleteArchiveItemRelation`。

- [ ] **Step 1: 写关系 API 和 Drawer 失败测试**

```ts
it("创建关系后重新读取关系列表", async () => {
    await createArchiveItemRelation(1, 2);
    expect(httpClient.post).toHaveBeenCalledWith("/api/v1/archive-items/1/relations", {
        targetItemId: 2,
    });
});

it("从搜索结果选择目标档案建立关系", async () => {
    renderDrawer();
    await user.click(screen.getByRole("button", { name: "添加关系" }));
    await user.click(await screen.findByRole("radio", { name: /A-2026-002/ }));
    await user.click(screen.getByRole("button", { name: "确认关联" }));
    expect(mocks.createArchiveItemRelation).toHaveBeenCalledWith(1, 2);
});
```

- [ ] **Step 2: 观察缺失行为失败**

```bash
pnpm --filter @archive-management/web test -- archive.test.ts ArchiveItemRelationsDrawer.test.ts
```

- [ ] **Step 3: 实现关系 API 与选择界面**

目标选择使用分类、关键字和现有游标搜索结果，不要求用户手输数据库 ID；当前档案不能被选择，后端冲突错误原样展示。

```ts
export function createArchiveItemRelation(id: number, targetItemId: number) {
    return httpClient.post<ArchiveItemRelationResponse>(`/api/v1/archive-items/${id}/relations`, {
        targetItemId,
    });
}
```

- [ ] **Step 4: 接入资源 Drawer 并验证权限**

查看关系使用 `archive:item:read`，新增和删除关系使用 `archive:item:update`；成功后只刷新关系页签。

```bash
pnpm --filter @archive-management/web test -- ArchiveItemRelationsDrawer.test.ts ArchiveItemManagementPage.test.ts
```

- [ ] **Step 5: 提交关系维护**

```bash
git add web/src/shared web/src/pages/archive-items
git commit -m "feat: 开发档案关系维护"
```

### Task 7: 开发明细表定义 PC 配置

**Files:**
- Create: `web/src/shared/types/archive-line-tables.ts`
- Create: `web/src/shared/api/archive-line-tables.ts`
- Create: `web/src/shared/api/archive-line-tables.test.ts`
- Create: `web/src/pages/archive-categories/ArchiveLineTablePanel.vue`
- Create: `web/src/pages/archive-categories/ArchiveLineTablePanel.test.ts`
- Modify: `web/src/pages/archive-categories/ArchiveCategoriesPage.vue`

**Interfaces:**
- Consumes: 分类明细表、字段和 build 现有 API。
- Produces: 分类配置页中的明细表列表、创建、字段创建和建表操作。

- [ ] **Step 1: 写客户端和面板失败测试**

```ts
it("创建字段后可显式构建明细表", async () => {
    renderPanel({ categoryId: 7 });
    await user.click(screen.getByRole("button", { name: "新增明细表" }));
    await fillLineTableForm();
    await user.click(screen.getByRole("button", { name: "保存" }));
    await user.click(screen.getByRole("button", { name: "构建数据表" }));
    expect(mocks.buildArchiveLineTable).toHaveBeenCalledWith(12);
});
```

- [ ] **Step 2: 运行并确认组件/API 缺失**

```bash
pnpm --filter @archive-management/web test -- archive-line-tables.test.ts ArchiveLineTablePanel.test.ts
```

- [ ] **Step 3: 实现薄客户端和紧凑配置面板**

```ts
export function buildArchiveLineTable(lineTableId: number) {
    return httpClient.post<ArchiveLineTableResponse>(
        `/api/v1/archive-item-line-tables/${lineTableId}:build`,
    );
}
```

配置面板只支持现有字段类型、编码、名称、列名、精确检索和排序；不实现拖拽设计器。

- [ ] **Step 4: 验证权限和失败恢复**

面板只对 `archive:metadata:manage` 可见；构建失败保留定义并显示可重试错误。

```bash
pnpm --filter @archive-management/web test -- ArchiveLineTablePanel.test.ts ArchiveCategoriesPage.test.ts
```

- [ ] **Step 5: 提交明细配置**

```bash
git add web/src/shared web/src/pages/archive-categories
git commit -m "feat: 开发档案明细表配置"
```

### Task 8: 增加明细行 CRUD 与档案编辑

**Files:**
- Create: `server/src/main/java/github/luckygc/am/module/archive/item/web/ArchiveItemLineRowController.java`
- Create: `server/src/main/java/github/luckygc/am/module/archive/item/service/ArchiveItemLineRowService.java`
- Modify: `server/src/main/java/github/luckygc/am/module/archive/mapper/ArchiveMapper.java`
- Modify: `server/src/main/resources/mapper/archive/ArchiveMapper.xml`
- Create: `server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveItemLineRowServiceTests.java`
- Create: `server/src/test/java/github/luckygc/am/module/archive/item/web/ArchiveItemLineRowControllerTests.java`
- Modify: `web/src/shared/types/archive-line-tables.ts`
- Modify: `web/src/shared/api/archive-line-tables.ts`
- Modify: `web/src/shared/api/archive-line-tables.test.ts`
- Create: `web/src/pages/archive-items/ArchiveItemLineRows.vue`
- Create: `web/src/pages/archive-items/ArchiveItemLineRows.test.ts`
- Modify: `web/src/pages/archive-items/ArchiveItemEditorDrawer.vue`

**Interfaces:**
- Produces: `GET/POST /api/v1/archive-items/{archiveItem}/line-tables/{lineTable}/rows` 和 `PATCH/DELETE .../rows/{row}`。
- Request: `CreateArchiveItemLineRowRequest(int lineOrder, Map<String, @Nullable Object> values)`。
- Response: `ArchiveItemLineRowResponse(Long id, Long archiveItemId, Long lineTableId, int lineOrder, Map<String, @Nullable Object> values)`。

- [ ] **Step 1: 写 Service 失败测试**

```java
@Test
void createRowValidatesItemScopeAndConfiguredFields() {
    when(permissionService.hasPermission(8L, "archive:item:update")).thenReturn(true);
    when(archiveMapper.getItemLineTable(4L)).thenReturn(lineTable("am_archive_item_line_contract"));
    when(archiveMapper.listItemLineFields(4L)).thenReturn(List.of(lineField("amount", "f_amount", "DECIMAL")));

    service.createRow(3L, 4L, new CreateArchiveItemLineRowRequest(0, Map.of("amount", "12.50")), 8L);

    verify(archiveItemReadService).assertItemInDataScope(3L, 8L);
    verify(archiveMapper).insertItemLineRow(
            eq("am_archive_item_line_contract"), eq(3L), eq(0),
            argThat(values -> values.size() == 1 && values.getFirst().columnName().equals("f_amount")));
}
```

- [ ] **Step 2: 运行并观察 Service 缺失失败**

```bash
cd server && mise exec -- mvn -q -Dtest=ArchiveItemLineRowServiceTests test
```

Expected: FAIL，类型或 Service 尚不存在。

- [ ] **Step 3: 实现白名单动态 SQL**

```java
public ArchiveItemLineRowResponse createRow(
        Long archiveItemId,
        Long lineTableId,
        CreateArchiveItemLineRowRequest request,
        Long userId) {
    permissionService.requirePermission(userId, AuthorizationPermissionCode.ARCHIVE_ITEM_UPDATE);
    archiveItemReadService.assertItemInDataScope(archiveItemId, userId);
    LineTableDefinition table = loadBuiltTable(lineTableId, archiveItemId);
    List<ArchiveSqlAssignment> values = validateValues(table.fields(), request.values());
    Long id = archiveMapper.insertItemLineRow(
            table.physicalTableName(), archiveItemId, request.lineOrder(), values);
    return loadRow(table, archiveItemId, id);
}
```

`${tableName}`、列名和赋值表达式只能来自已校验的明细定义；请求键不得直接拼接 SQL。更新使用白名单 assignments，删除更新 `deleted_flag/deleted_at/deleted_by`。

- [ ] **Step 4: 暴露完整资源 URL 并验证 ProblemDetail**

```java
@PostMapping("/api/v1/archive-items/{archiveItem}/line-tables/{lineTable}/rows")
@ResponseStatus(HttpStatus.CREATED)
public ArchiveItemLineRowResponse createRow(
        @PathVariable Long archiveItem,
        @PathVariable Long lineTable,
        @RequestBody CreateArchiveItemLineRowRequest request,
        Authentication authentication) {
    return service.createRow(archiveItem, lineTable, request, AuthenticatedUsers.requireUserId(authentication.getPrincipal()));
}
```

```bash
cd server && mise exec -- mvn -q -Dtest=ArchiveItemLineRowServiceTests,ArchiveItemLineRowControllerTests test
```

- [ ] **Step 5: 写前端失败测试并实现行编辑器**

```ts
it("新增明细行时按字段定义提交 values", async () => {
    renderLineRows();
    await user.click(screen.getByRole("button", { name: "新增明细" }));
    await user.type(screen.getByLabelText("金额"), "12.50");
    await user.click(screen.getByRole("button", { name: "保存明细" }));
    expect(mocks.createArchiveItemLineRow).toHaveBeenCalledWith(3, 4, {
        lineOrder: 0,
        values: { amount: "12.50" },
    });
});
```

详情模式只读，创建和编辑模式允许新增、编辑、删除；锁定档案时全部禁用。

- [ ] **Step 6: 验证并提交**

```bash
pnpm --filter @archive-management/web test -- archive-line-tables.test.ts ArchiveItemLineRows.test.ts ArchiveItemComponents.test.ts
cd server && mise exec -- mvn -q -Dtest=ArchiveItemLineRowServiceTests,ArchiveItemLineRowControllerTests test
git add server/src web/src
git commit -m "feat: 支持档案明细行维护"
```

### Task 9: 统一路由、菜单和按钮权限

**Files:**
- Modify: `web/src/stores/permissionStore.ts`
- Create: `web/src/stores/permissionStore.test.ts`
- Modify: `web/src/app/routes.ts`
- Modify: `web/src/app/routes.test.ts`
- Modify: `web/src/layout/AppShell.vue`
- Modify: `web/src/layout/RouteMenuItem.vue`
- Modify: `web/src/layout/RouteMenuItem.test.ts`
- Create: `web/src/pages/forbidden/ForbiddenPage.vue`

**Interfaces:**
- Produces: `RouteMeta.permission?: string`、`permissionStore.has(code)`、`canAccessRoute(route, permissionStore)`。

- [ ] **Step 1: 写权限失败测试**

```ts
it("超级管理员拥有未枚举权限", () => {
    const store = usePermissionStore();
    store.$patch({ superAdmin: true, permissionCodes: [] });
    expect(store.has("future:permission")).toBe(true);
});

it("隐藏无权限叶子和没有可见子项的分组", () => {
    renderMenu({ granted: ["archive:item:read"] });
    expect(screen.getByText("档案管理")).toBeInTheDocument();
    expect(screen.queryByText("系统配置")).not.toBeInTheDocument();
});
```

- [ ] **Step 2: 运行并确认当前全量显示失败**

```bash
pnpm --filter @archive-management/web test -- permissionStore.test.ts routes.test.ts RouteMenuItem.test.ts
```

- [ ] **Step 3: 实现唯一权限判断**

```ts
function has(code: string) {
    return superAdmin.value || permissionCodeSet.value.has(code);
}

export function canAccessRoute(record: RouteRecordRaw, has: (code: string) => boolean): boolean {
    const permission = record.meta?.permission;
    if (permission && !has(permission)) return false;
    const menuChildren = (record.children ?? []).filter((child) => child.meta?.menu);
    return menuChildren.length === 0 || menuChildren.some((child) => canAccessRoute(child, has));
}
```

- [ ] **Step 4: 标注路由并拦截直接访问**

`archive/library`、`archive/items`、`archive/volumes` 使用 `archive:item:read`；目录配置使用 `archive:metadata:manage`；治理使用 `archive:governance:manage`；各系统管理页面使用其现有权限码。尚未完成的 intake、storage 和 settings 在对应阶段完成前设置 `menu: false`。

```ts
if (to.meta.permission && !permissionStore.has(to.meta.permission)) {
    return { name: "forbidden", replace: true };
}
```

- [ ] **Step 5: 验证权限与提交**

```bash
pnpm --filter @archive-management/web test -- permissionStore.test.ts routes.test.ts RouteMenuItem.test.ts
git add web/src/stores web/src/app web/src/layout web/src/pages/forbidden
git commit -m "feat: 统一前端功能权限体验"
```

### Task 10: 用数据范围内真实统计替换工作台假数据

**Files:**
- Create: `server/src/main/java/github/luckygc/am/module/archive/item/web/ArchiveWorkspaceController.java`
- Create: `server/src/main/java/github/luckygc/am/module/archive/item/service/ArchiveWorkspaceService.java`
- Modify: `server/src/main/java/github/luckygc/am/module/archive/item/service/ArchiveItemQueryService.java`
- Modify: `server/src/main/java/github/luckygc/am/module/archive/mapper/ArchiveMapper.java`
- Modify: `server/src/main/resources/mapper/archive/ArchiveMapper.xml`
- Create: `server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveWorkspaceServiceTests.java`
- Modify: `server/src/test/java/github/luckygc/am/module/archive/item/service/ArchiveItemQueryServiceBoundaryTests.java`
- Create: `server/src/test/java/github/luckygc/am/module/archive/item/web/ArchiveWorkspaceControllerTests.java`
- Create: `web/src/shared/types/workspace.ts`
- Create: `web/src/shared/api/workspace.ts`
- Create: `web/src/shared/api/workspace.test.ts`
- Modify: `web/src/pages/dashboard/DashboardPage.vue`
- Create: `web/src/pages/dashboard/DashboardPage.test.ts`

**Interfaces:**
- Produces: `GET /api/v1/workspace-summary`。
- Response: `WorkspaceSummaryResponse(long archiveItemCount, long draftCount, long lockedCount, long electronicFileCount)`。
- Query interface: `ArchiveItemQueryService.ArchiveWorkspaceCategorySummary summarizeCategoryForWorkspace(Long categoryId, Long userId)` 和 `ArchiveMapper.summarizeDynamicItems(ArchiveDynamicItemSource source, ArchiveDynamicItemCriteria criteria)`。

- [ ] **Step 1: 写数据范围统计失败测试**

```java
@Test
void summaryUsesResolvedItemDataScope() {
    when(categoryService.listCategories(true)).thenReturn(List.of(category(1L), category(2L)));
    when(queryService.summarizeCategoryForWorkspace(1L, 8L))
            .thenReturn(new ArchiveWorkspaceCategorySummary(7, 2, 1, 4));
    when(queryService.summarizeCategoryForWorkspace(2L, 8L))
            .thenReturn(new ArchiveWorkspaceCategorySummary(5, 1, 1, 3));

    assertThat(service.getSummary(8L)).isEqualTo(new WorkspaceSummaryResponse(12, 3, 2, 7));
}
```

- [ ] **Step 2: 运行并确认 Service 缺失**

```bash
cd server && mise exec -- mvn -q -Dtest=ArchiveWorkspaceServiceTests test
```

- [ ] **Step 3: 按分类复用现有查询上下文并执行聚合**

`ArchiveWorkspaceService` 枚举启用分类，并调用另一个 Spring Bean `ArchiveItemQueryService.summarizeCategoryForWorkspace(categoryId, userId)`；后者复用现有分类动态表、数据范围和查询条件编译，不在工作台复制权限 SQL。每个分类执行一次聚合，Service 将结果相加。空数据范围返回全零。

```sql
<select id="summarizeDynamicItems" resultType="map">
    select count(distinct visible.id) as archive_item_count,
           count(distinct visible.id)
               filter (where visible.electronic_status = 'DRAFT') as draft_count,
           count(distinct visible.id)
               filter (where visible.locked_flag = true) as locked_count,
           count(distinct ef.id) as electronic_file_count
    from (
        select i.id, i.electronic_status, i.locked_flag
        <include refid="dynamicItemFromWhere" />
    ) visible
    left join am_archive_item_electronic_file ef
      on ef.archive_item_id = visible.id
</select>
```

在 XML 中抽取并复用现有动态表请求条件和数据范围 SQL 片段，避免搜索与摘要形成两份条件实现。

- [ ] **Step 4: 写页面失败测试并替换硬编码数据**

```ts
it("渲染服务端摘要且不包含演示待办", async () => {
    mocks.getWorkspaceSummary.mockResolvedValue({
        archiveItemCount: 12,
        draftCount: 3,
        lockedCount: 2,
        electronicFileCount: 7,
    });
    render(DashboardPage);
    expect(await screen.findByText("12")).toBeInTheDocument();
    expect(screen.queryByText("财务凭证归档批次待确认")).not.toBeInTheDocument();
});
```

工作台保留四个真实统计；近期事项区域在审批阶段完成前移除，不显示虚构空壳。

- [ ] **Step 5: 验证并提交**

```bash
cd server && mise exec -- mvn -q -Dtest=ArchiveWorkspaceServiceTests,ArchiveWorkspaceControllerTests test
pnpm --filter @archive-management/web test -- workspace.test.ts DashboardPage.test.ts
git add server/src web/src
git commit -m "feat: 提供真实工作台摘要"
```

### Task 11: 统一关键页面原位错误恢复

**Files:**
- Create: `web/src/shared/components/RequestErrorState.vue`
- Create: `web/src/shared/components/RequestErrorState.test.ts`
- Modify: `web/src/pages/archive-items/useArchiveItemSearch.ts`
- Modify: `web/src/pages/archive-items/useArchiveItemResources.ts`
- Modify: `web/src/pages/archive-items/ArchiveItemManagementPage.vue`
- Modify: `web/src/pages/archive-library/ArchiveLibraryPage.vue`
- Modify: `web/src/pages/archive-volumes/ArchiveVolumesPage.vue`
- Modify: `web/src/pages/dashboard/DashboardPage.vue`

**Interfaces:**
- Produces: `<RequestErrorState :message retry-label @retry>`，只负责展示与重试事件，不负责请求。

- [ ] **Step 1: 写组件失败测试**

```ts
it("展示可执行错误并发出重试事件", async () => {
    const { emitted } = render(RequestErrorState, { props: { message: "查询失败" } });
    await user.click(screen.getByRole("button", { name: "重试" }));
    expect(emitted().retry).toHaveLength(1);
});
```

- [ ] **Step 2: 运行并确认组件缺失**

```bash
pnpm --filter @archive-management/web test -- RequestErrorState.test.ts
```

- [ ] **Step 3: 实现无额外状态源的展示组件**

```vue
<template>
    <el-alert :title="message" type="error" show-icon :closable="false">
        <template #default>
            <el-button link type="primary" @click="$emit('retry')">{{ retryLabel }}</el-button>
        </template>
    </el-alert>
</template>
```

- [ ] **Step 4: 接入列表与摘要页面**

加载失败保留当前结果和已提交条件；错误区域位于对应内容容器内。保存、删除、锁定等瞬时命令继续使用消息反馈，不强制改成页面错误。

- [ ] **Step 5: 验证并提交**

```bash
pnpm --filter @archive-management/web test -- RequestErrorState.test.ts ArchiveItemManagementPage.test.ts ArchiveLibraryPage.test.ts ArchiveVolumesPage.test.ts DashboardPage.test.ts
git add web/src/shared/components web/src/pages
git commit -m "fix: 增加关键页面原位错误恢复"
```

### Task 12: 完成阶段验证与文档回填

**Files:**
- Modify: `openspec/changes/close-archive-pc-mvp/tasks.md`
- Modify: `docs/archive-knowledge/mvp-implementation-gap.md`
- Modify: `docs/api.md`
- Modify: `docs/architecture.md`

**Interfaces:**
- Consumes: Tasks 1-11 的实现和测试证据。
- Produces: 完整阶段验收记录和与代码一致的文档。

- [ ] **Step 1: 运行 Java 格式化与定向后端测试**

```bash
cd server && mise exec -- mvn -q spotless:apply
cd server && mise exec -- mvn -q test
```

Expected: Spotless 完成，后端测试零失败、零错误。

- [ ] **Step 2: 运行前端完整验证**

```bash
pnpm check
pnpm test
pnpm run build
```

Expected: 格式、lint、类型检查、测试和生产构建退出码均为 0。

- [ ] **Step 3: 运行规格与职责验证**

```bash
openspec validate close-archive-pc-mvp --strict
openspec validate --all --strict
pnpm run check:source-lines
```

Expected: OpenSpec 严格校验全部通过，源码职责检查无硬失败。

- [ ] **Step 4: 回填真实文档与任务状态**

MVP 差距文档必须删除已经关闭的缺口；API 文档记录分页、案卷、关系、明细行和工作台合同；架构文档不增加新运行面。只有有对应实现和验证证据的 task 才标记为 `[x]`。

- [ ] **Step 5: 最终需求审计**

逐项对照 OpenSpec 场景，确认超过 100 条翻页、生命周期、物理字段、案卷、关系、明细行、权限、真实摘要和错误恢复均有代码、测试和页面入口证据。任何缺失保持任务未完成并继续修复。

- [ ] **Step 6: 提交阶段收口**

```bash
git add openspec/changes/close-archive-pc-mvp docs server web
git commit -m "feat: 完成档案 PC MVP 闭环"
```
