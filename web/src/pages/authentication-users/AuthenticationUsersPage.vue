<script setup lang="ts">
import { ElMessage, type FormInstance } from "element-plus";
import { onMounted, ref, watch } from "vue";

import {
    createAuthenticationUser,
    getAuthenticationUser,
    listAuthenticationUsers,
    listAuthorizationRoles,
    listOrganizationDepartments,
    resetAuthenticationUserPassword,
    saveAuthenticationUserRoles,
    updateAuthenticationUser,
} from "@/shared/api/archive";
import type {
    AuthenticationUserDto,
    AuthorizationRoleDto,
    OrganizationDepartmentDto,
} from "@/shared/types/archive";

const keywordInput = ref("");
const keyword = ref("");
const cursor = ref<string>();
const prevCursor = ref<string | null>(null);
const nextCursor = ref<string | null>(null);
const users = ref<AuthenticationUserDto[]>([]);
const roles = ref<AuthorizationRoleDto[]>([]);
const departments = ref<OrganizationDepartmentDto[]>([]);
const loading = ref(false);
const auxiliaryLoading = ref(false);

const userModalOpen = ref(false);
const modalMode = ref<"create" | "edit">("create");
const editingUserId = ref<number>();
const editUserLoading = ref(false);
const userSubmitting = ref(false);
const userFormRef = ref<FormInstance>();
const userForm = ref(defaultUserForm());

const roleModalUserId = ref<number>();
const selectedRoleIds = ref<number[]>([]);
const roleLoading = ref(false);
const roleLoadFailed = ref(false);
const roleSubmitting = ref(false);

const passwordModalUserId = ref<number>();
const passwordSubmitting = ref(false);
const passwordFormRef = ref<FormInstance>();
const passwordForm = ref({ newPassword: "" });

function defaultUserForm() {
    return {
        username: "",
        password: "",
        displayName: "",
        email: "",
        mobilePhone: "",
        departmentId: undefined as number | undefined,
        enabled: true,
    };
}

async function loadUsers() {
    loading.value = true;
    try {
        const response = await listAuthenticationUsers(
            keyword.value || undefined,
            100,
            cursor.value,
        );
        users.value = response.items;
        prevCursor.value = response.prev ?? null;
        nextCursor.value = response.next ?? null;
    } catch (error) {
        ElMessage.error((error as Error).message);
    } finally {
        loading.value = false;
    }
}

async function loadAuxiliaryData() {
    auxiliaryLoading.value = true;
    try {
        const [roleResponse, departmentResponse] = await Promise.all([
            listAuthorizationRoles(true, 1000),
            listOrganizationDepartments(true),
        ]);
        roles.value = roleResponse.items;
        departments.value = departmentResponse.items;
    } catch (error) {
        ElMessage.error((error as Error).message);
    } finally {
        auxiliaryLoading.value = false;
    }
}

watch(cursor, () => void loadUsers());
onMounted(() => {
    void loadUsers();
    void loadAuxiliaryData();
});

function searchUsers() {
    keyword.value = keywordInput.value;
    cursor.value = undefined;
    void loadUsers();
}

function clearSearch() {
    if (keywordInput.value) return;
    keyword.value = "";
    cursor.value = undefined;
    void loadUsers();
}

function openCreateModal() {
    modalMode.value = "create";
    editingUserId.value = undefined;
    userForm.value = defaultUserForm();
    userModalOpen.value = true;
}

async function openEditModal(value: unknown) {
    const user = value as AuthenticationUserDto;
    modalMode.value = "edit";
    editingUserId.value = user.id;
    userForm.value = defaultUserForm();
    userModalOpen.value = true;
    editUserLoading.value = true;
    try {
        const detail = await getAuthenticationUser(user.id);
        if (editingUserId.value !== user.id || !userModalOpen.value) return;
        userForm.value = {
            ...defaultUserForm(),
            displayName: detail.displayName,
            email: detail.email ?? "",
            mobilePhone: detail.mobilePhone ?? "",
            departmentId: detail.departmentId,
            enabled: detail.enabled,
        };
    } catch (error) {
        ElMessage.error((error as Error).message);
    } finally {
        editUserLoading.value = false;
    }
}

function closeUserModal() {
    userModalOpen.value = false;
    editingUserId.value = undefined;
    userFormRef.value?.resetFields();
}

