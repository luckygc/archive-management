import { beforeEach, describe, expect, it, vi } from "vite-plus/test";

import { listMyUnifiedTodos } from "./unified-todo";

const httpClientMock = vi.hoisted(() => ({ get: vi.fn() }));

vi.mock("@archive-management/frontend-core/api", () => ({ httpClient: httpClientMock }));

beforeEach(() => vi.clearAllMocks());

describe("统一待办 API", () => {
    it("将已办状态和游标放入 URL query", async () => {
        await listMyUnifiedTodos({ completed: true, limit: 50, cursor: "next-token" });

        expect(httpClientMock.get).toHaveBeenCalledWith(
            "/api/v1/unified-todos?limit=50&completed=true&cursor=next-token",
        );
    });
});
