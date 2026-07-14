<script setup lang="ts">
import { ElMessage, ElMessageBox, type FormInstance } from "element-plus";
import { computed, onMounted, ref, watch } from "vue";

import {
    createArchiveGovernanceScheme,
    createArchiveGovernanceSchemeVersion,
    deleteArchiveGovernanceScheme,
    freezeArchiveGovernanceSchemeVersion,
    listArchiveGovernanceBindings,
    listArchiveGovernanceSchemeVersions,
    listArchiveGovernanceSchemes,
    listArchiveGovernanceScopes,
    publishArchiveGovernanceSchemeVersion,
    replaceArchiveGovernanceBindings,
    replaceArchiveGovernanceScopes,
    resolveDefaultArchiveGovernanceVersion,
    retireArchiveGovernanceSchemeVersion,
    updateArchiveGovernanceScheme,
} from "@/shared/api/archive-governance";
import type {
    ArchiveGovernanceBindingDto,
    ArchiveGovernanceBindingRequest,
    ArchiveGovernanceBindingType,
    ArchiveGovernanceSchemeDto,
    ArchiveGovernanceSchemeVersionDto,
    ArchiveGovernanceSchemeVersionStatus,
    ArchiveGovernanceScopeDto,
    ArchiveGovernanceScopeRequest,
    ArchiveGovernanceScopeType,
} from "@/shared/types/archive-governance";

const statusLabels: Record<ArchiveGovernanceSchemeVersionStatus, string> = {
    DRAFT: "草稿",
    PUBLISHED: "已发布",
    FROZEN: "已冻结",
    RETIRED: "已退役",
};
const scopeTypeLabels: Record<ArchiveGovernanceScopeType, string> = {
    GLOBAL: "全局默认",
    FONDS: "全宗",
    CATEGORY: "分类",
};
const bindingTypeLabels: Record<ArchiveGovernanceBindingType, string> = {
    ONTOLOGY: "本体",
    RULE_SET: "规则集",
    CLASSIFICATION_SCHEME: "分类方案",
    DESCRIPTION_PROFILE: "著录方案",
    REFERENCE_CODE_RULE: "档号规则",
};
let draftCounter = 0;
interface ScopeDraft {
    draftKey: string;
    id?: number;
    scopeType: ArchiveGovernanceScopeType;
    fondsCode?: string;
    categoryCode?: string;
    defaultFlag: boolean;
}
interface BindingDraft {
    draftKey: string;
    id?: number;
    bindingType: ArchiveGovernanceBindingType;
    targetType?: string;
    targetId?: number;
    targetCode?: string;
    bindingOrder: number;
}

const schemes = ref<ArchiveGovernanceSchemeDto[]>([]);
const versions = ref<ArchiveGovernanceSchemeVersionDto[]>([]);
const selectedSchemeId = ref<number>();
const selectedVersionId = ref<number>();
const scopeDrafts = ref<ScopeDraft[]>([]);
const bindingDrafts = ref<BindingDraft[]>([]);
const schemesLoading = ref(false);
const versionsLoading = ref(false);
const workbenchLoading = ref(false);
const schemeModalOpen = ref(false);
const versionModalOpen = ref(false);
const submitting = ref(false);
const savingScopes = ref(false);
const savingBindings = ref(false);
const resolving = ref(false);
const editingScheme = ref<ArchiveGovernanceSchemeDto>();
const schemeFormRef = ref<FormInstance>();
const versionFormRef = ref<FormInstance>();
const schemeForm = ref({
    schemeCode: "",
    schemeName: "",
    description: "",
    enabled: true,
    sortOrder: 0,
});
const versionForm = ref({ versionCode: "v1", versionDescription: "" });
const resolveForm = ref({ fondsCode: "", categoryCode: "" });
const resolvedVersion = ref<ArchiveGovernanceSchemeVersionDto>();
const selectedVersion = computed(() =>
    versions.value.find((item) => item.id === selectedVersionId.value),
);
const selectedVersionReadonly = computed(() => selectedVersion.value?.status !== "DRAFT");

