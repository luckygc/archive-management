<script setup lang="ts">
import { ElMessage } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import { computed, onMounted, reactive, ref } from "vue";

import {
    createOrganizationDepartment,
    listOrganizationDepartments,
    updateOrganizationDepartment,
} from "@/shared/api/archive";
import type { OrganizationDepartmentDto } from "@/shared/types/archive";

interface DepartmentFormValues {
    departmentCode: string;
    departmentName: string;
    parentId?: number;
    enabled: boolean;
    sortOrder: number;
}

type DepartmentTreeNode = OrganizationDepartmentDto & { children?: DepartmentTreeNode[] };

const departments = ref<OrganizationDepartmentDto[]>([]);
const loading = ref(false);
const saving = ref(false);
const modalOpen = ref(false);
const editing = ref<OrganizationDepartmentDto>();
const formRef = ref<FormInstance>();
const form = reactive<DepartmentFormValues>(emptyForm());
const rules: FormRules<DepartmentFormValues> = {
    departmentCode: [{ required: true, message: "请输入部门编码", trigger: "blur" }],
    departmentName: [{ required: true, message: "请输入部门名称", trigger: "blur" }],
};

const departmentsById = computed(
    () => new Map(departments.value.map((department) => [department.id, department])),
);
const treeData = computed(() => buildDepartmentTree(departments.value));
const excludedParentIds = computed(() =>
    editing.value
        ? descendantIds(departments.value, editing.value.id).add(editing.value.id)
        : new Set<number>(),
);

async function loadDepartments() {
    loading.value = true;
    try {
        departments.value = (await listOrganizationDepartments()).items;
    } catch (error) {
        ElMessage.error(error instanceof Error ? error.message : "部门加载失败");
    } finally {
        loading.value = false;
    }
}

function openCreate(parentId?: number) {
    editing.value = undefined;
    Object.assign(form, emptyForm(), { parentId });
    formRef.value?.clearValidate();
    modalOpen.value = true;
}

function openEdit(value: unknown) {
    const department = value as OrganizationDepartmentDto;
    editing.value = department;
    Object.assign(form, {
        departmentCode: department.departmentCode,
        departmentName: department.departmentName,
        parentId: department.parentId ?? undefined,
        enabled: department.enabled,
        sortOrder: department.sortOrder,
    });
    formRef.value?.clearValidate();
    modalOpen.value = true;
}

function closeModal() {
    modalOpen.value = false;
    editing.value = undefined;
    Object.assign(form, emptyForm());
    formRef.value?.resetFields();
}

async function submit() {
    if (!(await formRef.value?.validate().catch(() => false))) return;
    saving.value = true;
    try {
        if (editing.value) {
            await updateOrganizationDepartment(editing.value.id, {
                ...form,
                parentId: form.parentId ?? null,
                sortOrder: form.sortOrder ?? 0,
            });
            ElMessage.success("部门已保存");
        } else {
            await createOrganizationDepartment({
                ...form,
                parentId: form.parentId,
                sortOrder: form.sortOrder ?? 0,
            });
            ElMessage.success("部门已创建");
        }
        closeModal();
        await loadDepartments();
    } catch (error) {
        ElMessage.error(error instanceof Error ? error.message : "部门保存失败");
    } finally {
        saving.value = false;
    }
}

function emptyForm(): DepartmentFormValues {
    return { departmentCode: "", departmentName: "", enabled: true, sortOrder: 0 };
}

onMounted(loadDepartments);
</script>

<template>
    <section class="am-page">
        <div class="am-page__header">
            <h1>组织架构</h1>
            <el-button aria-label="新建部门" type="primary" @click="openCreate()"
                >新建部门</el-button
            >
        </div>
        <el-card shadow="never">
            <el-table
                v-loading="loading"
                :data="treeData"
                row-key="id"
                default-expand-all
                empty-text="暂无部门"
            >
                <el-table-column label="部门编码" prop="departmentCode" width="160" />
                <el-table-column label="部门名称" prop="departmentName" />
                <el-table-column label="上级部门" width="180">
                    <template #default="{ row }">
                        {{
                            row.parentId
                                ? (departmentsById.get(row.parentId)?.departmentName ?? "-")
                                : "-"
                        }}
                    </template>
                </el-table-column>
                <el-table-column label="排序" prop="sortOrder" width="90" />
                <el-table-column label="状态" width="90">
                    <template #default="{ row }">
                        <el-tag :type="row.enabled ? 'success' : 'info'">
                            {{ row.enabled ? "启用" : "停用" }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="操作" width="180">
                    <template #default="{ row }">
                        <el-button size="small" @click="openEdit(row)">编辑</el-button>
                        <el-button link size="small" type="primary" @click="openCreate(row.id)">
                            新增下级
                        </el-button>
                    </template>
                </el-table-column>
            </el-table>
        </el-card>

        <el-dialog
            v-model="modalOpen"
            :title="editing ? '编辑部门' : '新建部门'"
            width="520px"
            destroy-on-close
            @closed="closeModal"
        >
            <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
                <el-form-item label="部门编码" prop="departmentCode">
                    <el-input v-model="form.departmentCode" placeholder="例如：D003" />
                </el-form-item>
                <el-form-item label="部门名称" prop="departmentName">
                    <el-input v-model="form.departmentName" placeholder="例如：法务部" />
                </el-form-item>
                <el-form-item label="上级部门" prop="parentId">
                    <el-select
                        v-model="form.parentId"
                        clearable
                        filterable
                        placeholder="选择上级部门"
                    >
                        <el-option
                            v-for="department in departments"
                            :key="department.id"
                            :disabled="excludedParentIds.has(department.id)"
                            :label="`${department.departmentCode} ${department.departmentName}`"
                            :value="department.id"
                        />
                    </el-select>
                </el-form-item>
                <el-form-item label="排序" prop="sortOrder">
                    <el-input-number v-model="form.sortOrder" :min="0" :precision="0" />
                </el-form-item>
                <el-form-item label="启用" prop="enabled">
                    <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" />
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="closeModal">取消</el-button>
                <el-button :loading="saving" type="primary" @click="submit">保存</el-button>
            </template>
        </el-dialog>
    </section>
</template>

<script lang="ts">
type BuiltDepartmentTreeNode = OrganizationDepartmentDto & {
    children?: BuiltDepartmentTreeNode[];
};

function buildDepartmentTree(departments: OrganizationDepartmentDto[]) {
    const childrenByParent = new Map<number | null, OrganizationDepartmentDto[]>();
    for (const department of departments) {
        const parentId = department.parentId ?? null;
        const siblings = childrenByParent.get(parentId) ?? [];
        siblings.push(department);
        childrenByParent.set(parentId, siblings);
    }
    for (const siblings of childrenByParent.values()) {
        siblings.sort((left, right) => left.sortOrder - right.sortOrder || left.id - right.id);
    }
    const build = (parentId: number | null): BuiltDepartmentTreeNode[] =>
        (childrenByParent.get(parentId) ?? []).map((department) => {
            const children = build(department.id);
            return { ...department, children: children.length > 0 ? children : undefined };
        });
    return build(null);
}

function descendantIds(departments: OrganizationDepartmentDto[], id: number) {
    const descendants = new Set<number>();
    let changed = true;
    while (changed) {
        changed = false;
        for (const department of departments) {
            const parentId = department.parentId;
            if (
                parentId != null &&
                (parentId === id || descendants.has(parentId)) &&
                !descendants.has(department.id)
            ) {
                descendants.add(department.id);
                changed = true;
            }
        }
    }
    return descendants;
}
</script>
