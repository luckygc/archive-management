<script setup lang="ts">
defineProps<{
    locked: boolean;
    canRead: boolean;
    canUpdate: boolean;
    canLock: boolean;
    canDelete: boolean;
    canReadAudit: boolean;
    busy: boolean;
}>();

defineEmits<{
    view: [];
    edit: [];
    files: [];
    relations: [];
    audits: [];
    lock: [];
    unlock: [];
    delete: [];
}>();
</script>

<template>
    <el-space :size="4" wrap>
        <el-button link :disabled="!canRead" @click="$emit('view')">查看</el-button>
        <el-button link :disabled="!canUpdate || locked" @click="$emit('edit')">编辑</el-button>
        <el-button link :disabled="!canRead" @click="$emit('files')">文件</el-button>
        <el-button link :disabled="!canRead" @click="$emit('relations')">关系</el-button>
        <el-button link :disabled="!canReadAudit" @click="$emit('audits')">审计</el-button>
        <el-button v-if="locked" link :disabled="!canLock || busy" @click="$emit('unlock')"
            >解锁</el-button
        >
        <el-button v-else link :disabled="!canLock || busy" @click="$emit('lock')">锁定</el-button>
        <el-button
            link
            type="danger"
            :disabled="!canDelete || locked || busy"
            @click="$emit('delete')"
            >删除</el-button
        >
    </el-space>
</template>
