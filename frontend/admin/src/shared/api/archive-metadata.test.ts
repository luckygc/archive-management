import { expect, it, vi } from "vite-plus/test";

import {
    savePublicArchiveCategoryLayout,
    updateArchiveCategory,
    updateArchiveField,
    updateArchiveUniqueConstraint,
} from "./archive-metadata";

const httpClientMock = vi.hoisted(() => ({ patch: vi.fn(), put: vi.fn() }));

vi.mock("@archive-management/frontend-core/api", async (importOriginal) => ({
    ...(await importOriginal<typeof import("@archive-management/frontend-core/api")>()),
    httpClient: httpClientMock,
}));

it("分类更新以 Merge Patch 删除空父级", async () => {
    await updateArchiveCategory(12, {
        categoryCode: "contract",
        categoryName: "合同档案",
        managementMode: "ITEM_ONLY",
        enabled: true,
        sortOrder: 0,
    });

    expect(httpClientMock.patch).toHaveBeenCalledWith(
        "/archive-categories/12",
        {
            categoryCode: "contract",
            categoryName: "合同档案",
            managementMode: "ITEM_ONLY",
            parentId: null,
            enabled: true,
            sortOrder: 0,
        },
        { headers: { "Content-Type": "application/merge-patch+json" } },
    );
});

it("布局保存整体替换字段列表", async () => {
    const payload = {
        items: [{ fieldId: 3, visible: true, colSpan: 1, rowOrder: 0, colOrder: 0 }],
    };

    await savePublicArchiveCategoryLayout(12, "TABLE", payload, "ITEM");

    expect(httpClientMock.put).toHaveBeenCalledWith(
        "/archive-categories/12/layouts/TABLE?archiveLevel=ITEM",
        payload,
    );
});

it("字段更新使用 Merge Patch 媒体类型", async () => {
    const patch = { fieldName: "新文号" };

    await updateArchiveField(12, 7, patch);

    expect(httpClientMock.patch).toHaveBeenCalledWith("/archive-categories/12/fields/7", patch, {
        headers: { "Content-Type": "application/merge-patch+json" },
    });
});

it("唯一规则更新按字段 ID 列表发送 Merge Patch", async () => {
    const patch = { fieldIds: [11, 12] };

    await updateArchiveUniqueConstraint(12, 7, patch);

    expect(httpClientMock.patch).toHaveBeenCalledWith(
        "/archive-categories/12/unique-constraints/7",
        patch,
        { headers: { "Content-Type": "application/merge-patch+json" } },
    );
});
