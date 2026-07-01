import { describe, expect, it, vi } from "vite-plus/test";

import { updateArchiveFonds } from "./archive";

const httpClientMock = vi.hoisted(() => ({
    delete: vi.fn(),
    download: vi.fn(),
    get: vi.fn(),
    patch: vi.fn(),
    post: vi.fn(),
}));

vi.mock("@archive-management/frontend-core/api", () => ({
    httpClient: httpClientMock,
}));

describe("archive API", () => {
    it("updates archive fonds through the resource PATCH endpoint", async () => {
        const payload = {
            fondsCode: "HD",
            fondsName: "华东公司",
            enabled: false,
            sortOrder: 10,
        };
        httpClientMock.patch.mockResolvedValue({ id: 1, ...payload });

        await updateArchiveFonds(1, payload);

        expect(httpClientMock.patch).toHaveBeenCalledWith("/api/v1/archive-fonds/1", payload);
    });
});
