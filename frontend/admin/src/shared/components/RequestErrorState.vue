<script setup lang="ts">
const props = withDefaults(
    defineProps<{
        message: string;
        retryLabel?: string;
        retrying?: boolean;
        disabled?: boolean;
    }>(),
    {
        retryLabel: "重试",
        retrying: false,
        disabled: false,
    },
);

const emit = defineEmits<{ retry: [] }>();

function retry() {
    if (props.retrying || props.disabled) return;
    emit("retry");
}
</script>

<template>
    <el-alert
        :title="props.message"
        type="error"
        show-icon
        :closable="false"
        role="alert"
        aria-live="assertive"
    >
        <el-button
            link
            type="primary"
            :loading="props.retrying"
            :disabled="props.retrying || props.disabled"
            @click="retry"
        >
            {{ props.retryLabel }}
        </el-button>
    </el-alert>
</template>
