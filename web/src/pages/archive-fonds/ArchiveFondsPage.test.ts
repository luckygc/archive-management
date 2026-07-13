import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import ArchiveFondsPage from "./ArchiveFondsPage.vue";

const archiveApiMocks = vi.hoisted(() => ({
    listArchiveFonds: vi.fn(),
    updateArchiveFonds: vi.fn(),
}));

vi.mock("@/shared/api/archive", async (importOriginal) => ({
    ...(await importOriginal<typeof import("@/shared/api/archive")>()),
    ...archiveApiMocks,
}));

beforeEach(() => {
    archiveApiMocks.listArchiveFonds.mockResolvedValue({
        items: [
            createFonds({ id: 1, fondsCode: "HD", fondsName: "华东公司", enabled: true }),
            createFonds({ id: 2, fondsCode: "CW", fondsName: "财务部", enabled: false }),
        ],
    });
    archiveApiMocks.updateArchiveFonds.mockResolvedValue(
        createFonds({ id: 1, fondsCode: "HD", fondsName: "华东公司", enabled: false }),
    );
});

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

describe("ArchiveFondsPage", () => {
    it("通过后端接口切换全宗启用状态", async () => {
        render(ArchiveFondsPage, { global: { plugins: [ElementPlus] } });
        const switchButton = await screen.findByRole("switch", { name: "停用全宗：华东公司" });
        expect(switchButton).toBeChecked();
        await fireEvent.click(switchButton);
        await waitFor(() =>
            expect(archiveApiMocks.updateArchiveFonds).toHaveBeenCalledWith(1, {
                enabled: false,
                fondsCode: "HD",
                fondsName: "华东公司",
                sortOrder: 10,
            }),
        );
    });
});

function createFonds(values: {
    id: number;
    fondsCode: string;
    fondsName: string;
    enabled: boolean;
}) {
    return {
        createdAt: "2026-06-27T00:00:00Z",
        sortOrder: 10,
        updatedAt: "2026-06-27T00:00:00Z",
        ...values,
    };
}