async function submitUser() {
    if (!(await userFormRef.value?.validate().catch(() => false))) return;
    userSubmitting.value = true;
    try {
        const values = userForm.value;
        if (modalMode.value === "create") {
            await createAuthenticationUser({
                username: values.username,
                password: values.password,
                displayName: values.displayName,
                email: values.email || undefined,
                mobilePhone: values.mobilePhone || undefined,
                departmentId: values.departmentId,
            });
            ElMessage.success("用户创建成功");
        } else if (editingUserId.value != null) {
            await updateAuthenticationUser(editingUserId.value, {
                displayName: values.displayName,
                email: values.email,
                mobilePhone: values.mobilePhone,
                departmentId: values.departmentId ?? null,
                enabled: values.enabled,
            });
            ElMessage.success("用户更新成功");
        }
        closeUserModal();
        await loadUsers();
    } catch (error) {
        ElMessage.error((error as Error).message);
    } finally {
        userSubmitting.value = false;
    }
}

async function openRoleModal(userId: number) {
    roleModalUserId.value = userId;
    selectedRoleIds.value = [];
    roleLoading.value = true;
    roleLoadFailed.value = false;
    try {
        const detail = await getAuthenticationUser(userId);
        if (roleModalUserId.value !== userId) return;
        selectedRoleIds.value = detail.roles.map((role) => role.id);
    } catch {
        roleLoadFailed.value = true;
        selectedRoleIds.value = [];
        ElMessage.error("用户角色加载失败，请重试");
    } finally {
        roleLoading.value = false;
    }
}

function closeRoleModal() {
    roleModalUserId.value = undefined;
    roleLoading.value = false;
    roleLoadFailed.value = false;
    selectedRoleIds.value = [];
}

async function saveRoles() {
    if (roleModalUserId.value == null || roleLoading.value || roleLoadFailed.value) return;
    roleSubmitting.value = true;
    try {
        await saveAuthenticationUserRoles(roleModalUserId.value, {
            roleIds: selectedRoleIds.value,
        });
        ElMessage.success("角色分配已保存");
        closeRoleModal();
        await loadUsers();
    } catch (error) {
        ElMessage.error((error as Error).message);
    } finally {
        roleSubmitting.value = false;
    }
}

function openPasswordModal(userId: number) {
    passwordModalUserId.value = userId;
    passwordForm.value = { newPassword: "" };
}

async function resetPassword() {
    if (
        !(await passwordFormRef.value?.validate().catch(() => false)) ||
        passwordModalUserId.value == null
    )
        return;
    passwordSubmitting.value = true;
    try {
        await resetAuthenticationUserPassword(passwordModalUserId.value, passwordForm.value);
        ElMessage.success("密码已重置");
        passwordModalUserId.value = undefined;
    } catch (error) {
        ElMessage.error((error as Error).message);
    } finally {
        passwordSubmitting.value = false;
    }
}
</script>

