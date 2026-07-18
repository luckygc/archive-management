import type { InjectionKey, Ref } from "vue";

export interface PageTabContext {
    fullPath: Ref<string>;
    instanceId: Ref<number>;
    version: Ref<number>;
}

export const pageTabContextKey: InjectionKey<PageTabContext> = Symbol("page-tab-context");