async function loadSchemes(preferredId?: number) {
    schemesLoading.value = true;
    try {
        schemes.value = (await listArchiveGovernanceSchemes()).items;
        const validPreferred = preferredId && schemes.value.some((item) => item.id === preferredId);
        selectedSchemeId.value = validPreferred ? preferredId : schemes.value[0]?.id;
    } catch (error) {
        ElMessage.error((error as Error).message);
    } finally {
        schemesLoading.value = false;
    }
}
async function loadVersions(preferredId?: number) {
    if (!selectedSchemeId.value) {
        versions.value = [];
        selectedVersionId.value = undefined;
        return;
    }
    versionsLoading.value = true;
    try {
        versions.value = (await listArchiveGovernanceSchemeVersions(selectedSchemeId.value)).items;
        selectedVersionId.value =
            preferredId && versions.value.some((item) => item.id === preferredId)
                ? preferredId
                : versions.value[0]?.id;
    } catch (error) {
        ElMessage.error((error as Error).message);
    } finally {
        versionsLoading.value = false;
    }
}
async function loadWorkbench() {
    if (!selectedVersionId.value) {
        scopeDrafts.value = [];
        bindingDrafts.value = [];
        return;
    }
    workbenchLoading.value = true;
    try {
        const [scopes, bindings] = await Promise.all([
            listArchiveGovernanceScopes(selectedVersionId.value),
            listArchiveGovernanceBindings(selectedVersionId.value),
        ]);
        scopeDrafts.value = scopes.items.map(toScopeDraft);
        bindingDrafts.value = bindings.items.map(toBindingDraft);
    } catch (error) {
        ElMessage.error((error as Error).message);
    } finally {
        workbenchLoading.value = false;
    }
}
watch(selectedSchemeId, () => {
    selectedVersionId.value = undefined;
    resolvedVersion.value = undefined;
    void loadVersions();
});
watch(selectedVersionId, () => {
    resolvedVersion.value = undefined;
    void loadWorkbench();
});
onMounted(() => void loadSchemes());

