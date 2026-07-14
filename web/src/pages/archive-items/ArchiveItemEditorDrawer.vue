<script setup lang="ts">
import type { FormInstance } from "element-plus";
import { computed, ref } from "vue";

import DynamicArchiveFields from "@/pages/archive-library/DynamicArchiveFields.vue";
import ArchiveItemLineRows from "./ArchiveItemLineRows.vue";
import type {
    ArchiveCategoryDto,
    ArchiveFieldDto,
    ArchiveRetentionPeriodDto,
    ArchiveSecurityLevelDto,
} from "@/shared/types/archive-metadata";
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
        securityLevelId?: number;
        retentionPeriodId?: number;
        physicalFields: Record<string, unknown>;
        dynamicFields: Record<string, unknown>;
    };
    fields: ArchiveFieldDto[];
    physicalFields: ArchiveFieldDto[];
    categories: ArchiveCategoryDto[];
    fonds: Array<{ fondsCode: string; fondsName: string }>;
    securityLevels: ArchiveSecurityLevelDto[];
    retentionPeriods: ArchiveRetentionPeriodDto[];
    fieldErrors: Record<string, string>;
    formError?: string;
    loading: boolean;
    referencesLoading?: boolean;
    referencesLoadError?: string;
    saving: boolean;
}>();

const emit = defineEmits<{ close: []; retryReferences: []; save: [] }>();
const formRef = ref<FormInstance>();
const formDisabled = computed(
    () => props.state?.mode === "detail" || props.detail?.item.lockedFlag === true,
);
const enabledSecurityLevels = computed(() => props.securityLevels.filter((item) => item.enabled));
const enabledRetentionPeriods = computed(() =>
    props.retentionPeriods.filter((item) => item.enabled),
);
const unavailableSecurityLevelId = computed(() => {
    if (props.referencesLoading) return undefined;
    const currentId = props.form.securityLevelId;
    return currentId !== undefined &&
        !enabledSecurityLevels.value.some((item) => item.id === currentId)
        ? currentId
        : undefined;
});
const unavailableRetentionPeriodId = computed(() => {
    if (props.referencesLoading) return undefined;
    const currentId = props.form.retentionPeriodId;
    return currentId !== undefined &&
        !enabledRetentionPeriods.value.some((item) => item.id === currentId)
        ? currentId
        : undefined;
});
const physicalFieldErrors = computed(() => fieldErrorGroup("physicalFields"));
const dynamicFieldErrors = computed(() => fieldErrorGroup("dynamicFields"));

async function submit() {
    if (formDisabled.value) return;
    if (await formRef.value?.validate().catch(() => false)) emit("save");
}
function fieldErrorGroup(prefix: "physicalFields" | "dynamicFields") {
    const result: Record<string, string> = {};
    for (const [path, message] of Object.entries(props.fieldErrors)) {
        if (path.startsWith(`${prefix}.`)) result[path.slice(prefix.length + 1)] = message;
    }
    return result;
}
</script>

