import { computed, ref } from "vue";
import { defineStore } from "pinia";

import { getCurrentUserPermissions } from "@/shared/api/authorization";

export const PERMISSION_REFRESH_INTERVAL_MS = 60_000;
export const PERMISSION_SNAPSHOT_TTL_MS = 300_000;

export interface PermissionSnapshot {
    readonly expired: boolean;
    readonly initialized: boolean;
    readonly permissionCodes: readonly string[];
    readonly revision: number;
    readonly superAdmin: boolean;
    readonly validUntil: number | null;
    readonly verifiedAt: number | null;
}

function freezeSnapshot(snapshot: PermissionSnapshot): PermissionSnapshot {
    const permissionCodes = Object.freeze([...snapshot.permissionCodes]);
    return Object.freeze({ ...snapshot, permissionCodes });
}

function emptySnapshot(revision: number): PermissionSnapshot {
    return freezeSnapshot({
        expired: false,
        initialized: false,
        permissionCodes: [],
        revision,
        superAdmin: false,
        validUntil: null,
        verifiedAt: null,
    });
}

export const usePermissionStore = defineStore("permission-summary", () => {
    const mutableSnapshot = ref<PermissionSnapshot>(emptySnapshot(0));
    const snapshot = computed<PermissionSnapshot>(() => mutableSnapshot.value);
    const initialized = computed(() => mutableSnapshot.value.initialized);
    const expired = computed(() => mutableSnapshot.value.expired);
    const ready = computed(() => isReadyAt(mutableSnapshot.value, Date.now()));
    const permissionCodes = computed<readonly string[]>(
        () => mutableSnapshot.value.permissionCodes,
    );
    const superAdmin = computed(() => mutableSnapshot.value.superAdmin);
    const permissionCodeSet = computed(() => new Set(mutableSnapshot.value.permissionCodes));
    let requestGeneration = 0;
    let lastAutomaticRefreshAttemptAt: number | undefined;
    let inFlight: { generation: number; promise: Promise<void> } | undefined;

    function fetchSummary(): Promise<void> {
        expireIfNeeded();
        if (inFlight) return inFlight.promise;
        const generation = requestGeneration;
        const promise = getCurrentUserPermissions()
            .then((response) => {
                if (generation !== requestGeneration) return;
                const verifiedAt = Date.now();
                mutableSnapshot.value = freezeSnapshot({
                    expired: false,
                    initialized: true,
                    permissionCodes: response.permissionCodes,
                    revision: mutableSnapshot.value.revision + 1,
                    superAdmin: response.superAdmin,
                    validUntil: verifiedAt + PERMISSION_SNAPSHOT_TTL_MS,
                    verifiedAt,
                });
            })
            .catch((error: unknown) => {
                if (generation === requestGeneration) expireIfNeeded();
                throw error;
            })
            .finally(() => {
                if (inFlight?.promise === promise) inFlight = undefined;
            });
        inFlight = { generation, promise };
        return promise;
    }

    function ensureFresh(): Promise<void> {
        expireIfNeeded();
        return isReadyAt(mutableSnapshot.value, Date.now()) ? Promise.resolve() : fetchSummary();
    }

    function refreshIfNeeded(): Promise<void> {
        const now = Date.now();
        expireIfNeeded(now);
        if (inFlight) return inFlight.promise;
        const current = mutableSnapshot.value;
        const shouldRefresh =
            !isReadyAt(current, now) || current.validUntil! - now <= PERMISSION_REFRESH_INTERVAL_MS;
        if (!shouldRefresh) return Promise.resolve();
        if (
            lastAutomaticRefreshAttemptAt != null &&
            now - lastAutomaticRefreshAttemptAt < PERMISSION_REFRESH_INTERVAL_MS
        )
            return Promise.resolve();
        lastAutomaticRefreshAttemptAt = now;
        return fetchSummary();
    }

    function expireIfNeeded(now = Date.now()) {
        const current = mutableSnapshot.value;
        if (
            !current.initialized ||
            current.expired ||
            current.validUntil == null ||
            now < current.validUntil
        )
            return;
        mutableSnapshot.value = freezeSnapshot({
            ...current,
            expired: true,
            revision: current.revision + 1,
        });
    }

    function has(code: string) {
        const current = mutableSnapshot.value;
        return (
            isReadyAt(current, Date.now()) &&
            (current.superAdmin || permissionCodeSet.value.has(code))
        );
    }

    function reset() {
        requestGeneration += 1;
        lastAutomaticRefreshAttemptAt = undefined;
        inFlight = undefined;
        mutableSnapshot.value = emptySnapshot(mutableSnapshot.value.revision + 1);
    }

    return {
        snapshot,
        initialized,
        expired,
        ready,
        permissionCodes,
        superAdmin,
        fetchSummary,
        ensureFresh,
        refreshIfNeeded,
        has,
        reset,
    };
});

function isReadyAt(snapshot: PermissionSnapshot, now: number) {
    return (
        snapshot.initialized &&
        !snapshot.expired &&
        snapshot.validUntil != null &&
        now < snapshot.validUntil
    );
}
