import { ElMessage } from "element-plus";
import { computed, ref, watch } from "vue";

import { listAuthenticationUserOptions } from "@/shared/api/authentication";
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
import type { AuthenticationUserOptionDto } from "@/shared/types/authentication";
import type {
    ArchiveDataScopeDto,
    AuthorizationPermissionDto,
    AuthorizationRoleDto,
} from "@/shared/types/authorization";
import type { OrganizationDepartmentDto } from "@/shared/types/organization";
import { type PermissionSnapshot, usePermissionStore } from "@/stores/permissionStore";

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
    const users = ref<AuthenticationUserOptionDto[]>([]);
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
    let permissionCapabilityVersion = 0;
    let dataScopeCapabilityVersion = 0;
    let catalogLoadRequested = false;
    let catalogRunner: Promise<void> | undefined;
    const loadedCatalogs = {
        roles: false,
        users: false,
        departments: false,
        permissions: false,
        scopes: false,
    };
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

    function requestCatalogLoad() {
        catalogLoadRequested = true;
        if (catalogRunner) return;
        catalogRunner = runCatalogLoads().finally(() => {
            catalogRunner = undefined;
            if (catalogLoadRequested) requestCatalogLoad();
        });
    }
    async function runCatalogLoads() {
        catalogLoading.value = true;
        try {
            while (catalogLoadRequested) {
                catalogLoadRequested = false;
                await loadMissingCatalogs();
            }
        } finally {
            catalogLoading.value = false;
        }
    }
    async function loadMissingCatalogs() {
        const tasks: CatalogTask[] = [];
        if (!loadedCatalogs.roles && (canManagePermissions.value || canManageDataScopes.value)) {
            tasks.push({
                current: () => canManagePermissions.value || canManageDataScopes.value,
                request: listAuthorizationRoles(true, 1000).then((response) => () => {
                    roles.value = response.items;
                    loadedCatalogs.roles = true;
                }),
            });
        }
        if (canManagePermissions.value && !loadedCatalogs.permissions) {
            const version = permissionCapabilityVersion;
            tasks.push({
                current: () =>
                    canManagePermissions.value && version === permissionCapabilityVersion,
                request: listAuthorizationPermissions().then((response) => () => {
                    allPermissions.value = response.items;
                    loadedCatalogs.permissions = true;
                }),
            });
        }
        if (canManageDataScopes.value) addDataScopeCatalogTasks(tasks);
        if (tasks.length === 0) return;
        const results = await Promise.allSettled(tasks.map((task) => task.request));
        let currentFailure = false;
        results.forEach((result, index) => {
            const task = tasks[index];
            if (!task?.current()) return;
            if (result.status === "fulfilled") result.value();
            else currentFailure = true;
        });
        if (currentFailure) ElMessage.error("部分授权目录加载失败，请稍后重试");
    }
    function addDataScopeCatalogTasks(tasks: CatalogTask[]) {
        const version = dataScopeCapabilityVersion;
        const current = () => canManageDataScopes.value && version === dataScopeCapabilityVersion;
        if (!loadedCatalogs.users)
            tasks.push({
                current,
                request: listAuthenticationUserOptions(1000).then((response) => () => {
                    users.value = response.items;
                    loadedCatalogs.users = true;
                }),
            });
        if (!loadedCatalogs.departments)
            tasks.push({
                current,
                request: listOrganizationDepartments(true).then((response) => () => {
                    departments.value = response.items;
                    loadedCatalogs.departments = true;
                }),
            });
        if (!loadedCatalogs.scopes)
            tasks.push({
                current,
                request: listArchiveDataScopes(false).then((response) => () => {
                    allScopes.value = response.items;
                    loadedCatalogs.scopes = true;
                }),
            });
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
    watch(
        () => permissionStore.snapshot,
        (snapshot, previousSnapshot) => {
            const canManagePermission = snapshotHas(snapshot, "authorization:permission:manage");
            const canManageDataScope = snapshotHas(snapshot, "archive:data-scope:manage");
            const previousPermission = previousSnapshot
                ? snapshotHas(previousSnapshot, "authorization:permission:manage")
                : undefined;
            const previousDataScope = previousSnapshot
                ? snapshotHas(previousSnapshot, "archive:data-scope:manage")
                : undefined;
            const permissionChanged = canManagePermission !== previousPermission;
            const dataScopeChanged = canManageDataScope !== previousDataScope;
            if (!permissionChanged && !dataScopeChanged) return;
            if (permissionChanged) permissionCapabilityVersion += 1;
            if (dataScopeChanged) dataScopeCapabilityVersion += 1;
            detailRequestVersion += 1;
            detailLoading.value = false;
            if (!canManagePermission) {
                loadedCatalogs.permissions = false;
                allPermissions.value = [];
                displayPermissionCodes.value = [];
                editingPermissionCodes.value = [];
                permissionModalOpen.value = false;
            }
            if (!canManageDataScope) {
                loadedCatalogs.users = false;
                loadedCatalogs.departments = false;
                loadedCatalogs.scopes = false;
                users.value = [];
                departments.value = [];
                allScopes.value = [];
                displayScopeIds.value = [];
                editingScopeIds.value = [];
                scopeModalOpen.value = false;
                if (subjectType.value !== "role") {
                    subjectType.value = "role";
                    selectedRoleId.value = undefined;
                    selectedUserId.value = undefined;
                    selectedDepartmentId.value = undefined;
                }
            }
            requestCatalogLoad();
        },
        { flush: "sync", immediate: true },
    );
    watch(
        [
            subjectType,
            selectedRoleId,
            selectedUserId,
            selectedDepartmentId,
            canManagePermissions,
            canManageDataScopes,
        ],
        loadSubjectDetail,
    );
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

interface CatalogTask {
    current: () => boolean;
    request: Promise<() => void>;
}

function snapshotHas(snapshot: PermissionSnapshot, permissionCode: string) {
    return snapshot.superAdmin || snapshot.permissionCodes.includes(permissionCode);
}
