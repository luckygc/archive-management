<script setup lang="ts">
import { listAuthenticationUserOptions } from "@/shared/api/authentication";
import { listAuthorizationRoles } from "@/shared/api/authorization";
import {
    createServiceAccount,
    deleteServiceAccount,
    issueServiceAccountToken,
    listServiceAccountTokens,
    listServiceAccounts,
    replaceServiceAccountRoles,
    revokeAccessToken,
    updateServiceAccount,
} from "@/shared/api/service-account";
import type { AuthenticationUserOptionDto } from "@/shared/types/authentication";
import type { AuthorizationRoleDto } from "@/shared/types/authorization";
import type {
    AccessTokenDto,
    IssuedAccessTokenDto,
    ServiceAccountDto,
    ServiceAccountStatus,
} from "@/shared/types/service-account";
import { requestErrorMessage } from "@/shared/requestError";
import CursorPagination from "@/shared/components/CursorPagination.vue";
import { AmDataTable } from "@/shared/components/data-table";
import { ElMessage, ElMessageBox } from "element-plus";
import { onMounted, reactive, ref } from "vue";

const accounts = ref<ServiceAccountDto[]>([]);
const users = ref<AuthenticationUserOptionDto[]>([]);
const roles = ref<AuthorizationRoleDto[]>([]);
const tokens = ref<AccessTokenDto[]>([]);
const loading = ref(false);
const saving = ref(false);
const limit = ref(100);
const cursor = ref<string | null>(null);
const total = ref<number | null>();
const prev = ref<string | null>();
const next = ref<string | null>();
const editorOpen = ref(false);
const editingId = ref<number | null>(null);
const roleOpen = ref(false);
const tokenOpen = ref(false);
const secretOpen = ref(false);
const current = ref<ServiceAccountDto | null>(null);
const issued = ref<IssuedAccessTokenDto | null>(null);
const selectedRoleIds = ref<number[]>([]);
const form = reactive({
    name: "",
    description: "",
    ownerUserId: undefined as number | undefined,
    status: "ACTIVE" as ServiceAccountStatus,
    roleIds: [] as number[],
});
const tokenForm = reactive({ name: "", expiresAt: "", scopes: "" });

function formatDateTime(value?: string | null) {
    return value ? value.replace("T", " ") : "-";
}

async function loadAccounts() {
    loading.value = true;
    try {
        const response = await listServiceAccounts(undefined, limit.value, cursor.value ?? undefined);
        accounts.value = response.items;
        prev.value = response.prev;
        next.value = response.next;
        if (!cursor.value) total.value = response.total;
    } catch (error) {
        ElMessage.error(requestErrorMessage(error, "服务账号加载失败"));
    } finally {
        loading.value = false;
    }
}

async function loadOptions() {
    const [userPage, rolePage] = await Promise.all([
        listAuthenticationUserOptions(),
        listAuthorizationRoles(true),
    ]);
    users.value = userPage.items;
    roles.value = rolePage.items;
}

function openCreate() {
    editingId.value = null;
    form.name = "";
    form.description = "";
    form.ownerUserId = undefined;
    form.status = "ACTIVE";
    form.roleIds = [];
    editorOpen.value = true;
}

function openEdit(row: ServiceAccountDto) {
    editingId.value = row.id;
    form.name = row.name;
    form.description = row.description ?? "";
    form.ownerUserId = row.ownerUserId;
    form.status = row.status;
    editorOpen.value = true;
}

async function saveAccount() {
    if (!form.name.trim() || form.ownerUserId == null) {
        ElMessage.warning("请填写名称和负责人");
        return;
    }
    saving.value = true;
    try {
        if (editingId.value == null) {
            await createServiceAccount({
                name: form.name.trim(),
                description: form.description.trim() || undefined,
                ownerUserId: form.ownerUserId,
                roleIds: form.roleIds,
            });
            ElMessage.success("服务账号已创建");
        } else {
            await updateServiceAccount(editingId.value, {
                name: form.name.trim(),
                description: form.description.trim() || null,
                ownerUserId: form.ownerUserId,
                status: form.status,
            });
            ElMessage.success("服务账号已更新");
        }
        editorOpen.value = false;
        cursor.value = null;
        await loadAccounts();
    } catch (error) {
        ElMessage.error(requestErrorMessage(error, "服务账号保存失败"));
    } finally {
        saving.value = false;
    }
}

