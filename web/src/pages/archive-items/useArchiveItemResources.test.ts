import { cleanup, render } from "@testing-library/vue";
import { createPinia, setActivePinia } from "pinia";
import { defineComponent, h } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useArchiveItemResources } from "./useArchiveItemResources";

const mocks = vi.hoisted(() => ({
    downloadArchiveItemElectronicFile: vi.fn(),
    listArchiveItemAudits: vi.fn(),
    listArchiveItemElectronicFiles: vi.fn(),
    unbindArchiveItemElectronicFile: vi.fn(),
    uploadArchiveItemElectronicFile: vi.fn(),
}));

vi.mock("@/shared/api/archive-records", () => mocks);

beforeEach(() => {
    setActivePinia(createPinia());
});

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

describe("useArchiveItemResources", () => {
    it("关闭档案 A 后打开档案 B 文件失败时不保留 A 文件", async () => {
        mocks.listArchiveItemElectronicFiles
            .mockResolvedValueOnce({ items: [electronicFile(10, 1, "A.pdf")] })
            .mockRejectedValueOnce(new Error("B 文件加载失败"));
        const resources = renderResources();

        await resources.openDrawer({ id: 1 }, "files");
        expect(resources.files.value.map((item) => item.originalFilename)).toEqual(["A.pdf"]);

        resources.drawerState.value = undefined;
        await resources.openDrawer({ id: 2 }, "files");

        expect(resources.drawerState.value).toEqual(
            expect.objectContaining({ archiveItemId: 2, activeKey: "files" }),
        );
        expect(resources.drawerLoadError.value).toBe("B 文件加载失败");
        expect(resources.files.value).toEqual([]);
    });

    it("从档案 A 切换档案 B 审计失败时不保留 A 审计", async () => {
        mocks.listArchiveItemAudits
            .mockResolvedValueOnce({ items: [audit(20, 1, "CREATE")] })
            .mockRejectedValueOnce(new Error("B 审计加载失败"));
        const resources = renderResources();

        await resources.openDrawer({ id: 1 }, "audits");
        expect(resources.audits.value.map((item) => item.operationType)).toEqual(["CREATE"]);

        await resources.openDrawer({ id: 2 }, "audits");

        expect(resources.drawerState.value?.archiveItemId).toBe(2);
        expect(resources.drawerLoadError.value).toBe("B 审计加载失败");
        expect(resources.audits.value).toEqual([]);
    });
});

function renderResources() {
    let resources!: ReturnType<typeof useArchiveItemResources>;
    render(
        defineComponent({
            setup() {
                resources = useArchiveItemResources(vi.fn());
                return () => h("div");
            },
        }),
    );
    return resources;
}

function electronicFile(id: number, archiveItemId: number, originalFilename: string) {
    return {
        id,
        archiveItemId,
        storageObjectId: id,
        usageType: "DEFAULT",
        displayOrder: 0,
        originalFilename,
        fileSize: 1,
        createdAt: "",
    };
}

function audit(id: number, archiveItemId: number, operationType: string) {
    return {
        id,
        sourceTableName: "am_archive_item",
        sourceItemId: archiveItemId,
        archiveItemId,
        operationType,
        operatedAt: "",
    };
}