<template>
    <el-drawer
        :model-value="Boolean(state)"
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
        <div v-loading="loading" class="editor-drawer-content">
            <template v-if="state?.mode === 'detail' && detail">
                <el-descriptions border :column="3">
                    <el-descriptions-item label="档案 ID">{{
                        detail.item.id
                    }}</el-descriptions-item>
                    <el-descriptions-item label="锁定">{{
                        detail.item.lockedFlag ? "是" : "否"
                    }}</el-descriptions-item>
                    <el-descriptions-item label="锁定原因">{{
                        detail.item.lockReason || "-"
                    }}</el-descriptions-item>
                </el-descriptions>
            </template>
            <el-form
                v-if="state"
                ref="formRef"
                :model="form"
                :disabled="formDisabled"
                label-position="top"
            >
                <div v-if="referencesLoadError" class="reference-load-error">
                    <el-alert
                        :title="referencesLoadError"
                        type="error"
                        show-icon
                        :closable="false"
                    />
                    <el-button
                        link
                        type="primary"
                        :loading="referencesLoading"
                        @click="emit('retryReferences')"
                        >重试参考数据</el-button
                    >
                </div>
                <el-alert
                    v-if="formError"
                    class="form-error"
                    :title="formError"
                    type="error"
                    show-icon
                    :closable="false"
                />
                <el-form-item label="档案分类" :error="fieldErrors.categoryId"
                    ><el-input
                        disabled
                        :model-value="
                            categories.find((item) => item.id === form.categoryId)?.categoryName
                        "
                /></el-form-item>
                <el-form-item
                    label="全宗"
                    prop="fondsCode"
                    :error="fieldErrors.fondsCode"
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
                        ><el-form-item label="档号" prop="archiveNo" :error="fieldErrors.archiveNo"
                            ><el-input v-model="form.archiveNo" /></el-form-item
                    ></el-col>
                    <el-col :span="8"
                        ><el-form-item
                            label="年度"
                            prop="archiveYear"
                            :error="fieldErrors.archiveYear"
                            ><el-input-number v-model="form.archiveYear" :min="1" /></el-form-item
                    ></el-col>
                    <el-col :span="8"
                        ><el-form-item
                            label="电子状态"
                            prop="electronicStatus"
                            :error="fieldErrors.electronicStatus"
                            ><el-select v-model="form.electronicStatus"
                                ><el-option label="草稿" value="DRAFT" /><el-option
                                    label="已归档"
                                    value="ARCHIVED" /><el-option
                                    label="借出"
                                    value="BORROWED" /></el-select></el-form-item
                    ></el-col>
                </el-row>
                <el-row :gutter="16">
                    <el-col :span="12">
                        <el-form-item
                            label="密级"
                            prop="securityLevelId"
                            :error="fieldErrors.securityLevelId"
                        >
                            <div class="reference-field">
                                <el-select
                                    v-model="form.securityLevelId"
                                    clearable
                                    :loading="referencesLoading"
                                >
                                    <el-option
                                        v-for="item in enabledSecurityLevels"
                                        :key="item.id"
                                        :label="item.levelName"
                                        :value="item.id"
                                    />
                                </el-select>
                                <el-text
                                    v-if="unavailableSecurityLevelId !== undefined"
                                    type="info"
                                >
                                    当前密级 ID：{{ unavailableSecurityLevelId }}（已停用或不可用）
                                </el-text>
                            </div>
                        </el-form-item>
                    </el-col>
                    <el-col :span="12">
                        <el-form-item
                            label="保管期限"
                            prop="retentionPeriodId"
                            :error="fieldErrors.retentionPeriodId"
                        >
                            <div class="reference-field">
                                <el-select
                                    v-model="form.retentionPeriodId"
                                    clearable
                                    :loading="referencesLoading"
                                >
                                    <el-option
                                        v-for="item in enabledRetentionPeriods"
                                        :key="item.id"
                                        :label="item.periodName"
                                        :value="item.id"
                                    />
                                </el-select>
                                <el-text
                                    v-if="unavailableRetentionPeriodId !== undefined"
                                    type="info"
                                >
                                    当前保管期限 ID：{{
                                        unavailableRetentionPeriodId
                                    }}（已停用或不可用）
                                </el-text>
                            </div>
                        </el-form-item>
                    </el-col>
                </el-row>
                <h3>实物字段</h3>
                <DynamicArchiveFields
                    v-model="form.physicalFields"
                    :fields="physicalFields"
                    :disabled="formDisabled"
                    :field-errors="physicalFieldErrors"
                    :surface="state.mode === 'detail' ? 'detail' : 'edit'"
                />
                <h3>动态字段</h3>
                <DynamicArchiveFields
                    v-model="form.dynamicFields"
                    :fields="fields"
                    :disabled="formDisabled"
                    :field-errors="dynamicFieldErrors"
                    :surface="state.mode === 'detail' ? 'detail' : 'edit'"
                />
                <template v-if="state.mode !== 'detail'">
                    <el-button :disabled="saving" @click="emit('close')">取消</el-button>
                    <el-button
                        :disabled="formDisabled"
                        :loading="saving"
                        type="primary"
                        @click="submit"
                        >保存</el-button
                    >
                </template>
            </el-form>
            <ArchiveItemLineRows
                v-if="state?.archiveItemId && detail"
                :archive-item-id="state.archiveItemId"
                :readonly="formDisabled"
            />
        </div>
    </el-drawer>
</template>

<style scoped>
.reference-field {
    display: grid;
    width: 100%;
    gap: 4px;
}

.editor-drawer-content {
    min-height: 160px;
}

.reference-load-error {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 16px;
}

.reference-load-error .el-alert {
    flex: 1;
}

.form-error {
    margin-bottom: 16px;
}
</style>
