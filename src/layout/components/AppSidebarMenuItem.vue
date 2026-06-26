<script setup lang="ts">
import type { Component } from "vue";
import { computed } from "vue";
import type { MenuNode } from "../../shared/types/menu";

const props = defineProps<{
    item: MenuNode;
    resolveIcon: (name?: string) => Component | undefined;
}>();

const hasChildren = computed(() => Boolean(props.item.children?.length));
const itemIndex = computed(() => props.item.path ?? props.item.id);
const iconComponent = computed(() => props.resolveIcon(props.item.icon));
</script>

<template>
    <el-sub-menu v-if="hasChildren" :index="itemIndex">
        <template #title>
            <el-icon v-if="iconComponent">
                <component :is="iconComponent" />
            </el-icon>
            <span>{{ item.title }}</span>
        </template>
        <AppSidebarMenuItem
            v-for="child in item.children"
            :key="child.id"
            :item="child"
            :resolve-icon="resolveIcon"
        />
    </el-sub-menu>
    <el-menu-item v-else :index="itemIndex">
        <el-icon v-if="iconComponent">
            <component :is="iconComponent" />
        </el-icon>
        <span>{{ item.title }}</span>
    </el-menu-item>
</template>
