import { computed, ref } from "vue";
import { defineStore } from "pinia";

import { getCurrentUserPermissions } from "@/shared/api/authorization";

export const usePermissionStore = defineStore("permission-summary", () => {
    const initialized = ref(false);
    const permissionCodes = ref<string[]>([]);
    const superAdmin = ref(false);
    const permissionCodeSet = computed(() => new Set(permissionCodes.value));

    async function fetchSummary() {
        try {
            const response = await getCurrentUserPermissions();
            permissionCodes.value = response.permissionCodes;
            superAdmin.value = response.superAdmin;
        } catch (error) {
            permissionCodes.value = [];
            superAdmin.value = false;
            throw error;
        } finally {
            initialized.value = true;
        }
    }

    function has(code: string) {
        return permissionCodeSet.value.has(code);
    }

    function reset() {
        initialized.value = false;
        permissionCodes.value = [];
        superAdmin.value = false;
    }

    return { initialized, permissionCodes, superAdmin, fetchSummary, has, reset };
});
