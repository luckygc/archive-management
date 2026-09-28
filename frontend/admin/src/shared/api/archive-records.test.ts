import { expect, it, vi } from "vite-plus/test";

import { updateArchiveRecord } from "./archive-records";

const httpClientMock = vi.hoisted(() => ({ patch: vi.fn() }));

vi.mock("@archive-management/frontend-core/api", () => ({ httpClient: httpClientMock }));

it("档案条目更新按详情表示发送 Merge Patch", async () => {
    const patch = {
        item: {
            fondsCode: "F001",
            archiveNo: null,
            archiveYear: 2026,
            securityLevelId: null,
            retentionPeriodId: 3,
        },
        dynamicFields: { title: "新标题" },
        physicalFieldValues: { boxNo: "A-1" },
    };
    await updateArchiveRecord(7, patch);

    expect(httpClientMock.patch).toHaveBeenCalledWith("/archive-items/7", patch, {
        headers: { "Content-Type": "application/merge-patch+json" },
    });
});
