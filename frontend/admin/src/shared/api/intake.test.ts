import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpClient } from "@archive-management/frontend-core/api";

import {
    acceptArchiveIntakePackage,
    downloadArchiveIntakePackage,
    getArchiveIntakePackage,
    listArchiveIntakePackages,
    receiveArchiveIntakePackage,
    rejectArchiveIntakePackage,
} from "./intake";

vi.mock("@archive-management/frontend-core/api", () => ({
    httpClient: {
        get: vi.fn(),
        post: vi.fn(),
        download: vi.fn(),
    },
}));

beforeEach(() => vi.clearAllMocks());

describe("归档接收 API", () => {
    it("使用 limit 和不透明 cursor 查询接收历史", async () => {
        vi.mocked(httpClient.get).mockResolvedValue({ items: [] });

        await listArchiveIntakePackages({ limit: 100, cursor: "next-token" });

        expect(httpClient.get).toHaveBeenCalledWith(
            "/api/v1/archive-intake-packages?limit=100&cursor=next-token",
        );
    });

    it("以 multipart file 创建信息包资源", async () => {
        vi.mocked(httpClient.post).mockResolvedValue({});
        const file = new File(["zip"], "package.zip", { type: "application/zip" });

        await receiveArchiveIntakePackage(file);

        const [, body] = vi.mocked(httpClient.post).mock.calls[0]!;
        expect(body).toBeInstanceOf(FormData);
        expect((body as FormData).get("file")).toBe(file);
    });

    it("按资源 ID 查询接收详情", async () => {
        vi.mocked(httpClient.get).mockResolvedValue({});

        await getArchiveIntakePackage(12);

        expect(httpClient.get).toHaveBeenCalledWith("/api/v1/archive-intake-packages/12");
    });

    it("提交完整人工复核结论后确认接收", async () => {
        vi.mocked(httpClient.post).mockResolvedValue({});
        const review = {
            sourceFixityConfirmed: true,
            contentReadabilityConfirmed: true,
            antivirusPassed: true,
            carrierSafetyConfirmed: true,
            handoverCompleted: true,
            remark: "现场交接完成",
        };

        await acceptArchiveIntakePackage(12, review);

        expect(httpClient.post).toHaveBeenCalledWith(
            "/api/v1/archive-intake-packages/12:accept",
            review,
        );
    });

    it("携带原因退回信息包", async () => {
        vi.mocked(httpClient.post).mockResolvedValue({});

        await rejectArchiveIntakePackage(12, "移交清单不一致");

        expect(httpClient.post).toHaveBeenCalledWith("/api/v1/archive-intake-packages/12:reject", {
            reason: "移交清单不一致",
        });
    });

    it("创建短期链接并下载原始信息包", async () => {
        vi.mocked(httpClient.post).mockResolvedValue({
            url: "/api/v1/file-links/code:download",
            expiresAt: "2026-07-29T12:00:00",
        });

        await downloadArchiveIntakePackage(12);

        expect(httpClient.download).toHaveBeenCalledWith("/api/v1/file-links/code:download");
    });
});
