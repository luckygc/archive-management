<script setup lang="ts">
import { ElMessage, ElMessageBox, type FormInstance } from "element-plus";
import { onMounted, ref, watch } from "vue";

import {
    createAuthorizationRole,
    deleteAuthorizationRole,
    getRolePermissions,
    listAuthorizationPermissions,
    listAuthorizationRoles,
    saveRolePermissions,
    updateAuthorizationRole,
} from "@/shared/api/archive";
import type { AuthorizationPermissionDto, AuthorizationRoleDto } from "@/shared/types/archive";

const roles = ref<AuthorizationRoleDto[]>([]);
const permissions = ref<AuthorizationPermissionDto[]>([]);
const cursor = ref<string>();
const prevCursor = ref<string | null>(null);
const nextCursor = ref<string | null>(null);
const loading = ref(false);

const roleModalOpen = ref(false);
const modalMode = ref<"create" | "edit">("create");
const editingRoleId = ref<number>();
const roleSubmitting = ref(false);
const formRef = ref<FormInstance>();
const form = ref(defaultForm());

const permissionRoleId = ref<number>();
const permissionRoleName = ref("");
const selectedPermissionCodes = ref<string[]>([]);
const permissionLoading = ref(false);
const permissionLoadFailed = ref(false);
const permissionSubmitting = ref(false);

function defaultForm() {
    return { roleName: "", description: "", enabled: true };
}

async function loadRoles() {
    loading.value = true;
    try {
        const response = await listAuthorizationRoles(undefined, 100, cursor.value);
        roles.value = response.items;
        prevCursor.value = response.prev ?? null;
        nextCursor.value = response.next ?? null;
    } catch (error) {
        ElMessage.error((error as Error).message);
    } finally {
        loading.value = false;
    }
}

watch(cursor, () => void loadRoles());
onMounted(() => void loadRoles());

function openCreateModal() {
    modalMode.value = "create";
    editingRoleId.value = undefined;
    form.value = defaultForm();
    roleModalOpen.value = true;
}

function openEditModal(value: unknown) {
    const role = value as AuthorizationRoleDto;
    modalMode.value = "edit";
    editingRoleId.value = role.id;
    form.value = {
        roleName: role.roleName,
        description: role.description ?? "",
        enabled: role.enabled,
    };
    roleModalOpen.value = true;
}

function closeRoleModal() {
    roleModalOpen.value = false;
    editingRoleId.value = undefined;
    formRef.value?.resetFields();
}

async function submitRole() {
    if (!(await formRef.value?.validate().catch(() => false))) return;
    roleSubmitting.value = true;
    try {
        if (modalMode.value === "create") {
            await createAuthorizationRole({
                roleName: form.value.roleName,
                description: form.value.description || undefined,
            });
            ElMessage.success("角色创建成功");
        } else if (editingRoleId.value != null) {
            await updateAuthorizationRole(editingRoleId.value, {
                roleName: form.value.roleName,
                description: form.value.description || undefined,
                enabled: form.value.enabled,
            });
            ElMessage.success("角色更新成功");
        }
        closeRoleModal();
        await loadRoles();
    } catch (error) {
        ElMessage.error((error as Error).message);
    } finally {
        roleSubmitting.value = false;
    }
}

async function removeRole(value: unknown) {
    const role = value as AuthorizationRoleDto;
    try {
        await ElMessageBox.confirm("确定删除此角色？", "提示", {
            confirmButtonText: "删除",
            cancelButtonText: "取消",
            type: "warning",
        });
        await deleteAuthorizationRole(role.id);
        ElMessage.success("角色已删除");
        await loadRoles();
    } catch (error) {
        if (error !== "cancel" && error !== "close") ElMessage.error((error as Error).message);
    }
}

async function openPermissionModal(value: unknown) {
    const role = value as AuthorizationRoleDto;
    permissionRoleId.value = role.id;
    permissionRoleName.value = role.roleName;
    selectedPermissionCodes.value = [];
    permissions.value = [];
    permissionLoading.value = true;
    permissionLoadFailed.value = false;
    try {
        const [assigned, available] = await Promise.all([
            getRolePermissions(role.id),
            listAuthorizationPermissions(),
        ]);
        if (permissionRoleId.value !== role.id) return;
        selectedPermissionCodes.value = assigned.permissionCodes;
        permissions.value = available.items;
    } catch {
        permissionLoadFailed.value = true;
        selectedPermissionCodes.value = [];
        ElMessage.error("角色权限加载失败，请重试");
    } finally {
        permissionLoading.value = false;
    }
}

function closePermissionModal() {
    permissionRoleId.value = undefined;
    permissionRoleName.value = "";
    permissionLoading.value = false;
    permissionLoadFailed.value = false;
    selectedPermissionCodes.value = [];
}

