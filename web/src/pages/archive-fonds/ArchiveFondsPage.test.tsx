import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vite-plus/test";

import { ArchiveFondsPage } from "./ArchiveFondsPage";

const archiveApiMocks = vi.hoisted(() => ({
    listArchiveFonds: vi.fn(),
    updateArchiveFonds: vi.fn(),
}));

vi.mock("@/shared/api/archive", async (importOriginal) => {
    const actual = await importOriginal<typeof import("@/shared/api/archive")>();
    return {
        ...actual,
        ...archiveApiMocks,
    };
});

beforeEach(() => {
    archiveApiMocks.listArchiveFonds.mockResolvedValue({
        items: [
            createFonds({
                id: 1,
                fondsCode: "HD",
                fondsName: "华东公司",
                enabled: true,
            }),
            createFonds({
                id: 2,
                fondsCode: "CW",
                fondsName: "财务部",
                enabled: false,
            }),
        ],
    });
    archiveApiMocks.updateArchiveFonds.mockResolvedValue(
        createFonds({ id: 1, fondsCode: "HD", fondsName: "华东公司", enabled: false }),
    );
});

afterEach(() => {
    cleanup();
});

describe("ArchiveFondsPage", () => {
    it("toggles fonds enabled state through backend update", async () => {
        render(
            <QueryClientProvider client={new QueryClient()}>
                <ArchiveFondsPage />
            </QueryClientProvider>,
        );

        const switchButton = await screen.findByRole("switch", { name: "停用全宗：华东公司" });
        expect(switchButton.getAttribute("aria-checked")).toBe("true");

        fireEvent.click(switchButton);

        await waitFor(() => {
            expect(archiveApiMocks.updateArchiveFonds).toHaveBeenCalledWith(1, {
                enabled: false,
                fondsCode: "HD",
                fondsName: "华东公司",
                sortOrder: 10,
            });
        });
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
