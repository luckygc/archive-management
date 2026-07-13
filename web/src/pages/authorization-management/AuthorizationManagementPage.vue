<script setup lang="ts">
import { ElMessage } from "element-plus";
import { computed, onMounted, ref, watch } from "vue";

import {
    getAuthenticationUser,
    getDepartmentArchiveDataScopes,
    getRoleArchiveDataScopes,
    getRolePermissions,
    getUserArchiveDataScopes,
    listArchiveDataScopes,
    listAuthenticationUsers,
    listAuthorizationPermissions,
    listAuthorizationRoles,
    listOrganizationDepartments,
    saveDepartmentArchiveDataScopes,
    saveRoleArchiveDataScopes,
    saveRolePermissions,
    saveUserArchiveDataScopes,
} from "@/shared/api/archive";
import type {
    ArchiveDataScopeDto,
    AuthenticationUserDetailDto,
    AuthenticationUserDto,
    AuthorizationPermissionDto,
    AuthorizationRoleDto,
    OrganizationDepartmentDto,
} from "@/shared/types/archive";

type SubjectType = "role" | "user" | "department";

const subjectType = ref<SubjectType>("role");
const selectedRoleId = ref<number>();
const selectedUserId = ref<number>();
const selectedDepartmentId = ref<number>();
const roles = ref<AuthorizationRoleDto[]>([]);
const users = ref<AuthenticationUserDto[]>([]);
const departments = ref<OrganizationDepartmentDto[]>([]);
const allPermissions = ref<AuthorizationPermissionDto[]>([]);
const allScopes = ref<ArchiveDataScopeDto[]>([]);
const userDetail = ref<AuthenticationUserDetailDto>();
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

const selectedSubjectId = computed(() => {
    if (subjectType.value === "role") return selectedRoleId.value;
    if (subjectType.value === "user") return selectedUserId.value;
    return selectedDepartmentId.value;
});
const hasSubject = computed(() => selectedSubjectId.value != null);
const selectedRole = computed(() => roles.value.find((item) => item.id === selectedRoleId.value));
const selectedUser = computed(() => users.value.find((item) => item.id === selectedUserId.value));
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

