import { beforeEach, describe, expect, it, vi } from "vite-plus/test";

import { searchArchiveRuntimeTraces } from "./archive-rules";

const httpClientMock = vi.hoisted(() => ({
    post: vi.fn(),
}));

vi.mock("@archive-management/frontend-core/api", () => ({
    httpClient: httpClientMock,
}));

beforeEach(() => {
    vi.clearAllMocks();
    httpClientMock.post.mockResolvedValue({ items: [] });
});

describe("archive rules API", () => {
    it("规则追踪将游标分页参数放入 URL query", async () => {
        await searchArchiveRuntimeTraces({
            schemeVersionId: 11,
            triggerPoint: "ITEM_BEFORE_CREATE",
            limit: 200,
            cursor: "next-token",
            requestTotal: true,
        });

        expect(httpClientMock.post).toHaveBeenCalledWith(
            "/api/v1/archive-runtime-traces:search?limit=200&cursor=next-token",
            {
                schemeVersionId: 11,
                triggerPoint: "ITEM_BEFORE_CREATE",
            },
        );
    });
});
