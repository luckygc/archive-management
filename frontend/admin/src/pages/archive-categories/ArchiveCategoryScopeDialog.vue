<script setup lang="ts">
import { ElMessage } from "element-plus";
import { computed, ref } from "vue";

import {
    listArchiveFondsCategoryScopes,
    saveArchiveFondsCategoryScopes,
} from "@/shared/api/archive-metadata";
import type {
    ArchiveCategoryDto,
    ArchiveFondsCategoryScopeRequest,
    ArchiveFondsDto,
} from "@/shared/types/archive-metadata";

const props = defineProps<{ fonds: ArchiveFondsDto[]; categories: ArchiveCategoryDto[] }>();
const open = ref(false);
const saving = ref(false);
const fondsCode = ref<string>();
const items = ref<ArchiveFondsCategoryScopeRequest[]>([]);
const categoryOptions = computed(() => props.categories.filter((item) => item.enabled));

async function show() {
    open.value = true;
    fondsCode.value = props.fonds[0]?.fondsCode;
    await load();
}

async function load() {
    items.value = fondsCode.value
        ? (await listArchiveFondsCategoryScopes(fondsCode.value)).items.map((item) => ({
              categoryId: item.categoryId,
              defaultFlag: item.defaultFlag,
              sortOrder: item.sortOrder,
          }))
        : [];
}

async function save() {
    if (!fondsCode.value) return;
    saving.value = true;
    try {
        await saveArchiveFondsCategoryScopes(fondsCode.value, items.value);
        open.value = false;
        ElMessage.success("全宗可用分类已保存");
    } finally {
        saving.value = false;
    }
}

function addItem() {
    items.value.push({
        categoryId: 0,
        defaultFlag: items.value.length === 0,
        sortOrder: items.value.length,
    });
}

defineExpose({ show });
</script>

<template>
    <el-dialog v-model="open" title="全宗可用分类范围" width="720">
        <el-form label-position="top">
            <el-form-item label="全宗">
                <el-select v-model="fondsCode" @change="load">
                    <el-option
                        v-for="fondsItem in fonds"
                        :key="fondsItem.fondsCode"
                        :label="`${fondsItem.fondsName}（${fondsItem.fondsCode}）`"
                        :value="fondsItem.fondsCode"
                    />
                </el-select>
            </el-form-item>
            <el-row v-for="(item, index) in items" :key="index" :gutter="12">
                <el-col :span="12">
                    <el-select v-model="item.categoryId">
                        <el-option
                            v-for="category in categoryOptions"
                            :key="category.id"
                            :label="category.categoryName"
                            :value="category.id"
                        />
                    </el-select>
                </el-col>
                <el-col :span="5"
                    ><el-checkbox v-model="item.defaultFlag">默认</el-checkbox></el-col
                >
                <el-col :span="5"><el-input-number v-model="item.sortOrder" :min="0" /></el-col>
                <el-col :span="2"
                    ><el-button type="danger" plain @click="items.splice(index, 1)"
                        >删除</el-button
                    ></el-col
                >
            </el-row>
            <el-button @click="addItem">添加分类</el-button>
        </el-form>
        <template #footer>
            <el-button @click="open = false">取消</el-button>
            <el-button type="primary" :loading="saving" @click="save">确定</el-button>
        </template>
    </el-dialog>
</template>
