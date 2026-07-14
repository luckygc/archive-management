import { ElMessage, type FormInstance } from "element-plus";
import { onMounted, ref, watch } from "vue";

import {
    createAuthenticationUser,
    getAuthenticationUser,
    listAuthenticationUsers,
    resetAuthenticationUserPassword,
    saveAuthenticationUserRoles,
    updateAuthenticationUser,
} from "@/shared/api/authentication";
import { listAuthorizationRoles } from "@/shared/api/authorization";
import { listOrganizationDepartments } from "@/shared/api/organization";
import type { AuthenticationUserDto } from "@/shared/types/authentication";
import type { AuthorizationRoleDto } from "@/shared/types/authorization";
import type { OrganizationDepartmentDto } from "@/shared/types/organization";

export function useAuthenticationUsers() {
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
            if (roleModalUserId.value === userId)
                selectedRoleIds.value = detail.roles.map((role) => role.id);
        } catch {
            roleLoadFailed.value = true;
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

    watch(cursor, () => void loadUsers());
    onMounted(() => {
        void loadUsers();
        void loadAuxiliaryData();
    });

    return {
        auxiliaryLoading,
        clearSearch,
        closeRoleModal,
        closeUserModal,
        cursor,
        departments,
        editUserLoading,
        keywordInput,
        loading,
        modalMode,
        nextCursor,
        openCreateModal,
        openEditModal,
        openPasswordModal,
        openRoleModal,
        passwordForm,
        passwordFormRef,
        passwordModalUserId,
        passwordSubmitting,
        prevCursor,
        resetPassword,
        roleLoadFailed,
        roleLoading,
        roles,
        roleModalUserId,
        roleSubmitting,
        saveRoles,
        searchUsers,
        selectedRoleIds,
        submitUser,
        userForm,
        userFormRef,
        userModalOpen,
        users,
        userSubmitting,
    };
}

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
