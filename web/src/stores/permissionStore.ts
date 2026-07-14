import { computed, ref } from "vue";
import { defineStore } from "pinia";

import { getCurrentUserPermissions } from "@/shared/api/authorization";

export interface PermissionSnapshot {
    initialized: boolean;
    permissionCodes: string[];
    revision: number;
    superAdmin: boolean;
}

function emptySnapshot(revision: number): PermissionSnapshot {
    return {
        initialized: false,
        permissionCodes: [],
        revision,
        superAdmin: false,
    };
}

export const usePermissionStore = defineStore("permission-summary", () => {
    const snapshot = ref<PermissionSnapshot>(emptySnapshot(0));
    const initialized = computed(() => snapshot.value.initialized);
    const permissionCodes = computed(() => snapshot.value.permissionCodes);
    const superAdmin = computed(() => snapshot.value.superAdmin);
    const permissionCodeSet = computed(() => new Set(snapshot.value.permissionCodes));
    let requestSequence = 0;

    async function fetchSummary() {
        const requestId = ++requestSequence;
        const response = await getCurrentUserPermissions();
        if (requestId !== requestSequence) return;
        snapshot.value = {
            initialized: true,
            permissionCodes: [...response.permissionCodes],
            revision: snapshot.value.revision + 1,
            superAdmin: response.superAdmin,
        };
    }

    function has(code: string) {
        return snapshot.value.superAdmin || permissionCodeSet.value.has(code);
    }

    function reset() {
        requestSequence += 1;
        snapshot.value = emptySnapshot(snapshot.value.revision + 1);
    }

    return { snapshot, initialized, permissionCodes, superAdmin, fetchSummary, has, reset };
});
