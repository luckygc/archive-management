<script setup lang="ts">
import type { FormInstance } from "element-plus";
import { ref } from "vue";

import type { ArchiveGovernanceSchemeDto } from "@/shared/types/archive-governance";

defineProps<{
    schemeOpen: boolean;
    versionOpen: boolean;
    editingScheme?: ArchiveGovernanceSchemeDto;
    schemeForm: {
        schemeCode: string;
        schemeName: string;
        description: string;
        enabled: boolean;
        sortOrder: number;
    };
    versionForm: { versionCode: string; versionDescription: string };
    submitting: boolean;
}>();
const emit = defineEmits<{
    closeScheme: [];
    closeVersion: [];
    submitScheme: [];
    submitVersion: [];
}>();
const schemeFormRef = ref<FormInstance>();
const versionFormRef = ref<FormInstance>();
async function submitScheme() {
    if (await schemeFormRef.value?.validate().catch(() => false)) emit("submitScheme");
}
async function submitVersion() {
    if (await versionFormRef.value?.validate().catch(() => false)) emit("submitVersion");
}
</script>

<template>
    <el-dialog
        :model-value="schemeOpen"
        :title="editingScheme ? '编辑治理方案' : '新建治理方案'"
        destroy-on-close
        @update:model-value="!$event && emit('closeScheme')"
    >
        <el-form ref="schemeFormRef" :model="schemeForm" label-position="top">
            <el-form-item
                label="编码"
                prop="schemeCode"
                :rules="[{ required: true, message: '请输入编码' }]"
                ><el-input v-model="schemeForm.schemeCode" placeholder="default_governance"
            /></el-form-item>
            <el-form-item
                label="名称"
                prop="schemeName"
                :rules="[{ required: true, message: '请输入名称' }]"
                ><el-input v-model="schemeForm.schemeName"
            /></el-form-item>
            <el-form-item label="说明"
                ><el-input v-model="schemeForm.description" type="textarea" :rows="3"
            /></el-form-item>
            <el-form-item label="排序"
                ><el-input-number v-model="schemeForm.sortOrder" :min="0"
            /></el-form-item>
            <el-form-item label="启用"><el-switch v-model="schemeForm.enabled" /></el-form-item>
        </el-form>
        <template #footer
            ><el-button @click="emit('closeScheme')">取消</el-button
            ><el-button type="primary" :loading="submitting" @click="submitScheme"
                >确定</el-button
            ></template
        >
    </el-dialog>
    <el-dialog
        :model-value="versionOpen"
        title="新建治理方案版本"
        destroy-on-close
        @update:model-value="!$event && emit('closeVersion')"
    >
        <el-form ref="versionFormRef" :model="versionForm" label-position="top">
            <el-form-item
                label="版本号"
                prop="versionCode"
                :rules="[{ required: true, message: '请输入版本号' }]"
                ><el-input v-model="versionForm.versionCode" placeholder="v1"
            /></el-form-item>
            <el-form-item label="版本说明"
                ><el-input v-model="versionForm.versionDescription" type="textarea" :rows="3"
            /></el-form-item>
        </el-form>
        <template #footer
            ><el-button @click="emit('closeVersion')">取消</el-button
            ><el-button type="primary" :loading="submitting" @click="submitVersion"
                >确定</el-button
            ></template
        >
    </el-dialog>
</template>
