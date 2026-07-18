<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, onMounted, ref, watch } from "vue";

import {
    createArchiveGovernanceScheme,
    createArchiveGovernanceSchemeVersion,
    deleteArchiveGovernanceScheme,
    freezeArchiveGovernanceSchemeVersion,
    listArchiveGovernanceSchemeVersions,
    listArchiveGovernanceSchemes,
    publishArchiveGovernanceSchemeVersion,
    retireArchiveGovernanceSchemeVersion,
    updateArchiveGovernanceScheme,
} from "@/shared/api/archive-governance";
import type {
    ArchiveGovernanceSchemeDto,
    ArchiveGovernanceSchemeVersionDto,
    ArchiveGovernanceSchemeVersionStatus,
} from "@/shared/types/archive-governance";
import ArchiveGovernanceDialogs from "./ArchiveGovernanceDialogs.vue";
import ArchiveGovernanceWorkbench from "./ArchiveGovernanceWorkbench.vue";

const statusLabels: Record<ArchiveGovernanceSchemeVersionStatus, string> = {
    DRAFT: "草稿",
    PUBLISHED: "已发布",
    FROZEN: "已冻结",
    RETIRED: "已退役",
};
const schemes = ref<ArchiveGovernanceSchemeDto[]>([]);
const versions = ref<ArchiveGovernanceSchemeVersionDto[]>([]);
const selectedSchemeId = ref<number>();
const selectedVersionId = ref<number>();
const schemesLoading = ref(false);
const versionsLoading = ref(false);
const schemeModalOpen = ref(false);
const versionModalOpen = ref(false);
const submitting = ref(false);
const editingScheme = ref<ArchiveGovernanceSchemeDto>();
const schemeForm = ref({
    schemeCode: "",
    schemeName: "",
    description: "",
    enabled: true,
    sortOrder: 0,
});
const versionForm = ref({ versionCode: "v1", versionDescription: "" });
const selectedVersion = computed(() =>
    versions.value.find((item) => item.id === selectedVersionId.value),
);

async function loadSchemes(preferredId?: number) {
    schemesLoading.value = true;
    try {
        schemes.value = (await listArchiveGovernanceSchemes()).items;
        selectedSchemeId.value =
            preferredId && schemes.value.some((item) => item.id === preferredId)
                ? preferredId
                : schemes.value[0]?.id;
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
    if (!selectedSchemeId.value) return;
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
function trimToUndefined(value?: string) {
    return value?.trim() || undefined;
}
function statusLabel(status: unknown) {
    return statusLabels[status as ArchiveGovernanceSchemeVersionStatus];
}
watch(selectedSchemeId, () => {
    selectedVersionId.value = undefined;
    void loadVersions();
});
onMounted(() => void loadSchemes());
</script>

<template>
    <section class="am-page">
        <div class="am-page__header">
            <h1>治理方案</h1>
            <div class="am-page__actions">
                <el-button type="primary" @click="openCreateScheme">新建方案</el-button>
                <el-button
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
                ><ArchiveGovernanceWorkbench :selected-version="selectedVersion"
            /></el-col>
        </el-row>
        <ArchiveGovernanceDialogs
            :scheme-open="schemeModalOpen"
            :version-open="versionModalOpen"
            :editing-scheme="editingScheme"
            :scheme-form="schemeForm"
            :version-form="versionForm"
            :submitting="submitting"
            @close-scheme="schemeModalOpen = false"
            @close-version="versionModalOpen = false"
            @submit-scheme="submitScheme"
            @submit-version="submitVersion"
        />
    </section>
</template>

<style scoped>
.workbench {
    margin-top: 16px;
}
</style>