<template>
    <section class="am-page">
        <div class="am-page__header">
            <h1>用户管理</h1>
            <div class="am-page__actions">
                <el-input
                    v-model="keywordInput"
                    clearable
                    placeholder="搜索用户名或显示名称"
                    style="width: 260px"
                    @keyup.enter="searchUsers"
                    @clear="clearSearch"
                >
                    <template #append
                        ><el-button aria-label="搜索" @click="searchUsers"
                            >搜索</el-button
                        ></template
                    >
                </el-input>
                <el-button type="primary" @click="openCreateModal">新建用户</el-button>
            </div>
        </div>

        <el-table v-loading="loading" :data="users" row-key="id" empty-text="暂无用户">
            <el-table-column prop="username" label="登录名" width="140" />
            <el-table-column prop="displayName" label="姓名" width="140" />
            <el-table-column label="邮箱" width="180"
                ><template #default="{ row }">{{ row.email ?? "-" }}</template></el-table-column
            >
            <el-table-column label="手机号" width="130"
                ><template #default="{ row }">{{
                    row.mobilePhone ?? "-"
                }}</template></el-table-column
            >
            <el-table-column label="所属部门" width="160"
                ><template #default="{ row }">{{
                    row.departmentName
                        ? `${row.departmentCode ? `${row.departmentCode} ` : ""}${row.departmentName}`
                        : "-"
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
            <el-table-column label="操作" width="240"
                ><template #default="{ row }"
                    ><el-button size="small" @click="openEditModal(row)">编辑</el-button
                    ><el-button size="small" @click="openRoleModal(row.id)">角色</el-button
                    ><el-button size="small" @click="openPasswordModal(row.id)"
                        >重置密码</el-button
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
            v-model="userModalOpen"
            :title="modalMode === 'create' ? '新建用户' : '编辑用户'"
            destroy-on-close
            @closed="closeUserModal"
        >
            <el-form
                ref="userFormRef"
                v-loading="editUserLoading"
                :model="userForm"
                label-position="top"
            >
                <template v-if="modalMode === 'create'"
                    ><el-form-item
                        label="登录名"
                        prop="username"
                        :rules="[{ required: true, whitespace: true, message: '请输入登录名' }]"
                        ><el-input
                            v-model="userForm.username"
                            placeholder="例如：zhangsan" /></el-form-item
                    ><el-form-item
                        label="密码"
                        prop="password"
                        :rules="[{ required: true, message: '请输入密码' }]"
                        ><el-input
                            v-model="userForm.password"
                            type="password"
                            show-password
                            placeholder="输入密码" /></el-form-item
                ></template>
                <el-form-item
                    label="显示名称"
                    prop="displayName"
                    :rules="[{ required: true, whitespace: true, message: '请输入显示名称' }]"
                    ><el-input v-model="userForm.displayName" placeholder="例如：张三"
                /></el-form-item>
                <el-form-item label="邮箱" prop="email"
                    ><el-input v-model="userForm.email" placeholder="zhangsan@example.com"
                /></el-form-item>
                <el-form-item label="手机号" prop="mobilePhone"
                    ><el-input v-model="userForm.mobilePhone" placeholder="13800138000"
                /></el-form-item>
                <el-form-item label="所属部门" prop="departmentId"
                    ><el-select
                        v-model="userForm.departmentId"
                        clearable
                        filterable
                        :loading="auxiliaryLoading"
                        placeholder="选择所属部门"
                        ><el-option
                            v-for="department in departments"
                            :key="department.id"
                            :label="`${department.departmentCode} ${department.departmentName}`"
                            :value="department.id" /></el-select
                ></el-form-item>
                <el-form-item v-if="modalMode === 'edit'" label="启用" prop="enabled"
                    ><el-switch v-model="userForm.enabled" active-text="启用" inactive-text="停用"
                /></el-form-item>
            </el-form>
            <template #footer
                ><el-button @click="closeUserModal">取消</el-button
                ><el-button type="primary" :loading="userSubmitting" @click="submitUser"
                    >确定</el-button
                ></template
            >
        </el-dialog>

        <el-dialog
            :model-value="roleModalUserId != null"
            title="分配角色"
            destroy-on-close
            @update:model-value="!$event && closeRoleModal()"
        >
            <div v-loading="roleLoading">
                <el-alert
                    v-if="roleLoadFailed"
                    title="用户角色加载失败"
                    description="当前角色数据不可信，请关闭弹窗后重试。"
                    type="error"
                    show-icon
                    :closable="false"
                /><el-select
                    v-model="selectedRoleIds"
                    multiple
                    placeholder="选择角色"
                    style="width: 100%; margin-top: 12px"
                    ><el-option
                        v-for="role in roles"
                        :key="role.id"
                        :label="role.roleName"
                        :value="role.id"
                /></el-select>
            </div>
            <template #footer
                ><el-button @click="closeRoleModal">取消</el-button
                ><el-button
                    type="primary"
                    :disabled="roleLoading || roleLoadFailed"
                    :loading="roleSubmitting"
                    @click="saveRoles"
                    >确定</el-button
                ></template
            >
        </el-dialog>

        <el-dialog
            :model-value="passwordModalUserId != null"
            title="重置密码"
            destroy-on-close
            @update:model-value="!$event && (passwordModalUserId = undefined)"
        >
            <el-form ref="passwordFormRef" :model="passwordForm" label-position="top"
                ><el-form-item
                    label="新密码"
                    prop="newPassword"
                    :rules="[{ required: true, message: '请输入新密码' }]"
                    ><el-input
                        v-model="passwordForm.newPassword"
                        type="password"
                        show-password
                        placeholder="输入新密码" /></el-form-item
            ></el-form>
            <template #footer
                ><el-button @click="passwordModalUserId = undefined">取消</el-button
                ><el-button type="primary" :loading="passwordSubmitting" @click="resetPassword"
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
