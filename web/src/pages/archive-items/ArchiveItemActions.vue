<script setup lang="ts">
import { Download, Plus, Refresh, Upload } from "@element-plus/icons-vue";
import { ref } from "vue";

defineProps<{
    categorySelected: boolean;
    canImport: boolean;
    canExport: boolean;
    canCreate: boolean;
    downloadingTemplate: boolean;
    importing: boolean;
    exporting: boolean;
}>();

const emit = defineEmits<{
    refresh: [];
    downloadTemplate: [];
    importFile: [file: File];
    export: [];
    create: [];
}>();

const importInput = ref<HTMLInputElement>();

function selectImportFile(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) emit("importFile", file);
    input.value = "";
}
</script>

<template>
    <div>
        <el-button :icon="Refresh" @click="$emit('refresh')">刷新</el-button>
        <el-button
            :disabled="!categorySelected || !canImport || downloadingTemplate"
            :loading="downloadingTemplate"
            :icon="Download"
            @click="$emit('downloadTemplate')"
            >导入模板</el-button
        >
        <el-button
            :disabled="!categorySelected || !canImport || importing"
            :loading="importing"
            :icon="Upload"
            @click="importInput?.click()"
            >导入</el-button
        >
        <input ref="importInput" hidden type="file" accept=".xlsx" @change="selectImportFile" />
        <el-button
            :disabled="!canExport || exporting"
            :loading="exporting"
            :icon="Download"
            @click="$emit('export')"
            >导出</el-button
        >
        <el-button
            :disabled="!canCreate || !categorySelected"
            :icon="Plus"
            type="primary"
            @click="$emit('create')"
            >新建档案</el-button
        >
    </div>
</template>
