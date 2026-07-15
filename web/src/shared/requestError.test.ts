import { describe, expect, it } from "vitest";

import { HttpClientError } from "@archive-management/frontend-core/api";

import { isCursorFieldViolation, requestErrorMessage } from "./requestError";

describe("requestError", () => {
    it("保留服务端追踪 ID", () => {
        const error = new HttpClientError("查询失败", 500, "INTERNAL", [], "trace-11");

        expect(requestErrorMessage(error, "加载失败")).toBe("查询失败（追踪 ID：trace-11）");
    });

    it("只按 fieldViolations 的 cursor 字段识别游标失效", () => {
        const cursorError = new HttpClientError("请求无效", 400, "INVALID_ARGUMENT", [
            { field: "cursor", message: "游标已过期" },
        ]);
        const freeTextError = new HttpClientError("cursor 已过期", 400, "INVALID_ARGUMENT");

        expect(isCursorFieldViolation(cursorError)).toBe(true);
        expect(isCursorFieldViolation(freeTextError)).toBe(false);
    });
});
