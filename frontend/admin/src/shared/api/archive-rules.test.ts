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
    it("规则追踪沿用服务端分页链接并保留搜索请求体", async () => {
        await searchArchiveRuntimeTraces({
            triggerPoint: "ITEM_BEFORE_CREATE",
            limit: 200,
            cursor: "/archive-runtime-traces:search?limit=200&cursor=next-token",
        });

        expect(httpClientMock.post).toHaveBeenCalledWith(
            "/archive-runtime-traces:search?limit=200&cursor=next-token",
            {
                triggerPoint: "ITEM_BEFORE_CREATE",
            },
        );
    });
});
