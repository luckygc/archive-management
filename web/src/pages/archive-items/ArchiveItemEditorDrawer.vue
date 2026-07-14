<script setup lang="ts">
import type { FormInstance } from "element-plus";
import { computed, ref } from "vue";

import DynamicArchiveFields from "@/pages/archive-library/DynamicArchiveFields.vue";
import type { ArchiveCategoryDto, ArchiveFieldDto } from "@/shared/types/archive-metadata";
import type {
    ArchiveElectronicStatus,
    ArchiveRecordDetailDto,
} from "@/shared/types/archive-records";

const props = defineProps<{
    state?: { mode: "create" | "detail" | "edit"; archiveItemId?: number };
    detail?: ArchiveRecordDetailDto;
    form: {
        categoryId?: number;
        fondsCode: string;
        archiveNo: string;
        archiveYear: number;
        electronicStatus: ArchiveElectronicStatus;
        dynamicFields: Record<string, unknown>;
    };
    fields: ArchiveFieldDto[];
    categories: ArchiveCategoryDto[];
    fonds: Array<{ fondsCode: string; fondsName: string }>;
    loading: boolean;
    saving: boolean;
}>();

const emit = defineEmits<{ close: []; save: [] }>();
const formRef = ref<FormInstance>();
const detailFields = computed(() =>
    [...(props.detail?.fields ?? [])]
        .filter((field) => field.enabled && field.detailVisible)
        .sort(
            (left, right) =>
                left.detailSortOrder - right.detailSortOrder || left.sortOrder - right.sortOrder,
        ),
);
async function submit() {
    if (await formRef.value?.validate().catch(() => false)) emit("save");
}
function formatValue(value: unknown) {
    return value == null || value === "" ? "-" : String(value);
}
</script>

<template>
    <el-drawer
        :model-value="Boolean(state)"
        v-loading="loading"
        :title="
            state?.mode === 'create'
                ? '新建档案'
                : state?.mode === 'detail'
                  ? `档案 ${state.archiveItemId} 详情`
                  : `编辑档案 ${state?.archiveItemId}`
        "
        size="70%"
        @close="emit('close')"
    >
        <template v-if="state?.mode === 'detail' && detail">
            <el-descriptions border :column="2">
                <el-descriptions-item label="档案 ID">{{ detail.item.id }}</el-descriptions-item>
                <el-descriptions-item label="档案分类">{{
                    detail.category.categoryName
                }}</el-descriptions-item>
                <el-descriptions-item label="全宗">{{
                    detail.item.fondsName
                }}</el-descriptions-item>
                <el-descriptions-item label="档号">{{
                    detail.item.archiveNo || "-"
                }}</el-descriptions-item>
                <el-descriptions-item label="年度">{{
                    detail.item.archiveYear
                }}</el-descriptions-item>
                <el-descriptions-item label="电子状态">{{
                    detail.item.electronicStatus
                }}</el-descriptions-item>
                <el-descriptions-item label="锁定">{{
                    detail.item.lockedFlag ? "是" : "否"
                }}</el-descriptions-item>
                <el-descriptions-item label="锁定原因">{{
                    detail.item.lockReason || "-"
                }}</el-descriptions-item>
            </el-descriptions>
            <h3>动态字段</h3>
            <el-empty
                v-if="detailFields.length === 0"
                description="无可展示字段"
                :image-size="48"
            />
            <el-descriptions v-else border :column="2">
                <el-descriptions-item
                    v-for="field in detailFields"
                    :key="field.id"
                    :label="field.fieldName"
                    >{{ formatValue(detail.dynamicFields[field.fieldCode]) }}</el-descriptions-item
                >
            </el-descriptions>
        </template>
        <el-form
            v-else-if="state && state.mode !== 'detail'"
            ref="formRef"
            :model="form"
            label-position="top"
        >
            <el-form-item label="档案分类"
                ><el-input
                    disabled
                    :model-value="
                        categories.find((item) => item.id === form.categoryId)?.categoryName
                    "
            /></el-form-item>
            <el-form-item
                label="全宗"
                prop="fondsCode"
                :rules="[{ required: true, message: '请选择全宗', trigger: 'change' }]"
            >
                <el-select v-model="form.fondsCode" filterable
                    ><el-option
                        v-for="item in fonds"
                        :key="item.fondsCode"
                        :label="`${item.fondsCode} ${item.fondsName}`"
                        :value="item.fondsCode"
                /></el-select>
            </el-form-item>
            <el-row :gutter="16">
                <el-col :span="8"
                    ><el-form-item label="档号"><el-input v-model="form.archiveNo" /></el-form-item
                ></el-col>
                <el-col :span="8"
                    ><el-form-item label="年度"
                        ><el-input-number v-model="form.archiveYear" :min="1" /></el-form-item
                ></el-col>
                <el-col :span="8"
                    ><el-form-item label="电子状态"
                        ><el-select v-model="form.electronicStatus"
                            ><el-option label="草稿" value="DRAFT" /><el-option
                                label="已归档"
                                value="ARCHIVED" /><el-option
                                label="借出"
                                value="BORROWED" /></el-select></el-form-item
                ></el-col>
            </el-row>
            <DynamicArchiveFields v-model="form.dynamicFields" :fields="fields" />
            <el-button :disabled="saving" @click="emit('close')">取消</el-button>
            <el-button :loading="saving" type="primary" @click="submit">保存</el-button>
        </el-form>
    </el-drawer>
</template>
