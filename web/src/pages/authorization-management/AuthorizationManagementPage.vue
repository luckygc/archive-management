<script setup lang="ts">
import {
    actionName,
    moduleName,
    parsePermissionCode,
    permissionOptionLabel,
    resourceName,
} from "./authorizationLabels";
import { useAuthorizationManagement } from "./useAuthorizationManagement";

const {
    allPermissions,
    allScopes,
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
    userDetail,
    users,
} = useAuthorizationManagement();
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