function openCreateScheme() {
    editingScheme.value = undefined;
    schemeForm.value = {
        schemeCode: "",
        schemeName: "",
        description: "",
        enabled: true,
        sortOrder: 0,
    };
    schemeModalOpen.value = true;
}
function openEditScheme(value: unknown) {
    const row = value as ArchiveGovernanceSchemeDto;
    editingScheme.value = row;
    schemeForm.value = {
        schemeCode: row.schemeCode,
        schemeName: row.schemeName,
        description: row.description ?? "",
        enabled: row.enabled,
        sortOrder: row.sortOrder,
    };
    schemeModalOpen.value = true;
}
async function submitScheme() {
    if (!(await schemeFormRef.value?.validate().catch(() => false))) return;
    submitting.value = true;
    try {
        const payload = {
            ...schemeForm.value,
            description: trimToUndefined(schemeForm.value.description),
        };
        const result = editingScheme.value
            ? await updateArchiveGovernanceScheme(editingScheme.value.id, payload)
            : await createArchiveGovernanceScheme(payload);
        ElMessage.success(editingScheme.value ? "治理方案已更新" : "治理方案已创建");
        schemeModalOpen.value = false;
        await loadSchemes(result.id);
    } catch (error) {
        ElMessage.error((error as Error).message);
    } finally {
        submitting.value = false;
    }
}
async function removeScheme(value: unknown) {
    const row = value as ArchiveGovernanceSchemeDto;
    try {
        await ElMessageBox.confirm("确认删除治理方案？", "提示", { type: "warning" });
        await deleteArchiveGovernanceScheme(row.id);
        ElMessage.success("治理方案已删除");
        await loadSchemes();
    } catch (error) {
        if (error !== "cancel" && error !== "close") ElMessage.error((error as Error).message);
    }
}
async function submitVersion() {
    if (!selectedSchemeId.value || !(await versionFormRef.value?.validate().catch(() => false)))
        return;
    submitting.value = true;
    try {
        const created = await createArchiveGovernanceSchemeVersion(selectedSchemeId.value, {
            versionCode: versionForm.value.versionCode,
            versionDescription: trimToUndefined(versionForm.value.versionDescription),
        });
        ElMessage.success("版本已创建");
        versionModalOpen.value = false;
        await loadVersions(created.id);
    } catch (error) {
        ElMessage.error((error as Error).message);
    } finally {
        submitting.value = false;
    }
}
async function changeStatus(value: unknown, action: "publish" | "freeze" | "retire") {
    const row = value as ArchiveGovernanceSchemeVersionDto;
    try {
        if (action === "publish") await publishArchiveGovernanceSchemeVersion(row.id);
        else if (action === "freeze") await freezeArchiveGovernanceSchemeVersion(row.id);
        else await retireArchiveGovernanceSchemeVersion(row.id);
        ElMessage.success("版本状态已更新");
        await loadVersions(row.id);
    } catch (error) {
        ElMessage.error((error as Error).message);
    }
}
async function saveScopes() {
    if (!selectedVersionId.value) return;
    savingScopes.value = true;
    try {
        await replaceArchiveGovernanceScopes(
            selectedVersionId.value,
            scopeDrafts.value.map(toScopeRequest),
        );
        ElMessage.success("适用范围已保存");
        await loadWorkbench();
    } catch (error) {
        ElMessage.error((error as Error).message);
    } finally {
        savingScopes.value = false;
    }
}
async function saveBindings() {
    if (!selectedVersionId.value) return;
    savingBindings.value = true;
    try {
        await replaceArchiveGovernanceBindings(
            selectedVersionId.value,
            bindingDrafts.value.map(toBindingRequest),
        );
        ElMessage.success("装配绑定已保存");
        await loadWorkbench();
    } catch (error) {
        ElMessage.error((error as Error).message);
    } finally {
        savingBindings.value = false;
    }
}
async function resolveDefault() {
    resolving.value = true;
    try {
        resolvedVersion.value = await resolveDefaultArchiveGovernanceVersion({
            fondsCode: trimToUndefined(resolveForm.value.fondsCode),
            categoryCode: trimToUndefined(resolveForm.value.categoryCode),
        });
    } catch (error) {
        resolvedVersion.value = undefined;
        ElMessage.error((error as Error).message);
    } finally {
        resolving.value = false;
    }
}
function changeScopeType(value: unknown) {
    const row = value as ScopeDraft;
    if (row.scopeType === "GLOBAL") {
        row.fondsCode = undefined;
        row.categoryCode = undefined;
    } else if (row.scopeType === "FONDS") row.categoryCode = undefined;
    else row.fondsCode = undefined;
}
function nextDraftKey() {
    return `draft-${++draftCounter}`;
}
function toScopeDraft(scope: ArchiveGovernanceScopeDto): ScopeDraft {
    return { draftKey: nextDraftKey(), ...scope };
}
function toBindingDraft(binding: ArchiveGovernanceBindingDto): BindingDraft {
    return { draftKey: nextDraftKey(), ...binding };
}
function toScopeRequest(scope: ScopeDraft): ArchiveGovernanceScopeRequest {
    return scope.scopeType === "GLOBAL"
        ? { scopeType: "GLOBAL", defaultFlag: scope.defaultFlag }
        : scope.scopeType === "FONDS"
          ? {
                scopeType: "FONDS",
                fondsCode: trimToUndefined(scope.fondsCode),
                defaultFlag: scope.defaultFlag,
            }
          : {
                scopeType: "CATEGORY",
                categoryCode: trimToUndefined(scope.categoryCode),
                defaultFlag: scope.defaultFlag,
            };
}
function toBindingRequest(binding: BindingDraft): ArchiveGovernanceBindingRequest {
    return {
        bindingType: binding.bindingType,
        targetType: trimToUndefined(binding.targetType),
        targetId: binding.targetId,
        targetCode: trimToUndefined(binding.targetCode),
        bindingOrder: binding.bindingOrder,
    };
}
function trimToUndefined(value?: string) {
    return value?.trim() || undefined;
}
function statusLabel(status: unknown) {
    return statusLabels[status as ArchiveGovernanceSchemeVersionStatus];
}
</script>

