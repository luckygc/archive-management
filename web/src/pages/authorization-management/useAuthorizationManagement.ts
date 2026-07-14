import { ElMessage } from "element-plus";
import { computed, onMounted, ref, watch } from "vue";

import { listAuthenticationUsers } from "@/shared/api/authentication";
import {
    getDepartmentArchiveDataScopes,
    getRoleArchiveDataScopes,
    getRolePermissions,
    getUserArchiveDataScopes,
    listArchiveDataScopes,
    listAuthorizationPermissions,
    listAuthorizationRoles,
    saveDepartmentArchiveDataScopes,
    saveRoleArchiveDataScopes,
    saveRolePermissions,
    saveUserArchiveDataScopes,
} from "@/shared/api/authorization";
import { listOrganizationDepartments } from "@/shared/api/organization";
import type { AuthenticationUserDto } from "@/shared/types/authentication";
import type {
    ArchiveDataScopeDto,
    AuthorizationPermissionDto,
    AuthorizationRoleDto,
} from "@/shared/types/authorization";
import type { OrganizationDepartmentDto } from "@/shared/types/organization";
import { usePermissionStore } from "@/stores/permissionStore";

type SubjectType = "role" | "user" | "department";

export function useAuthorizationManagement() {
    const permissionStore = usePermissionStore();
    const canManagePermissions = computed(() =>
        permissionStore.has("authorization:permission:manage"),
    );
    const canManageDataScopes = computed(() => permissionStore.has("archive:data-scope:manage"));
    const subjectType = ref<SubjectType>("role");
    const selectedRoleId = ref<number>();
    const selectedUserId = ref<number>();
    const selectedDepartmentId = ref<number>();
    const roles = ref<AuthorizationRoleDto[]>([]);
    const users = ref<AuthenticationUserDto[]>([]);
    const departments = ref<OrganizationDepartmentDto[]>([]);
    const allPermissions = ref<AuthorizationPermissionDto[]>([]);
    const allScopes = ref<ArchiveDataScopeDto[]>([]);
    const displayPermissionCodes = ref<string[]>([]);
    const displayScopeIds = ref<number[]>([]);
    const catalogLoading = ref(false);
    const detailLoading = ref(false);
    const saving = ref(false);
    const permissionModalOpen = ref(false);
    const scopeModalOpen = ref(false);
    const editingPermissionCodes = ref<string[]>([]);
    const editingScopeIds = ref<number[]>([]);
    let detailRequestVersion = 0;
    const selectedSubjectId = computed(() =>
        subjectType.value === "role"
            ? selectedRoleId.value
            : subjectType.value === "user"
              ? selectedUserId.value
              : selectedDepartmentId.value,
    );
    const hasSubject = computed(() => selectedSubjectId.value != null);
    const selectedRole = computed(() =>
        roles.value.find((item) => item.id === selectedRoleId.value),
    );
    const selectedUser = computed(() =>
        users.value.find((item) => item.id === selectedUserId.value),
    );
    const selectedDepartment = computed(() =>
        departments.value.find((item) => item.id === selectedDepartmentId.value),
    );
    const displayedPermissions = computed(() =>
        allPermissions.value.filter((item) =>
            displayPermissionCodes.value.includes(item.permissionCode),
        ),
    );
    const displayedScopes = computed(() =>
        allScopes.value.filter((item) => displayScopeIds.value.includes(item.id)),
    );
    const subjectOptions = computed(() => [
        { label: "角色", value: "role" },
        ...(canManageDataScopes.value
            ? [
                  { label: "用户", value: "user" },
                  { label: "部门", value: "department" },
              ]
            : []),
    ]);

    async function loadCatalogs() {
        catalogLoading.value = true;
        const results = await Promise.allSettled([
            listAuthorizationRoles(true, 1000),
            canManageDataScopes.value
                ? listAuthenticationUsers(undefined, 1000)
                : Promise.resolve(undefined),
            canManageDataScopes.value
                ? listOrganizationDepartments(true)
                : Promise.resolve(undefined),
            canManagePermissions.value
                ? listAuthorizationPermissions()
                : Promise.resolve(undefined),
            canManageDataScopes.value ? listArchiveDataScopes(false) : Promise.resolve(undefined),
        ]);
        const [roleResult, userResult, departmentResult, permissionResult, scopeResult] = results;
        if (roleResult?.status === "fulfilled") roles.value = roleResult.value.items;
        if (userResult?.status === "fulfilled" && userResult.value)
            users.value = userResult.value.items;
        if (departmentResult?.status === "fulfilled" && departmentResult.value)
            departments.value = departmentResult.value.items;
        if (permissionResult?.status === "fulfilled" && permissionResult.value)
            allPermissions.value = permissionResult.value.items;
        if (scopeResult?.status === "fulfilled" && scopeResult.value)
            allScopes.value = scopeResult.value.items;
        if (results.some((result) => result.status === "rejected"))
            ElMessage.error("部分授权目录加载失败，请稍后重试");
        catalogLoading.value = false;
    }
    function changeSubjectType(value: string | number | boolean) {
        subjectType.value = value as SubjectType;
        selectedRoleId.value = undefined;
        selectedUserId.value = undefined;
        selectedDepartmentId.value = undefined;
        clearDetail();
    }
    function clearDetail() {
        detailRequestVersion += 1;
        displayPermissionCodes.value = [];
        displayScopeIds.value = [];
        detailLoading.value = false;
    }
    async function loadSubjectDetail() {
        const id = selectedSubjectId.value;
        if (id == null) {
            clearDetail();
            return;
        }
        const version = ++detailRequestVersion;
        detailLoading.value = true;
        try {
            if (subjectType.value === "role") {
                const [permissions, scopes] = await Promise.all([
                    canManagePermissions.value
                        ? getRolePermissions(id)
                        : Promise.resolve(undefined),
                    canManageDataScopes.value
                        ? getRoleArchiveDataScopes(id)
                        : Promise.resolve(undefined),
                ]);
                if (version !== detailRequestVersion) return;
                displayPermissionCodes.value = permissions?.permissionCodes ?? [];
                displayScopeIds.value = scopes?.scopeIds ?? [];
            } else if (subjectType.value === "user") {
                if (!canManageDataScopes.value) return;
                const scopes = await getUserArchiveDataScopes(id);
                if (version !== detailRequestVersion) return;
                displayScopeIds.value = scopes.scopeIds;
            } else {
                if (!canManageDataScopes.value) return;
                const scopes = await getDepartmentArchiveDataScopes(id);
                if (version !== detailRequestVersion) return;
                displayScopeIds.value = scopes.scopeIds;
            }
        } catch (error) {
            if (version === detailRequestVersion) {
                clearDetail();
                ElMessage.error(error instanceof Error ? error.message : "授权详情加载失败");
            }
        } finally {
            if (version === detailRequestVersion) detailLoading.value = false;
        }
    }
    function openPermissionEdit() {
        editingPermissionCodes.value = [...displayPermissionCodes.value];
        permissionModalOpen.value = true;
    }
    function openScopeEdit() {
        editingScopeIds.value = [...displayScopeIds.value];
        scopeModalOpen.value = true;
    }
    async function savePermissions() {
        if (
            !canManagePermissions.value ||
            subjectType.value !== "role" ||
            selectedRoleId.value == null
        )
            return;
        saving.value = true;
        try {
            await saveRolePermissions(selectedRoleId.value, editingPermissionCodes.value);
            permissionModalOpen.value = false;
            ElMessage.success("功能权限已保存");
            await loadSubjectDetail();
        } catch (error) {
            ElMessage.error(error instanceof Error ? error.message : "功能权限保存失败");
        } finally {
            saving.value = false;
        }
    }
    async function saveScopes() {
        if (!canManageDataScopes.value) return;
        const id = selectedSubjectId.value;
        if (id == null) return;
        saving.value = true;
        try {
            if (subjectType.value === "role")
                await saveRoleArchiveDataScopes(id, editingScopeIds.value);
            else if (subjectType.value === "user")
                await saveUserArchiveDataScopes(id, editingScopeIds.value);
            else await saveDepartmentArchiveDataScopes(id, editingScopeIds.value);
            scopeModalOpen.value = false;
            ElMessage.success("数据范围已保存");
            await loadSubjectDetail();
        } catch (error) {
            ElMessage.error(error instanceof Error ? error.message : "数据范围保存失败");
        } finally {
            saving.value = false;
        }
    }
    watch([subjectType, selectedRoleId, selectedUserId, selectedDepartmentId], loadSubjectDetail);
    onMounted(loadCatalogs);
    return {
        allPermissions,
        allScopes,
        canManageDataScopes,
        canManagePermissions,
        catalogLoading,
        changeSubjectType,
        departments,
        detailLoading,
        displayPermissionCodes,
        displayScopeIds,
        displayedPermissions,
        displayedScopes,
        editingPermissionCodes,
        editingScopeIds,
        hasSubject,
        openPermissionEdit,
        openScopeEdit,
        permissionModalOpen,
        roles,
        savePermissions,
        saveScopes,
        saving,
        scopeModalOpen,
        selectedDepartment,
        selectedDepartmentId,
        selectedRole,
        selectedRoleId,
        selectedUser,
        selectedUserId,
        subjectType,
        subjectOptions,
        users,
    };
}
