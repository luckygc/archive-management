import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { usePermissionStore } from "@/stores/permissionStore";

import IntakePage from "./IntakePage.vue";

const mocks = vi.hoisted(() => ({
    acceptArchiveIntakePackage: vi.fn(),
    downloadArchiveIntakePackage: vi.fn(),
    getArchiveIntakePackage: vi.fn(),
    listArchiveIntakePackages: vi.fn(),
    receiveArchiveIntakePackage: vi.fn(),
    rejectArchiveIntakePackage: vi.fn(),
}));
const permissionApiMocks = vi.hoisted(() => ({ getCurrentUserPermissions: vi.fn() }));

vi.mock("@/shared/api/intake", () => mocks);
vi.mock("@/shared/api/authorization", () => permissionApiMocks);

beforeEach(async () => {
    setActivePinia(createPinia());
    permissionApiMocks.getCurrentUserPermissions.mockResolvedValue({
        permissionCodes: ["archive:item:read", "archive:item:create"],
        superAdmin: false,
    });
    await usePermissionStore().fetchSummary();
    mocks.listArchiveIntakePackages.mockResolvedValue({
        items: [packageRow()],
        next: "next-page",
    });
});

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

describe("IntakePage", () => {
    it("展示接收历史并复用不透明 cursor 翻页", async () => {
        renderPage();

        expect(await screen.findByText("package.zip")).toBeVisible();
        await fireEvent.click(screen.getByRole("button", { name: "下一页" }));

        await waitFor(() =>
            expect(mocks.listArchiveIntakePackages).toHaveBeenLastCalledWith({
                limit: 100,
                cursor: "next-page",
            }),
        );
    });

    it("上传期间禁止重复提交并在完成后刷新历史", async () => {
        const request = deferred<ReturnType<typeof packageDetail>>();
        mocks.receiveArchiveIntakePackage.mockImplementationOnce(() => request.promise);
        renderPage();
        await screen.findByText("package.zip");
        const input = screen.getByLabelText("选择档案信息包");
        const file = new File(["zip"], "incoming.zip", { type: "application/zip" });
        await fireEvent.change(input, { target: { files: [file] } });

        const submit = screen.getByRole("button", { name: "接收信息包" });
        await fireEvent.click(submit);
        expect(submit).toBeDisabled();
        expect(mocks.receiveArchiveIntakePackage).toHaveBeenCalledWith(file);

        request.resolve(packageDetail());
        await waitFor(() => expect(mocks.listArchiveIntakePackages).toHaveBeenCalledTimes(2));
        expect(submit).toBeDisabled();
    });

    it("失败记录展示原因且详情说明未生成正式档案", async () => {
        mocks.listArchiveIntakePackages.mockResolvedValue({
            items: [
                {
                    ...packageRow(),
                    status: "FAILED",
                    failureReason: "第 2 个档案条目档号已存在",
                },
            ],
        });
        mocks.getArchiveIntakePackage.mockResolvedValue({
            ...packageDetail(),
            status: "FAILED",
            itemCount: 0,
            failureReason: "第 2 个档案条目档号已存在",
            generatedItems: [],
        });
        renderPage();

        expect(await screen.findByText("第 2 个档案条目档号已存在")).toBeVisible();
        await fireEvent.click(screen.getByRole("button", { name: "查看结果" }));

        expect(await screen.findByText("未生成正式馆藏档案")).toBeVisible();
        expect(mocks.getArchiveIntakePackage).toHaveBeenCalledWith(1);
    });

    it("全部人工复核通过后接收入正式馆藏", async () => {
        mocks.getArchiveIntakePackage.mockResolvedValue(packageDetail());
        mocks.acceptArchiveIntakePackage.mockResolvedValue({
            ...packageDetail(),
            status: "ACCEPTED",
            reviewedAt: "2026-07-29T11:05:00",
        });
        renderPage();
        await screen.findByText("package.zip");
        await fireEvent.click(screen.getByRole("button", { name: "查看结果" }));

        const checks = await screen.findAllByRole("checkbox");
        for (const check of checks) await fireEvent.click(check);
        await fireEvent.click(screen.getByRole("button", { name: "确认接收入正式馆藏" }));

        await waitFor(() =>
            expect(mocks.acceptArchiveIntakePackage).toHaveBeenCalledWith(
                1,
                expect.objectContaining({
                    sourceFixityConfirmed: true,
                    contentReadabilityConfirmed: true,
                    antivirusPassed: true,
                    carrierSafetyConfirmed: true,
                    handoverCompleted: true,
                }),
            ),
        );
    });

    it("列表失败时提供重试并恢复结果", async () => {
        mocks.listArchiveIntakePackages
            .mockRejectedValueOnce(new Error("接收服务暂不可用"))
            .mockResolvedValueOnce({ items: [packageRow()] });
        renderPage();

        expect(await screen.findByText("接收服务暂不可用")).toBeVisible();
        await fireEvent.click(screen.getByRole("button", { name: "重试" }));

        expect(await screen.findByText("package.zip")).toBeVisible();
    });

    it("详情失败后保留当前记录 ID 并可重试", async () => {
        mocks.getArchiveIntakePackage
            .mockRejectedValueOnce(new Error("详情服务暂不可用"))
            .mockResolvedValueOnce(packageDetail());
        renderPage();
        await screen.findByText("package.zip");
        await fireEvent.click(screen.getByRole("button", { name: "查看结果" }));

        expect(await screen.findByText("详情服务暂不可用")).toBeVisible();
        await fireEvent.click(screen.getByRole("button", { name: "重试" }));

        expect(await screen.findByText("PKG-001")).toBeVisible();
        expect(mocks.getArchiveIntakePackage).toHaveBeenNthCalledWith(2, 1);
    });
});

function renderPage() {
    return render(IntakePage, { global: { plugins: [ElementPlus] } });
}

function packageRow() {
    return {
        id: 1,
        packageCode: "PKG-001",
        originalFileName: "package.zip",
        formatProfile: "DAT93_ITEM",
        contentLength: 512,
        status: "PENDING_REVIEW" as const,
        itemCount: 1,
        electronicFileCount: 2,
        electronicFileBytes: 384,
        validationPassedCount: 6,
        validationWarningCount: 0,
        validationManualCount: 5,
        createdAt: "2026-07-29T11:00:00",
        processingCompletedAt: "2026-07-29T11:00:01",
    };
}

function packageDetail() {
    return {
        ...packageRow(),
        sha256: "0".repeat(64),
        receivedBy: 9,
        processingStartedAt: "2026-07-29T11:00:00",
        sourceFixityConfirmed: false,
        contentReadabilityConfirmed: false,
        antivirusPassed: false,
        carrierSafetyConfirmed: false,
        handoverCompleted: false,
        validations: [
            {
                code: "ZIP_STRUCTURE",
                category: "INTEGRITY" as const,
                outcome: "PASSED" as const,
                message: "ZIP 目录结构符合约束",
            },
        ],
        generatedItems: [],
    };
}

function deferred<T>() {
    let resolve!: (value: T) => void;
    let reject!: (reason?: unknown) => void;
    const promise = new Promise<T>((res, rej) => {
        resolve = res;
        reject = rej;
    });
    return { promise, resolve, reject };
}
