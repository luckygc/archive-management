<script setup lang="ts">
import { computed, toRef } from "vue";

import type {
    ArchiveGovernanceBindingType,
    ArchiveGovernanceSchemeVersionDto,
    ArchiveGovernanceScopeType,
} from "@/shared/types/archive-governance";
import { useArchiveGovernanceWorkbench } from "./useArchiveGovernanceWorkbench";

const props = defineProps<{ selectedVersion?: ArchiveGovernanceSchemeVersionDto }>();
const selectedVersionId = computed(() => props.selectedVersion?.id);
const readonly = computed(() => props.selectedVersion?.status !== "DRAFT");
const scopeTypeLabels: Record<ArchiveGovernanceScopeType, string> = {
    GLOBAL: "全局默认",
    FONDS: "全宗",
    CATEGORY: "分类",
};
const bindingTypeLabels: Record<ArchiveGovernanceBindingType, string> = {
    ONTOLOGY: "本体",
    RULE_SET: "规则集",
    CLASSIFICATION_SCHEME: "分类方案",
    DESCRIPTION_PROFILE: "著录方案",
    REFERENCE_CODE_RULE: "档号规则",
};
const statusLabels = { DRAFT: "草稿", PUBLISHED: "已发布", FROZEN: "已冻结", RETIRED: "已退役" };
const {
    bindingDrafts,
    changeScopeType,
    nextDraftKey,
    resolvedVersion,
    resolveDefault,
    resolveForm,
    resolving,
    saveBindings,
    saveScopes,
    savingBindings,
    savingScopes,
    scopeDrafts,
    workbenchLoading,
} = useArchiveGovernanceWorkbench(toRef(selectedVersionId));
</script>