<template>
    <section class="am-page">
        <div class="am-page__header">
            <h1>治理方案</h1>
            <div class="am-page__actions">
                <el-button type="primary" @click="openCreateScheme">新建方案</el-button
                ><el-button
                    :disabled="!selectedSchemeId"
                    @click="
                        versionForm = { versionCode: 'v1', versionDescription: '' };
                        versionModalOpen = true;
                    "
                    >新建版本</el-button
                >
            </div>
        </div>
        <el-row :gutter="16">
            <el-col :span="10"
                ><el-card shadow="never"
                    ><el-table
                        v-loading="schemesLoading"
                        :data="schemes"
                        highlight-current-row
                        row-key="id"
                        @current-change="selectedSchemeId = $event?.id"
                        ><el-table-column
                            prop="schemeCode"
                            label="编码"
                            width="180"
                        /><el-table-column prop="schemeName" label="名称" /><el-table-column
                            label="启用"
                            width="90"
                            ><template #default="{ row }"
                                ><el-tag :type="row.enabled ? 'primary' : 'info'">{{
                                    row.enabled ? "启用" : "停用"
                                }}</el-tag></template
                            ></el-table-column
                        ><el-table-column
                            prop="sortOrder"
                            label="排序"
                            width="80"
                        /><el-table-column label="操作" width="150"
                            ><template #default="{ row }"
                                ><el-button link @click.stop="openEditScheme(row)">编辑</el-button
                                ><el-button link type="danger" @click.stop="removeScheme(row)"
                                    >删除</el-button
                                ></template
                            ></el-table-column
                        ></el-table
                    ></el-card
                ></el-col
            >
            <el-col :span="14"
                ><el-card shadow="never"
                    ><el-table
                        v-loading="versionsLoading"
                        :data="versions"
                        highlight-current-row
                        row-key="id"
                        @current-change="selectedVersionId = $event?.id"
                        ><el-table-column
                            prop="versionCode"
                            label="版本号"
                            width="150"
                        /><el-table-column prop="versionDescription" label="说明" /><el-table-column
                            label="状态"
                            width="110"
                            ><template #default="{ row }"
                                ><el-tag :type="row.status === 'PUBLISHED' ? 'primary' : 'info'">{{
                                    statusLabel(row.status)
                                }}</el-tag></template
                            ></el-table-column
                        ><el-table-column label="操作" width="220"
                            ><template #default="{ row }"
                                ><el-button
                                    size="small"
                                    :disabled="row.status !== 'DRAFT'"
                                    @click.stop="changeStatus(row, 'publish')"
                                    >发布</el-button
                                ><el-button
                                    size="small"
                                    :disabled="row.status !== 'PUBLISHED'"
                                    @click.stop="changeStatus(row, 'freeze')"
                                    >冻结</el-button
                                ><el-button
                                    size="small"
                                    :disabled="row.status !== 'FROZEN'"
                                    @click.stop="changeStatus(row, 'retire')"
                                    >退役</el-button
                                ></template
                            ></el-table-column
                        ></el-table
                    ></el-card
                ></el-col
            >
            <el-col :span="24" class="workbench"
                ><el-card v-loading="workbenchLoading" header="版本工作台" shadow="never"
                    ><el-empty v-if="!selectedVersion" description="请选择治理方案版本" /><template
                        v-else
                    >
                        <el-descriptions :column="3" border size="small"
                            ><el-descriptions-item label="版本号">{{
                                selectedVersion.versionCode
                            }}</el-descriptions-item
                            ><el-descriptions-item label="状态"
                                ><el-tag
                                    :type="
                                        selectedVersion.status === 'PUBLISHED' ? 'primary' : 'info'
                                    "
                                    >{{ statusLabels[selectedVersion.status] }}</el-tag
                                ></el-descriptions-item
                            ><el-descriptions-item label="版本说明">{{
                                selectedVersion.versionDescription ?? "-"
                            }}</el-descriptions-item
                            ><el-descriptions-item label="发布时间">{{
                                selectedVersion.publishedAt ?? "-"
                            }}</el-descriptions-item
                            ><el-descriptions-item label="冻结时间">{{
                                selectedVersion.frozenAt ?? "-"
                            }}</el-descriptions-item
                            ><el-descriptions-item label="退役时间">{{
                                selectedVersion.retiredAt ?? "-"
                            }}</el-descriptions-item></el-descriptions
                        >
                        <el-divider content-position="left">适用范围</el-divider>
                        <div class="am-table-toolbar">
                            <el-button
                                :disabled="selectedVersionReadonly"
                                @click="
                                    scopeDrafts.push({
                                        draftKey: nextDraftKey(),
                                        scopeType: 'GLOBAL',
                                        defaultFlag: true,
                                    })
                                "
                                >新增范围</el-button
                            ><el-button
                                type="primary"
                                :disabled="selectedVersionReadonly"
                                :loading="savingScopes"
                                @click="saveScopes"
                                >保存范围</el-button
                            >
                        </div>
                        <el-table :data="scopeDrafts" row-key="draftKey" size="small"
                            ><el-table-column label="范围类型" width="150"
                                ><template #default="{ row }"
                                    ><el-select
                                        v-model="row.scopeType"
                                        :disabled="selectedVersionReadonly"
                                        @change="changeScopeType(row)"
                                        ><el-option
                                            v-for="(label, value) in scopeTypeLabels"
                                            :key="value"
                                            :label="label"
                                            :value="
                                                value
                                            " /></el-select></template></el-table-column
                            ><el-table-column label="全宗编码"
                                ><template #default="{ row }"
                                    ><el-input
                                        v-model="row.fondsCode"
                                        :disabled="
                                            selectedVersionReadonly || row.scopeType !== 'FONDS'
                                        " /></template></el-table-column
                            ><el-table-column label="分类编码"
                                ><template #default="{ row }"
                                    ><el-input
                                        v-model="row.categoryCode"
                                        :disabled="
                                            selectedVersionReadonly || row.scopeType !== 'CATEGORY'
                                        " /></template></el-table-column
                            ><el-table-column label="默认" width="90"
                                ><template #default="{ row }"
                                    ><el-switch
                                        v-model="row.defaultFlag"
                                        :disabled="
                                            selectedVersionReadonly
                                        " /></template></el-table-column
                            ><el-table-column label="操作" width="90"
                                ><template #default="{ row }"
                                    ><el-button
                                        link
                                        type="danger"
                                        :disabled="selectedVersionReadonly"
                                        @click="
                                            scopeDrafts = scopeDrafts.filter(
                                                (item) => item.draftKey !== row.draftKey,
                                            )
                                        "
                                        >删除</el-button
                                    ></template
                                ></el-table-column
                            ></el-table
                        >
                        <el-divider content-position="left">装配绑定</el-divider>
                        <div class="am-table-toolbar">
                            <el-button
                                :disabled="selectedVersionReadonly"
                                @click="
                                    bindingDrafts.push({
                                        draftKey: nextDraftKey(),
                                        bindingType: 'ONTOLOGY',
                                        bindingOrder: 0,
                                    })
                                "
                                >新增绑定</el-button
                            ><el-button
                                type="primary"
                                :disabled="selectedVersionReadonly"
                                :loading="savingBindings"
                                @click="saveBindings"
                                >保存绑定</el-button
                            >
                        </div>
                        <el-table :data="bindingDrafts" row-key="draftKey" size="small"
                            ><el-table-column label="绑定类型" width="160"
                                ><template #default="{ row }"
                                    ><el-select
                                        v-model="row.bindingType"
                                        :disabled="selectedVersionReadonly"
                                        ><el-option
                                            v-for="(label, value) in bindingTypeLabels"
                                            :key="value"
                                            :label="label"
                                            :value="
                                                value
                                            " /></el-select></template></el-table-column
                            ><el-table-column label="目标类型" width="150"
                                ><template #default="{ row }"
                                    ><el-input
                                        v-model="row.targetType"
                                        :disabled="
                                            selectedVersionReadonly
                                        " /></template></el-table-column
                            ><el-table-column label="目标 ID" width="130"
                                ><template #default="{ row }"
                                    ><el-input-number
                                        v-model="row.targetId"
                                        :min="0"
                                        :disabled="
                                            selectedVersionReadonly
                                        " /></template></el-table-column
                            ><el-table-column label="目标编码"
                                ><template #default="{ row }"
                                    ><el-input
                                        v-model="row.targetCode"
                                        :disabled="
                                            selectedVersionReadonly
                                        " /></template></el-table-column
                            ><el-table-column label="排序" width="110"
                                ><template #default="{ row }"
                                    ><el-input-number
                                        v-model="row.bindingOrder"
                                        :disabled="
                                            selectedVersionReadonly
                                        " /></template></el-table-column
                            ><el-table-column label="操作" width="90"
                                ><template #default="{ row }"
                                    ><el-button
                                        link
                                        type="danger"
                                        :disabled="selectedVersionReadonly"
                                        @click="
                                            bindingDrafts = bindingDrafts.filter(
                                                (item) => item.draftKey !== row.draftKey,
                                            )
                                        "
                                        >删除</el-button
                                    ></template
                                ></el-table-column
                            ></el-table
                        >
                        <el-divider content-position="left">默认解析试算</el-divider
                        ><el-form :model="resolveForm" inline
                            ><el-form-item label="全宗编码"
                                ><el-input
                                    v-model="resolveForm.fondsCode"
                                    placeholder="F001" /></el-form-item
                            ><el-form-item label="分类编码"
                                ><el-input
                                    v-model="resolveForm.categoryCode"
                                    placeholder="case_file" /></el-form-item
                            ><el-form-item
                                ><el-button
                                    type="primary"
                                    :loading="resolving"
                                    @click="resolveDefault"
                                    >解析默认版本</el-button
                                ></el-form-item
                            ></el-form
                        ><el-descriptions v-if="resolvedVersion" :column="3" border size="small"
                            ><el-descriptions-item label="命中版本">{{
                                resolvedVersion.versionCode
                            }}</el-descriptions-item
                            ><el-descriptions-item label="状态">{{
                                statusLabels[resolvedVersion.status]
                            }}</el-descriptions-item
                            ><el-descriptions-item label="说明">{{
                                resolvedVersion.versionDescription ?? "-"
                            }}</el-descriptions-item></el-descriptions
                        >
                    </template></el-card
                ></el-col
            >
        </el-row>
        <el-dialog
            v-model="schemeModalOpen"
            :title="editingScheme ? '编辑治理方案' : '新建治理方案'"
            destroy-on-close
            ><el-form ref="schemeFormRef" :model="schemeForm" label-position="top"
                ><el-form-item
                    label="编码"
                    prop="schemeCode"
                    :rules="[{ required: true, message: '请输入编码' }]"
                    ><el-input
                        v-model="schemeForm.schemeCode"
                        placeholder="default_governance" /></el-form-item
                ><el-form-item
                    label="名称"
                    prop="schemeName"
                    :rules="[{ required: true, message: '请输入名称' }]"
                    ><el-input v-model="schemeForm.schemeName" /></el-form-item
                ><el-form-item label="说明"
                    ><el-input
                        v-model="schemeForm.description"
                        type="textarea"
                        :rows="3" /></el-form-item
                ><el-form-item label="排序"
                    ><el-input-number v-model="schemeForm.sortOrder" :min="0" /></el-form-item
                ><el-form-item label="启用"
                    ><el-switch v-model="schemeForm.enabled" /></el-form-item></el-form
            ><template #footer
                ><el-button @click="schemeModalOpen = false">取消</el-button
                ><el-button type="primary" :loading="submitting" @click="submitScheme"
                    >确定</el-button
                ></template
            ></el-dialog
        >
        <el-dialog v-model="versionModalOpen" title="新建治理方案版本" destroy-on-close
            ><el-form ref="versionFormRef" :model="versionForm" label-position="top"
                ><el-form-item
                    label="版本号"
                    prop="versionCode"
                    :rules="[{ required: true, message: '请输入版本号' }]"
                    ><el-input v-model="versionForm.versionCode" placeholder="v1" /></el-form-item
                ><el-form-item label="版本说明"
                    ><el-input
                        v-model="versionForm.versionDescription"
                        type="textarea"
                        :rows="3" /></el-form-item></el-form
            ><template #footer
                ><el-button @click="versionModalOpen = false">取消</el-button
                ><el-button type="primary" :loading="submitting" @click="submitVersion"
                    >确定</el-button
                ></template
            ></el-dialog
        >
    </section>
</template>

<style scoped>
.workbench {
    margin-top: 16px;
}
.am-table-toolbar {
    display: flex;
    gap: 8px;
    margin-bottom: 12px;
}
</style>