async function removeAccount(row: ServiceAccountDto) {
    await ElMessageBox.confirm(`删除服务账号「${row.name}」后，未吊销的令牌会一并吊销。`, "删除服务账号", {
        type: "warning",
    });
    try {
        await deleteServiceAccount(row.id);
        ElMessage.success("服务账号已删除");
        cursor.value = null;
        await loadAccounts();
    } catch (error) {
        ElMessage.error(requestErrorMessage(error, "服务账号删除失败"));
    }
}

function openRoles(row: ServiceAccountDto) {
    current.value = row;
    selectedRoleIds.value = [...row.roleIds];
    roleOpen.value = true;
}

async function saveRoles() {
    if (!current.value) return;
    saving.value = true;
    try {
        await replaceServiceAccountRoles(current.value.id, selectedRoleIds.value);
        ElMessage.success("角色已更新");
        roleOpen.value = false;
        await loadAccounts();
    } catch (error) {
        ElMessage.error(requestErrorMessage(error, "角色保存失败"));
    } finally {
        saving.value = false;
    }
}

async function openTokens(row: ServiceAccountDto) {
    current.value = row;
    tokenForm.name = "";
    tokenForm.expiresAt = "";
    tokenForm.scopes = "";
    tokenOpen.value = true;
    await loadTokens();
}

async function loadTokens() {
    if (!current.value) return;
    const response = await listServiceAccountTokens(current.value.id);
    tokens.value = response.items;
}

async function issueToken() {
    if (!current.value || !tokenForm.name.trim() || !tokenForm.expiresAt) {
        ElMessage.warning("请填写令牌名称和过期时间");
        return;
    }
    const scopes = tokenForm.scopes
        .split(/[\s,]+/)
        .map((item) => item.trim())
        .filter(Boolean);
    saving.value = true;
    try {
        issued.value = await issueServiceAccountToken(current.value.id, {
            name: tokenForm.name.trim(),
            expiresAt: tokenForm.expiresAt,
            scopes: scopes.length ? scopes : undefined,
        });
        secretOpen.value = true;
        tokenForm.name = "";
        tokenForm.scopes = "";
        await loadTokens();
    } catch (error) {
        ElMessage.error(requestErrorMessage(error, "令牌签发失败"));
    } finally {
        saving.value = false;
    }
}

async function copySecret() {
    if (!issued.value) return;
    await navigator.clipboard.writeText(issued.value.secret);
    ElMessage.success("已复制令牌明文");
}

function closeSecret() {
    secretOpen.value = false;
    issued.value = null;
}

async function revokeToken(row: AccessTokenDto) {
    await ElMessageBox.confirm("吊销后该令牌立即不可用。", "吊销令牌", { type: "warning" });
    try {
        await revokeAccessToken(row.id);
        ElMessage.success("令牌已吊销");
        await loadTokens();
    } catch (error) {
        ElMessage.error(requestErrorMessage(error, "令牌吊销失败"));
    }
}

onMounted(async () => {
    await Promise.all([loadAccounts(), loadOptions().catch(() => undefined)]);
});
</script>

