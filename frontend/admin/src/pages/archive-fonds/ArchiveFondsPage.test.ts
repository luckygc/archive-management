import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import ArchiveFondsPage from "./ArchiveFondsPage.vue";

const archiveApiMocks = vi.hoisted(() => ({
    assignArchiveFondsNumber: vi.fn(),
    closeArchiveFonds: vi.fn(),
    createArchiveFonds: vi.fn(),
    listArchiveFonds: vi.fn(),
    listArchiveFondsEvents: vi.fn(),
    reopenArchiveFonds: vi.fn(),
    updateArchiveFonds: vi.fn(),
}));

vi.mock("@/shared/api/archive-metadata", async (importOriginal) => ({
    ...(await importOriginal<typeof import("@/shared/api/archive-metadata")>()),
    ...archiveApiMocks,
}));

beforeEach(() => {
    archiveApiMocks.listArchiveFonds.mockResolvedValue({
        items: [
            createFonds({ id: 1, fondsCode: "SYS-HD", fondsNo: "HD", status: "ACTIVE" }),
            createFonds({ id: 2, fondsCode: "SYS-CW", fondsNo: null, status: "CLOSED" }),
        ],
    });
    archiveApiMocks.closeArchiveFonds.mockResolvedValue(
        createFonds({ id: 1, fondsCode: "SYS-HD", fondsNo: "HD", status: "CLOSED" }),
    );
    archiveApiMocks.listArchiveFondsEvents.mockResolvedValue({
        items: [
            {
                id: 11,
                fondsCode: "SYS-HD",
                eventType: "NUMBER_ASSIGNED",
                previousValue: null,
                currentValue: "HD",
                reason: "完成全宗登记",
                effectiveAt: "2026-08-01T10:00:00",
                operatedBy: 1,
                createdAt: "2026-08-01T10:00:00",
            },
        ],
    });
});

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

describe("ArchiveFondsPage", () => {
    it("通过专用动作封闭全宗并刷新行状态", async () => {
        render(ArchiveFondsPage, { global: { plugins: [ElementPlus] } });

        await fireEvent.click(await screen.findByRole("button", { name: "封闭" }));
        await fireEvent.update(screen.getByRole("textbox", { name: "办理原因" }), "机构撤并");
        await fireEvent.click(screen.getByRole("button", { name: "确认封闭" }));

        await waitFor(() =>
            expect(archiveApiMocks.closeArchiveFonds).toHaveBeenCalledWith(1, {
                effectiveAt: undefined,
                reason: "机构撤并",
            }),
        );
        await waitFor(() =>
            expect(screen.getAllByRole("button", { name: "重新开放" })).toHaveLength(2),
        );
    });

    it("展示全宗号与生命周期历史事件", async () => {
        render(ArchiveFondsPage, { global: { plugins: [ElementPlus] } });

        const eventButtons = await screen.findAllByRole("button", { name: "事件" });
        await fireEvent.click(eventButtons[0]!);

        await waitFor(() => expect(archiveApiMocks.listArchiveFondsEvents).toHaveBeenCalledWith(1));
        expect(await screen.findByText("完成全宗登记")).toBeInTheDocument();
        expect(screen.getAllByText("分配全宗号")).toHaveLength(2);
    });

    it("不提供删除入口，并为未编号全宗提供分配动作", async () => {
        render(ArchiveFondsPage, { global: { plugins: [ElementPlus] } });

        expect(await screen.findByText("SYS-CW")).toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "删除" })).not.toBeInTheDocument();
        expect(screen.getByRole("button", { name: "分配全宗号" })).toBeInTheDocument();
    });
});

function createFonds(values: {
    id: number;
    fondsCode: string;
    fondsNo: string | null;
    status: "ACTIVE" | "CLOSED";
}) {
    return {
        fondsName: values.id === 1 ? "华东公司" : "财务部",
        numberAssignedBy: null,
        numberAssignedAt: values.fondsNo ? "2026-06-27T00:00:00" : null,
        startDate: null,
        endDate: null,
        historyNote: null,
        closedAt: values.status === "CLOSED" ? "2026-08-01T00:00:00" : null,
        closureReason: values.status === "CLOSED" ? "机构撤并" : null,
        createdAt: "2026-06-27T00:00:00",
        sortOrder: 10,
        updatedAt: "2026-06-27T00:00:00",
        ...values,
    };
}
