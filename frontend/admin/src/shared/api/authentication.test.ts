import { describe, expect, it, vi } from "vite-plus/test";

import * as authenticationApi from "./authentication";

const httpClientMock = vi.hoisted(() => ({
    get: vi.fn(),
}));

vi.mock("@archive-management/frontend-core/api", () => ({
    httpClient: httpClientMock,
}));

describe("authentication API", () => {
    it("授权用户选项目录使用独立游标资源", async () => {
        httpClientMock.get.mockResolvedValue({ items: [] });
        const listUserOptions = Reflect.get(
            authenticationApi,
            "listAuthenticationUserOptions",
        ) as unknown;

        expect(listUserOptions).toBeTypeOf("function");
        if (typeof listUserOptions !== "function") return;

        await listUserOptions(100, "next-user");

        expect(httpClientMock.get).toHaveBeenCalledWith(
            "/api/v1/authentication-user-options?limit=100&cursor=next-user",
        );
    });
});
