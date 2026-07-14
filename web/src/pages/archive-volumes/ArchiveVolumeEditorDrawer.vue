<script setup lang="ts">
import type { FormInstance } from "element-plus";
import { ElMessage } from "element-plus";
import { reactive, ref, watch } from "vue";

import { errorMessage, HttpClientError } from "@archive-management/frontend-core/api";

import { createArchiveVolume, getArchiveVolume } from "@/shared/api/archive-volumes";
import type { ArchiveCategoryDto } from "@/shared/types/archive-metadata";
import type { ArchiveElectronicStatus } from "@/shared/types/archive-records";
import type { ArchiveVolumeDetailResponse } from "@/shared/types/archive-volumes";

const props = defineProps<{
    state?: { mode: "create" | "detail"; volumeId?: number };
    categories: ArchiveCategoryDto[];
    fonds: Array<{ fondsCode: string; fondsName: string }>;
}>();
const emit = defineEmits<{ close: []; created: [] }>();

const formRef = ref<FormInstance>();
const loading = ref(false);
const saving = ref(false);
const detail = ref<ArchiveVolumeDetailResponse>();
const fieldErrors = reactive<Record<string, string>>({});
const form = reactive({
    categoryId: undefined as number | undefined,
    fondsCode: "",
    archiveNo: "",
    archiveYear: new Date().getFullYear(),
    electronicStatus: "DRAFT" as ArchiveElectronicStatus,
});
let requestVersion = 0;

watch(
    () => props.state,
    (state) => {
        clearErrors();
        detail.value = undefined;
        if (!state) {
            requestVersion += 1;
            loading.value = false;
            return;
        }
        if (state.mode === "create") {
            Object.assign(form, {
                categoryId: undefined,
                fondsCode: "",
                archiveNo: "",
                archiveYear: new Date().getFullYear(),
                electronicStatus: "DRAFT",
            });
            return;
        }
        if (state.volumeId) void loadDetail(state.volumeId);
    },
    { immediate: true },
);

async function loadDetail(volumeId: number) {
    const version = ++requestVersion;
    loading.value = true;
    try {
        const response = await getArchiveVolume(volumeId);
        if (version === requestVersion) detail.value = response;
    } catch (error) {
        if (version === requestVersion) ElMessage.error(errorMessage(error, "加载案卷详情失败"));
    } finally {
        if (version === requestVersion) loading.value = false;
    }
}

async function submit() {
    if (saving.value || props.state?.mode !== "create") return;
    const version = ++requestVersion;
    saving.value = true;
    try {
        if (!(await formRef.value?.validate().catch(() => false))) return;
        if (version !== requestVersion) return;
        clearErrors();
        await createArchiveVolume({
            categoryId: form.categoryId!,
            fondsCode: form.fondsCode,
            archiveNo: form.archiveNo.trim() || undefined,
            archiveYear: form.archiveYear,
            electronicStatus: form.electronicStatus,
        });
        if (version !== requestVersion) return;
        ElMessage.success("案卷已创建");
        emit("created");
    } catch (error) {
        if (version !== requestVersion) return;
        applyFieldErrors(error);
        ElMessage.error(withTraceId(error, "创建案卷失败"));
    } finally {
        if (version === requestVersion) saving.value = false;
    }
}

function close() {
    requestVersion += 1;
    loading.value = false;
    saving.value = false;
    emit("close");
}

function clearErrors() {
    for (const key of Object.keys(fieldErrors)) delete fieldErrors[key];
}

function applyFieldErrors(error: unknown) {
    if (!(error instanceof HttpClientError)) return;
    for (const violation of error.fieldViolations) {
        if (violation.field && violation.message) fieldErrors[violation.field] = violation.message;
    }
}

function withTraceId(error: unknown, fallback: string) {
    const message = errorMessage(error, fallback);
    return error instanceof HttpClientError && error.traceId
        ? `${message}（追踪 ID：${error.traceId}）`
        : message;
}
</script>

<template>
    <el-drawer
        :model-value="Boolean(state)"
        :title="state?.mode === 'create' ? '新建案卷' : '案卷详情'"
        size="560px"
        @close="close"
    >
        <div v-loading="loading">
            <el-descriptions v-if="state?.mode === 'detail' && detail" border :column="1">
                <el-descriptions-item label="档号">{{
                    detail.archiveNo || "-"
                }}</el-descriptions-item>
                <el-descriptions-item label="全宗">
                    {{ detail.fondsCode }} {{ detail.fondsName }}
                </el-descriptions-item>
                <el-descriptions-item label="档案分类">
                    {{ detail.categoryCode }} {{ detail.categoryName }}
                </el-descriptions-item>
                <el-descriptions-item label="年度">{{ detail.archiveYear }}</el-descriptions-item>
                <el-descriptions-item label="电子状态">
                    {{ detail.electronicStatus }}
                </el-descriptions-item>
            </el-descriptions>
            <el-form
                v-else-if="state?.mode === 'create'"
                ref="formRef"
                :model="form"
                label-position="top"
            >
                <el-form-item
                    label="档案分类"
                    prop="categoryId"
                    :error="fieldErrors.categoryId"
                    :rules="[{ required: true, message: '请选择档案分类', trigger: 'change' }]"
                >
                    <el-select v-model="form.categoryId" filterable aria-label="创建档案分类">
                        <el-option
                            v-for="category in categories"
                            :key="category.id"
                            :label="`${category.categoryCode} ${category.categoryName}`"
                            :value="category.id"
                        />
                    </el-select>
                </el-form-item>
                <el-form-item
                    label="全宗"
                    prop="fondsCode"
                    :error="fieldErrors.fondsCode"
                    :rules="[{ required: true, message: '请选择全宗', trigger: 'change' }]"
                >
                    <el-select v-model="form.fondsCode" filterable aria-label="创建全宗">
                        <el-option
                            v-for="item in fonds"
                            :key="item.fondsCode"
                            :label="`${item.fondsCode} ${item.fondsName}`"
                            :value="item.fondsCode"
                        />
                    </el-select>
                </el-form-item>
                <el-form-item label="档号" :error="fieldErrors.archiveNo">
                    <el-input v-model="form.archiveNo" />
                </el-form-item>
                <el-form-item label="年度" :error="fieldErrors.archiveYear">
                    <el-input-number v-model="form.archiveYear" :min="1" />
                </el-form-item>
                <el-form-item label="电子状态" :error="fieldErrors.electronicStatus">
                    <el-select v-model="form.electronicStatus">
                        <el-option label="草稿" value="DRAFT" />
                        <el-option label="已归档" value="ARCHIVED" />
                    </el-select>
                </el-form-item>
            </el-form>
        </div>
        <template #footer>
            <el-button @click="close">取消</el-button>
            <el-button
                v-if="state?.mode === 'create'"
                type="primary"
                :loading="saving"
                @click="submit"
                >创建</el-button
            >
        </template>
    </el-drawer>
</template>