async function loadCatalogs() {
    catalogLoading.value = true;
    const results = await Promise.allSettled([
        listAuthorizationRoles(true, 1000),
        listAuthenticationUsers(undefined, 1000),
        listOrganizationDepartments(true),
        listAuthorizationPermissions(),
        listArchiveDataScopes(false),
    ]);
    const [roleResult, userResult, departmentResult, permissionResult, scopeResult] = results;
    if (roleResult?.status === "fulfilled") roles.value = roleResult.value.items;
    if (userResult?.status === "fulfilled") users.value = userResult.value.items;
    if (departmentResult?.status === "fulfilled") departments.value = departmentResult.value.items;
    if (permissionResult?.status === "fulfilled")
        allPermissions.value = permissionResult.value.items;
    if (scopeResult?.status === "fulfilled") allScopes.value = scopeResult.value.items;
    if (results.some((result) => result.status === "rejected")) {
        ElMessage.error("部分授权目录加载失败，请稍后重试");
    }
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
    userDetail.value = undefined;
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
                getRolePermissions(id),
                getRoleArchiveDataScopes(id),
            ]);
            if (version !== detailRequestVersion) return;
            displayPermissionCodes.value = permissions.permissionCodes;
            displayScopeIds.value = scopes.scopeIds;
        } else if (subjectType.value === "user") {
            const [detail, scopes] = await Promise.all([
                getAuthenticationUser(id),
                getUserArchiveDataScopes(id),
            ]);
            const roleResults = await Promise.allSettled(
                detail.roles.map((role) => getRolePermissions(role.id)),
            );
            if (version !== detailRequestVersion) return;
            userDetail.value = detail;
            displayScopeIds.value = scopes.scopeIds;
            displayPermissionCodes.value = [
                ...new Set(
                    roleResults.flatMap((result) =>
                        result.status === "fulfilled" ? result.value.permissionCodes : [],
                    ),
                ),
            ].sort();
        } else {
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
    if (subjectType.value !== "role" || selectedRoleId.value == null) return;
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
</script>

<template>
    <section class="am-page">
        <div class="am-page__header"><h1>授权管理</h1></div>
        <el-row :gutter="16">
            <el-col :xs="24" :md="8">
                <el-card header="授权主体" shadow="never">
                    <el-segmented
                        :model-value="subjectType"
                        :options="[
                            { label: '角色', value: 'role' },
                            { label: '用户', value: 'user' },
                            { label: '部门', value: 'department' },
                        ]"
                        block
                        @change="changeSubjectType"
                    />
                    <el-select
                        v-if="subjectType === 'role'"
                        v-model="selectedRoleId"
                        :loading="catalogLoading"
                        clearable
                        filterable
                        placeholder="搜索并选择角色"
                    >
                        <el-option
                            v-for="item in roles"
                            :key="item.id"
                            :label="item.roleName"
                            :value="item.id"
                        />
                    </el-select>
                    <el-select
                        v-else-if="subjectType === 'user'"
                        v-model="selectedUserId"
                        :loading="catalogLoading"
                        clearable
                        filterable
                        placeholder="搜索并选择用户"
                    >
                        <el-option
                            v-for="item in users"
                            :key="item.id"
                            :label="`${item.displayName}（${item.username}）`"
                            :value="item.id"
                        />
                    </el-select>
                    <el-select
                        v-else
                        v-model="selectedDepartmentId"
                        :loading="catalogLoading"
                        clearable
                        filterable
                        placeholder="搜索并选择部门"
                    >
                        <el-option
                            v-for="item in departments"
                            :key="item.id"
                            :label="`${item.departmentCode} ${item.departmentName}`"
                            :value="item.id"
                        />
                    </el-select>
                </el-card>

                <el-card v-if="selectedRole" class="subject-summary" shadow="never">
                    <strong>{{ selectedRole.roleName }}</strong>
                    <span>{{ selectedRole.description ?? "无说明" }}</span>
                    <el-tag :type="selectedRole.enabled ? 'success' : 'info'">
                        {{ selectedRole.enabled ? "启用" : "停用" }}
                    </el-tag>
                </el-card>
                <el-card v-if="selectedUser" class="subject-summary" shadow="never">
                    <strong>{{ selectedUser.displayName }}</strong>
                    <span
                        >{{ selectedUser.username
                        }}{{ selectedUser.email ? ` · ${selectedUser.email}` : "" }}</span
                    >
                    <el-tag :type="selectedUser.enabled ? 'success' : 'info'">
                        {{ selectedUser.enabled ? "启用" : "停用" }}
                    </el-tag>
                </el-card>
                <el-card v-if="selectedDepartment" class="subject-summary" shadow="never">
                    <strong>{{ selectedDepartment.departmentName }}</strong>
                    <span>{{ selectedDepartment.departmentCode }}</span>
                    <el-tag :type="selectedDepartment.enabled ? 'success' : 'info'">
                        {{ selectedDepartment.enabled ? "启用" : "停用" }}
                    </el-tag>
                </el-card>
            </el-col>

            <el-col v-loading="detailLoading" :xs="24" :md="16">
                <el-card v-if="!hasSubject" shadow="never">
                    <el-empty description="请在左侧选择授权主体" />
                </el-card>
                <div v-else class="authorization-details">
                    <el-card v-if="subjectType !== 'department'" shadow="never">
                        <template #header>
                            <div class="card-header">
                                <span>功能权限</span>
                                <el-button
                                    v-if="subjectType === 'role'"
                                    size="small"
                                    type="primary"
                                    @click="openPermissionEdit"
                                    >编辑</el-button
                                >
                            </div>
                        </template>
                        <p v-if="subjectType === 'user'" class="secondary-text">
                            用户权限由其角色决定
                        </p>
                        <el-empty
                            v-if="displayPermissionCodes.length === 0"
                            description="暂无功能权限"
                        />
                        <el-table
                            v-else
                            :data="displayedPermissions"
                            row-key="permissionCode"
                            size="small"
                        >
                            <el-table-column label="模块" width="80">
                                <template #default="{ row }">{{
                                    moduleName(row.moduleCode)
                                }}</template>
                            </el-table-column>
                            <el-table-column label="资源" width="120">
                                <template #default="{ row }">{{
                                    resourceName(parsePermissionCode(row.permissionCode).resource)
                                }}</template>
                            </el-table-column>
                            <el-table-column label="动作" width="120">
                                <template #default="{ row }">{{
                                    actionName(parsePermissionCode(row.permissionCode).action)
                                }}</template>
                            </el-table-column>
                            <el-table-column label="权限名称" prop="permissionName" />
                            <el-table-column label="说明" prop="description" />
                        </el-table>
                    </el-card>

                    <el-card shadow="never">
                        <template #header>
                            <div class="card-header">
                                <span>数据范围</span>
                                <el-button size="small" type="primary" @click="openScopeEdit"
                                    >编辑</el-button
                                >
                            </div>
                        </template>
                        <el-empty v-if="displayScopeIds.length === 0" description="暂无数据范围" />
                        <el-table v-else :data="displayedScopes" row-key="id" size="small">
                            <el-table-column label="范围编码" prop="scopeCode" width="160" />
                            <el-table-column label="范围名称" prop="scopeName" />
                            <el-table-column label="类型" width="100">
                                <template #default="{ row }">
                                    <el-tag :type="row.scopeType === 'ALL' ? 'primary' : 'info'">
                                        {{ row.scopeType === "ALL" ? "*" : "条件" }}
                                    </el-tag>
                                </template>
                            </el-table-column>
                            <el-table-column label="启用" width="80">
                                <template #default="{ row }">
                                    <el-tag :type="row.enabled ? 'success' : 'info'">{{
                                        row.enabled ? "启用" : "停用"
                                    }}</el-tag>
                                </template>
                            </el-table-column>
                            <el-table-column label="说明">
                                <template #default="{ row }">{{ row.description ?? "-" }}</template>
                            </el-table-column>
                        </el-table>
                    </el-card>

                    <el-card
                        v-if="subjectType === 'user' && userDetail"
                        header="所属角色"
                        shadow="never"
                    >
                        <el-empty v-if="userDetail.roles.length === 0" description="未分配角色" />
                        <el-space v-else wrap>
                            <el-tag
                                v-for="role in userDetail.roles"
                                :key="role.id"
                                type="primary"
                                >{{ role.roleName }}</el-tag
                            >
                        </el-space>
                    </el-card>
                </div>
            </el-col>
        </el-row>

        <el-dialog
            v-model="permissionModalOpen"
            title="编辑功能权限"
            width="640px"
            destroy-on-close
        >
            <el-select v-model="editingPermissionCodes" multiple placeholder="选择功能权限">
                <el-option
                    v-for="item in allPermissions"
                    :key="item.permissionCode"
                    :label="permissionOptionLabel(item)"
                    :value="item.permissionCode"
                />
            </el-select>
            <template #footer>
                <el-button @click="permissionModalOpen = false">取消</el-button>
                <el-button :loading="saving" type="primary" @click="savePermissions"
                    >保存</el-button
                >
            </template>
        </el-dialog>

        <el-dialog v-model="scopeModalOpen" title="编辑数据范围" width="640px" destroy-on-close>
            <el-select v-model="editingScopeIds" multiple placeholder="选择数据范围">
                <el-option
                    v-for="item in allScopes"
                    :key="item.id"
                    :label="item.scopeName"
                    :value="item.id"
                />
            </el-select>
            <template #footer>
                <el-button @click="scopeModalOpen = false">取消</el-button>
                <el-button :loading="saving" type="primary" @click="saveScopes">保存</el-button>
            </template>
        </el-dialog>
    </section>