<template>
    <section class="am-page">
        <div class="am-page__header">
            <h1>服务账号</h1>
            <el-button type="primary" @click="openCreate">新建服务账号</el-button>
        </div>
        <el-card class="am-page__result" shadow="never">
            <AmDataTable
                :data="accounts"
                :loading="loading"
                row-key="id"
                sort-mode="none"
                :columns="[
                    { key: 'name', label: '名称', width: 180 },
                    { key: 'ownerDisplayName', label: '负责人', width: 140 },
                    { key: 'status', label: '状态', width: 100 },
                    { key: 'description', label: '说明', width: 240 },
                    { key: 'createdAt', label: '创建时间', width: 180 },
                    { key: 'actions', label: '操作', width: 280 },
                ]"
            >
                <template #cell-status="{ row }">
                    <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
                        {{ row.status === "ACTIVE" ? "启用" : "停用" }}
                    </el-tag>
                </template>
                <template #cell-description="{ row }">{{ row.description || "-" }}</template>
                <template #cell-createdAt="{ row }">{{ formatDateTime(row.createdAt) }}</template>
                <template #cell-actions="{ row }">
                    <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
                    <el-button link type="primary" @click="openRoles(row)">角色</el-button>
                    <el-button link type="primary" @click="openTokens(row)">令牌</el-button>
                    <el-button link type="danger" @click="removeAccount(row)">删除</el-button>
                </template>
            </AmDataTable>
            <div class="am-table-footer">
                <CursorPagination
                    :limit="limit"
                    :total="total ?? undefined"
                    :loading="loading"
                    :next="next"
                    :prev="prev"
                    @limit-change="
                        (value) => {
                            limit = value;
                            cursor = null;
                            loadAccounts();
                        }
                    "
                    @page="
                        (value) => {
                            cursor = value;
                            loadAccounts();
                        }
                    "
                />
            </div>
        </el-card>

        <el-dialog v-model="editorOpen" :title="editingId == null ? '新建服务账号' : '编辑服务账号'" width="520px">
            <el-form label-width="88px">
                <el-form-item label="名称" required>
                    <el-input v-model="form.name" maxlength="100" />
                </el-form-item>
                <el-form-item label="说明">
                    <el-input v-model="form.description" type="textarea" maxlength="500" />
                </el-form-item>
                <el-form-item label="负责人" required>
                    <el-select v-model="form.ownerUserId" filterable placeholder="选择负责人" style="width: 100%">
                        <el-option
                            v-for="user in users"
                            :key="user.id"
                            :label="`${user.displayName}（${user.username}）`"
                            :value="user.id"
                        />
                    </el-select>
                </el-form-item>
                <el-form-item v-if="editingId == null" label="角色">
                    <el-select v-model="form.roleIds" multiple filterable placeholder="绑定角色" style="width: 100%">
                        <el-option v-for="role in roles" :key="role.id" :label="role.roleName" :value="role.id" />
                    </el-select>
                </el-form-item>
                <el-form-item v-else label="状态">
                    <el-radio-group v-model="form.status">
                        <el-radio value="ACTIVE">启用</el-radio>
                        <el-radio value="DISABLED">停用</el-radio>
                    </el-radio-group>
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="editorOpen = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="saveAccount">保存</el-button>
            </template>
        </el-dialog>

        <el-dialog v-model="roleOpen" title="绑定角色" width="480px">
            <el-select v-model="selectedRoleIds" multiple filterable placeholder="选择角色" style="width: 100%">
                <el-option v-for="role in roles" :key="role.id" :label="role.roleName" :value="role.id" />
            </el-select>
            <el-text type="info">数据范围只来自这些启用角色，不继承负责人的个人或部门范围。</el-text>
            <template #footer>
                <el-button @click="roleOpen = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="saveRoles">保存</el-button>
            </template>
        </el-dialog>

        <el-dialog v-model="tokenOpen" :title="`令牌 · ${current?.name ?? ''}`" width="760px">
            <el-form :inline="true">
                <el-form-item>
                    <el-input v-model="tokenForm.name" placeholder="令牌名称" style="width: 160px" />
                </el-form-item>
                <el-form-item>
                    <el-date-picker
                        v-model="tokenForm.expiresAt"
                        type="datetime"
                        value-format="YYYY-MM-DDTHH:mm:ss"
                        placeholder="过期时间"
                    />
                </el-form-item>
                <el-form-item>
                    <el-input
                        v-model="tokenForm.scopes"
                        placeholder="权限点，留空则默认 archive:item:read"
                        style="width: 280px"
                    />
                </el-form-item>
                <el-form-item>
                    <el-button type="primary" :loading="saving" @click="issueToken">签发</el-button>
                </el-form-item>
            </el-form>
            <AmDataTable
                :data="tokens"
                row-key="id"
                sort-mode="none"
                :columns="[
                    { key: 'name', label: '名称', width: 140 },
                    { key: 'tokenPrefix', label: '前缀', width: 150 },
                    { key: 'expiresAt', label: '过期时间', width: 170 },
                    { key: 'revokedAt', label: '吊销时间', width: 170 },
                    { key: 'actions', label: '操作', width: 90 },
                ]"
            >
                <template #cell-expiresAt="{ row }">{{ formatDateTime(row.expiresAt) }}</template>
                <template #cell-revokedAt="{ row }">{{ formatDateTime(row.revokedAt) }}</template>
                <template #cell-actions="{ row }">
                    <el-button v-if="!row.revokedAt" link type="danger" @click="revokeToken(row)">吊销</el-button>
                    <span v-else>-</span>
                </template>
            </AmDataTable>
        </el-dialog>

        <el-dialog v-model="secretOpen" title="复制令牌明文" width="560px" :close-on-click-modal="false" @closed="issued = null">
            <el-alert
                title="令牌明文只显示这一次，关闭后无法再次查看。请立即复制并妥善保存。"
                type="warning"
                show-icon
                :closable="false"
            />
            <el-input class="secret-input" :model-value="issued?.secret ?? ''" readonly type="textarea" :rows="3" />
            <template #footer>
                <el-button @click="copySecret">复制</el-button>
                <el-button type="primary" @click="closeSecret">我已保存</el-button>
            </template>
        </el-dialog>
    </section>
</template>

<style scoped>
.secret-input {
    margin-top: 12px;
}
</style>
