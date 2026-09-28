import { describe, expect, it } from "vite-plus/test";

import { pageUrl } from "./query-string";

describe("分页链接", () => {
    it("首页使用筛选参数，后续页直接使用服务端返回的链接", () => {
        expect(pageUrl("/archive-volumes", { fondsCode: "F001", limit: 100 })).toBe(
            "/archive-volumes?fondsCode=F001&limit=100",
        );
        expect(
            pageUrl("/archive-volumes", {
                fondsCode: "F002",
                limit: 200,
                cursor: "/archive-volumes?fondsCode=F001&limit=100&cursor=opaque",
            }),
        ).toBe("/archive-volumes?fondsCode=F001&limit=100&cursor=opaque");
    });

    it("拒绝跨资源与外站分页链接，避免携带会话请求错误地址", () => {
        expect(() =>
            pageUrl("/archive-volumes", {
                cursor: "/authentication-users?limit=100&cursor=opaque",
            }),
        ).toThrow("分页链接无效");
        expect(() =>
            pageUrl("/archive-volumes", {
                cursor: "https://example.org/archive-volumes?limit=100&cursor=opaque",
            }),
        ).toThrow("分页链接无效");
    });
});