</template>

<script lang="ts">
const moduleNameMap: Record<string, string> = {
    archive: "档案",
    authorization: "授权",
    authentication: "认证",
    organization: "组织",
};
const resourceNameMap: Record<string, string> = {
    item: "条目",
    audit: "审计",
    export: "导出",
    metadata: "元数据",
    "data-scope": "数据范围",
    permission: "功能权限",
    role: "角色",
    session: "会话",
    user: "用户",
    department: "部门",
};
const actionNameMap: Record<string, string> = {
    read: "读取",
    create: "创建",
    update: "修改",
    delete: "删除",
    lock: "锁定",
    manage: "管理",
    "download-electronic-file": "下载电子文件",
};

function parsePermissionCode(code: string) {
    const segments = code.split(":");
    return {
        module: segments[0] ?? "",
        resource: segments.slice(1, -1).join(":"),
        action: segments.at(-1) ?? "",
    };
}
function moduleName(code: string) {
    return moduleNameMap[code] ?? code;
}
function resourceName(code: string) {
    return resourceNameMap[code] ?? code;
}
function actionName(code: string) {
    return actionNameMap[code] ?? code;
}
function permissionOptionLabel(permission: AuthorizationPermissionDto) {
    const parsed = parsePermissionCode(permission.permissionCode);
    return `${permission.permissionName}（${moduleName(permission.moduleCode)} · ${resourceName(parsed.resource)} · ${actionName(parsed.action)}）`;
}
</script>

<style scoped>
.el-select {
    width: 100%;
}
.el-segmented + .el-select {
    margin-top: 16px;
}
.subject-summary {
    margin-top: 12px;
}
.subject-summary :deep(.el-card__body),
.authorization-details {
    display: flex;
    flex-direction: column;
    gap: 12px;
}
.subject-summary :deep(.el-card__body) {
    align-items: flex-start;
}
.card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
}
.secondary-text {
    margin-top: 0;
    color: var(--el-text-color-secondary);
}
</style>