<template>
    <el-card v-loading="workbenchLoading" header="版本工作台" shadow="never">
        <el-empty v-if="!selectedVersion" description="请选择治理方案版本" />
        <template v-else>
            <el-descriptions :column="3" border size="small">
                <el-descriptions-item label="版本号">{{
                    selectedVersion.versionCode
                }}</el-descriptions-item>
                <el-descriptions-item label="状态"
                    ><el-tag :type="selectedVersion.status === 'PUBLISHED' ? 'primary' : 'info'">{{
                        statusLabels[selectedVersion.status]
                    }}</el-tag></el-descriptions-item
                >
                <el-descriptions-item label="版本说明">{{
                    selectedVersion.versionDescription ?? "-"
                }}</el-descriptions-item>
                <el-descriptions-item label="发布时间">{{
                    selectedVersion.publishedAt ?? "-"
                }}</el-descriptions-item>
                <el-descriptions-item label="冻结时间">{{
                    selectedVersion.frozenAt ?? "-"
                }}</el-descriptions-item>
                <el-descriptions-item label="退役时间">{{
                    selectedVersion.retiredAt ?? "-"
                }}</el-descriptions-item>
            </el-descriptions>
            <el-divider content-position="left">适用范围</el-divider>
            <div class="am-table-toolbar">
                <el-button
                    :disabled="readonly"
                    @click="
                        scopeDrafts.push({
                            draftKey: nextDraftKey(),
                            scopeType: 'GLOBAL',
                            defaultFlag: true,
                        })
                    "
                    >新增范围</el-button
                >
                <el-button
                    type="primary"
                    :disabled="readonly"
                    :loading="savingScopes"
                    @click="saveScopes"
                    >保存范围</el-button
                >
            </div>
            <el-table :data="scopeDrafts" row-key="draftKey" size="small">
                <el-table-column label="范围类型" width="150"
                    ><template #default="{ row }"
                        ><el-select
                            v-model="row.scopeType"
                            :disabled="readonly"
                            @change="changeScopeType(row)"
                            ><el-option
                                v-for="(label, value) in scopeTypeLabels"
                                :key="value"
                                :label="label"
                                :value="value" /></el-select></template
                ></el-table-column>
                <el-table-column label="全宗编码"
                    ><template #default="{ row }"
                        ><el-input
                            v-model="row.fondsCode"
                            :disabled="readonly || row.scopeType !== 'FONDS'" /></template
                ></el-table-column>
                <el-table-column label="分类编码"
                    ><template #default="{ row }"
                        ><el-input
                            v-model="row.categoryCode"
                            :disabled="readonly || row.scopeType !== 'CATEGORY'" /></template
                ></el-table-column>
                <el-table-column label="默认" width="90"
                    ><template #default="{ row }"
                        ><el-switch v-model="row.defaultFlag" :disabled="readonly" /></template
                ></el-table-column>
                <el-table-column label="操作" width="90"
                    ><template #default="{ row }"
                        ><el-button
                            link
                            type="danger"
                            :disabled="readonly"
                            @click="
                                scopeDrafts.splice(
                                    scopeDrafts.findIndex((item) => item.draftKey === row.draftKey),
                                    1,
                                )
                            "
                            >删除</el-button
                        ></template
                    ></el-table-column
                >
            </el-table>
            <el-divider content-position="left">装配绑定</el-divider>
            <div class="am-table-toolbar">
                <el-button
                    :disabled="readonly"
                    @click="
                        bindingDrafts.push({
                            draftKey: nextDraftKey(),
                            bindingType: 'ONTOLOGY',
                            bindingOrder: 0,
                        })
                    "
                    >新增绑定</el-button
                >
                <el-button
                    type="primary"
                    :disabled="readonly"
                    :loading="savingBindings"
                    @click="saveBindings"
                    >保存绑定</el-button
                >
            </div>
            <el-table :data="bindingDrafts" row-key="draftKey" size="small">
                <el-table-column label="绑定类型" width="160"
                    ><template #default="{ row }"
                        ><el-select v-model="row.bindingType" :disabled="readonly"
                            ><el-option
                                v-for="(label, value) in bindingTypeLabels"
                                :key="value"
                                :label="label"
                                :value="value" /></el-select></template
                ></el-table-column>
                <el-table-column label="目标类型" width="150"
                    ><template #default="{ row }"
                        ><el-input v-model="row.targetType" :disabled="readonly" /></template
                ></el-table-column>
                <el-table-column label="目标 ID" width="130"
                    ><template #default="{ row }"
                        ><el-input-number
                            v-model="row.targetId"
                            :min="0"
                            :disabled="readonly" /></template
                ></el-table-column>
                <el-table-column label="目标编码"
                    ><template #default="{ row }"
                        ><el-input v-model="row.targetCode" :disabled="readonly" /></template
                ></el-table-column>
                <el-table-column label="排序" width="110"
                    ><template #default="{ row }"
                        ><el-input-number
                            v-model="row.bindingOrder"
                            :disabled="readonly" /></template
                ></el-table-column>
                <el-table-column label="操作" width="90"
                    ><template #default="{ row }"
                        ><el-button
                            link
                            type="danger"
                            :disabled="readonly"
                            @click="
                                bindingDrafts.splice(
                                    bindingDrafts.findIndex(
                                        (item) => item.draftKey === row.draftKey,
                                    ),
                                    1,
                                )
                            "
                            >删除</el-button
                        ></template
                    ></el-table-column
                >
            </el-table>
            <el-divider content-position="left">默认解析试算</el-divider>
            <el-form :model="resolveForm" inline>
                <el-form-item label="全宗编码"
                    ><el-input v-model="resolveForm.fondsCode" placeholder="F001"
                /></el-form-item>
                <el-form-item label="分类编码"
                    ><el-input v-model="resolveForm.categoryCode" placeholder="case_file"
                /></el-form-item>
                <el-form-item
                    ><el-button type="primary" :loading="resolving" @click="resolveDefault"
                        >解析默认版本</el-button
                    ></el-form-item
                >
            </el-form>
            <el-descriptions v-if="resolvedVersion" :column="3" border size="small">
                <el-descriptions-item label="命中版本">{{
                    resolvedVersion.versionCode
                }}</el-descriptions-item>
                <el-descriptions-item label="状态">{{
                    statusLabels[resolvedVersion.status]
                }}</el-descriptions-item>
                <el-descriptions-item label="说明">{{
                    resolvedVersion.versionDescription ?? "-"
                }}</el-descriptions-item>
            </el-descriptions>
        </template>
    </el-card>
</template>

<style scoped>
.am-table-toolbar {
    display: flex;
    gap: 8px;
    margin-bottom: 12px;
}
</style>
