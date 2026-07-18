import { ElMessage } from "element-plus";
import { ref, watch, type Ref } from "vue";

import {
    listArchiveGovernanceBindings,
    listArchiveGovernanceScopes,
    replaceArchiveGovernanceBindings,
    replaceArchiveGovernanceScopes,
    resolveDefaultArchiveGovernanceVersion,
} from "@/shared/api/archive-governance";
import { listArchiveRuntimeDefinitions } from "@/shared/api/archive-rules";
import type {
    ArchiveGovernanceBindingDto,
    ArchiveGovernanceBindingRequest,
    ArchiveGovernanceBindingType,
    ArchiveGovernanceSchemeVersionDto,
    ArchiveGovernanceScopeDto,
    ArchiveGovernanceScopeRequest,
    ArchiveGovernanceScopeType,
} from "@/shared/types/archive-governance";

interface ScopeDraft {
    draftKey: string;
    id?: number;
    scopeType: ArchiveGovernanceScopeType;
    fondsCode?: string;
    categoryCode?: string;
    defaultFlag: boolean;
}

interface BindingDraft {
    draftKey: string;
    id?: number;
    bindingType: ArchiveGovernanceBindingType;
    targetType?: string;
    targetId?: number;
    targetCode?: string;
    bindingOrder: number;
}

export function useArchiveGovernanceWorkbench(selectedVersionId: Ref<number | undefined>) {
    let draftCounter = 0;
    const scopeDrafts = ref<ScopeDraft[]>([]);
    const bindingDrafts = ref<BindingDraft[]>([]);
    const workbenchLoading = ref(false);
    const savingScopes = ref(false);
    const savingBindings = ref(false);
    const resolving = ref(false);
    const resolveForm = ref({ fondsCode: "", categoryCode: "" });
    const resolvedVersion = ref<ArchiveGovernanceSchemeVersionDto>();
    const runtimeDefinitions = ref<
        Awaited<ReturnType<typeof listArchiveRuntimeDefinitions>>["items"]
    >([]);

    watch(selectedVersionId, () => {
        resolvedVersion.value = undefined;
        void loadWorkbench();
    });

    async function loadWorkbench() {
        if (!selectedVersionId.value) {
            scopeDrafts.value = [];
            bindingDrafts.value = [];
            runtimeDefinitions.value = [];
            return;
        }
        workbenchLoading.value = true;
        try {
            const [scopes, bindings, runtime] = await Promise.all([
                listArchiveGovernanceScopes(selectedVersionId.value),
                listArchiveGovernanceBindings(selectedVersionId.value),
                listArchiveRuntimeDefinitions(selectedVersionId.value),
            ]);
            scopeDrafts.value = scopes.items.map(toScopeDraft);
            bindingDrafts.value = bindings.items.map(toBindingDraft);
            runtimeDefinitions.value = runtime.items;
        } catch (error) {
            ElMessage.error((error as Error).message);
        } finally {
            workbenchLoading.value = false;
        }
    }

    async function saveScopes() {
        if (!selectedVersionId.value) return;
        savingScopes.value = true;
        try {
            await replaceArchiveGovernanceScopes(
                selectedVersionId.value,
                scopeDrafts.value.map(toScopeRequest),
            );
            ElMessage.success("适用范围已保存");
            await loadWorkbench();
        } catch (error) {
            ElMessage.error((error as Error).message);
        } finally {
            savingScopes.value = false;
        }
    }

    async function saveBindings() {
        if (!selectedVersionId.value) return;
        savingBindings.value = true;
        try {
            await replaceArchiveGovernanceBindings(
                selectedVersionId.value,
                bindingDrafts.value.map(toBindingRequest),
            );
            ElMessage.success("装配绑定已保存");
            await loadWorkbench();
        } catch (error) {
            ElMessage.error((error as Error).message);
        } finally {
            savingBindings.value = false;
        }
    }

    async function resolveDefault() {
        resolving.value = true;
        try {
            resolvedVersion.value = await resolveDefaultArchiveGovernanceVersion({
                fondsCode: trimToUndefined(resolveForm.value.fondsCode),
                categoryCode: trimToUndefined(resolveForm.value.categoryCode),
            });
        } catch (error) {
            resolvedVersion.value = undefined;
            ElMessage.error((error as Error).message);
        } finally {
            resolving.value = false;
        }
    }

    function changeScopeType(value: unknown) {
        const row = value as ScopeDraft;
        if (row.scopeType === "GLOBAL") {
            row.fondsCode = undefined;
            row.categoryCode = undefined;
        } else if (row.scopeType === "FONDS") row.categoryCode = undefined;
        else row.fondsCode = undefined;
    }

    function nextDraftKey() {
        return `draft-${++draftCounter}`;
    }

    function toScopeDraft(scope: ArchiveGovernanceScopeDto): ScopeDraft {
        return { draftKey: nextDraftKey(), ...scope };
    }

    function toBindingDraft(binding: ArchiveGovernanceBindingDto): BindingDraft {
        return { draftKey: nextDraftKey(), ...binding };
    }

    return {
        bindingDrafts,
        changeScopeType,
        nextDraftKey,
        resolvedVersion,
        resolveDefault,
        resolveForm,
        resolving,
        runtimeDefinitions,
        saveBindings,
        saveScopes,
        savingBindings,
        savingScopes,
        scopeDrafts,
        workbenchLoading,
    };
}

function toScopeRequest(scope: ScopeDraft): ArchiveGovernanceScopeRequest {
    return scope.scopeType === "GLOBAL"
        ? { scopeType: "GLOBAL", defaultFlag: scope.defaultFlag }
        : scope.scopeType === "FONDS"
          ? {
                scopeType: "FONDS",
                fondsCode: trimToUndefined(scope.fondsCode),
                defaultFlag: scope.defaultFlag,
            }
          : {
                scopeType: "CATEGORY",
                categoryCode: trimToUndefined(scope.categoryCode),
                defaultFlag: scope.defaultFlag,
            };
}

function toBindingRequest(binding: BindingDraft): ArchiveGovernanceBindingRequest {
    return {
        bindingType: binding.bindingType,
        targetType: trimToUndefined(binding.targetType),
        targetId: binding.targetId,
        targetCode: trimToUndefined(binding.targetCode),
        bindingOrder: binding.bindingOrder,
    };
}

function trimToUndefined(value?: string) {
    return value?.trim() || undefined;
}