async function savePermissions() {
    if (permissionRoleId.value == null || permissionLoading.value || permissionLoadFailed.value)
        return;
    permissionSubmitting.value = true;
    try {
        await saveRolePermissions(permissionRoleId.value, selectedPermissionCodes.value);
        ElMessage.success("权限分配已保存");
        closePermissionModal();
        await loadRoles();
    } catch (error) {
        ElMessage.error((error as Error).message);
    } finally {
        permissionSubmitting.value = false;
    }
}
</script>

<template>
    <section class="am-page">
        <div class="am-page__header">
            <h1>角色管理</h1>
            <el-button type="primary" @click="openCreateModal">新建角色</el-button>
        </div>
        <el-table v-loading="loading" :data="roles" row-key="id" empty-text="暂无角色">
            <el-table-column prop="roleName" label="角色名称" width="160" />
            <el-table-column label="说明"
                ><template #default="{ row }">{{
                    row.description ?? "-"
                }}</template></el-table-column
            >
            <el-table-column label="状态" width="80"
                ><template #default="{ row }"
                    ><el-tag :type="row.enabled ? 'success' : 'info'">{{
                        row.enabled ? "启用" : "停用"
                    }}</el-tag></template
                ></el-table-column
            >
            <el-table-column prop="createdAt" label="创建时间" width="180" />
            <el-table-column label="操作" width="220"
                ><template #default="{ row }"
                    ><el-button size="small" @click="openEditModal(row)">编辑</el-button
                    ><el-button size="small" @click="openPermissionModal(row)">权限</el-button
                    ><el-button
                        size="small"
                        type="danger"
                        :loading="loading"
                        @click="removeRole(row)"
                        >删除</el-button
                    ></template
                ></el-table-column
            >
        </el-table>
        <div v-if="prevCursor || nextCursor" class="am-pagination">
            <el-button
                :disabled="!prevCursor"
                :loading="loading"
                @click="cursor = prevCursor ?? undefined"
                >上一页</el-button
            ><el-button
                :disabled="!nextCursor"
                :loading="loading"
                @click="cursor = nextCursor ?? undefined"
                >下一页</el-button
            >
        </div>

        <el-dialog
            v-model="roleModalOpen"
            :title="modalMode === 'create' ? '新建角色' : '编辑角色'"
            destroy-on-close
            @closed="closeRoleModal"
        >
            <el-form ref="formRef" :model="form" label-position="top"
                ><el-form-item
                    label="角色名称"
                    prop="roleName"
                    :rules="[{ required: true, message: '请输入角色名称' }]"
                    ><el-input
                        v-model="form.roleName"
                        placeholder="例如：档案管理员" /></el-form-item
                ><el-form-item label="说明" prop="description"
                    ><el-input
                        v-model="form.description"
                        type="textarea"
                        :rows="3"
                        placeholder="角色说明（可选）" /></el-form-item
                ><el-form-item label="启用" prop="enabled"
                    ><el-switch
                        v-model="form.enabled"
                        active-text="启用"
                        inactive-text="停用" /></el-form-item
            ></el-form>
            <template #footer
                ><el-button @click="closeRoleModal">取消</el-button
                ><el-button type="primary" :loading="roleSubmitting" @click="submitRole"
                    >确定</el-button
                ></template
            >
        </el-dialog>

        <el-dialog
            :model-value="permissionRoleId != null"
            :title="`分配权限 — ${permissionRoleName}`"
            width="640px"
            destroy-on-close
            @update:model-value="!$event && closePermissionModal()"
        >
            <div v-loading="permissionLoading">
                <el-alert
                    v-if="permissionLoadFailed"
                    title="角色权限加载失败"
                    description="当前权限数据不可信，请关闭弹窗后重试。"
                    type="error"
                    show-icon
                    :closable="false"
                /><el-select
                    v-model="selectedPermissionCodes"
                    multiple
                    placeholder="选择功能权限"
                    style="width: 100%; margin-top: 12px"
                    ><el-option
                        v-for="permission in permissions"
                        :key="permission.permissionCode"
                        :label="permission.permissionName"
                        :value="permission.permissionCode"
                /></el-select>
            </div>
            <template #footer
                ><el-button @click="closePermissionModal">取消</el-button
                ><el-button
                    type="primary"
                    :disabled="permissionLoading || permissionLoadFailed"
                    :loading="permissionSubmitting"
                    @click="savePermissions"
                    >确定</el-button
                ></template
            >
        </el-dialog>
    </section>
</template>

<style scoped>
.am-pagination {
    display: flex;
    gap: 8px;
    margin-top: 12px;
}
</style>
