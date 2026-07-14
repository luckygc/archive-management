import { beforeEach, describe, expect, it, vi } from "vitest";

import { useArchiveItemLifecycle } from "./useArchiveItemLifecycle";

const mocks = vi.hoisted(() => ({
    deleteArchiveRecord: vi.fn(),
    errorMessage: vi.fn((_: unknown, fallback: string) => fallback),
    lockArchiveRecord: vi.fn(),
    message: {
        error: vi.fn(),
        success: vi.fn(),
    },
    messageBox: {
        confirm: vi.fn(),
        prompt: vi.fn(),
    },
    unlockArchiveRecord: vi.fn(),
}));

vi.mock("element-plus", () => ({ ElMessage: mocks.message, ElMessageBox: mocks.messageBox }));
vi.mock("@archive-management/frontend-core/api", () => ({ errorMessage: mocks.errorMessage }));
vi.mock("@/shared/api/archive-records", () => ({
    deleteArchiveRecord: mocks.deleteArchiveRecord,
    lockArchiveRecord: mocks.lockArchiveRecord,
    unlockArchiveRecord: mocks.unlockArchiveRecord,
}));

beforeEach(() => {
    vi.clearAllMocks();
    mocks.deleteArchiveRecord.mockResolvedValue(undefined);
    mocks.lockArchiveRecord.mockResolvedValue({ id: 9 });
    mocks.unlockArchiveRecord.mockResolvedValue({ id: 9 });
});

describe("useArchiveItemLifecycle", () => {
    it("锁定确认非空原因后刷新当前查询", async () => {
        mocks.messageBox.prompt.mockResolvedValue({ value: "  整理期间冻结  " });
        const refresh = vi.fn();
        const lifecycle = useArchiveItemLifecycle(refresh);

        await lifecycle.lock(9);

        expect(mocks.lockArchiveRecord).toHaveBeenCalledWith(9, "整理期间冻结");
        expect(refresh).toHaveBeenCalledTimes(1);
        expect(mocks.message.success).toHaveBeenCalledWith("档案已锁定");
        expect(lifecycle.busyAction.value).toBeUndefined();
    });

    it("空锁定原因不提交也不报错", async () => {
        mocks.messageBox.prompt.mockResolvedValue({ value: "   " });
        const lifecycle = useArchiveItemLifecycle(vi.fn());

        await lifecycle.lock(9);

        expect(mocks.lockArchiveRecord).not.toHaveBeenCalled();
        expect(mocks.message.error).not.toHaveBeenCalled();
        expect(lifecycle.busyAction.value).toBeUndefined();
    });

    it("解锁确认后刷新当前查询", async () => {
        mocks.messageBox.confirm.mockResolvedValue("confirm");
        const refresh = vi.fn();
        const lifecycle = useArchiveItemLifecycle(refresh);

        await lifecycle.unlock(9);

        expect(mocks.unlockArchiveRecord).toHaveBeenCalledWith(9);
        expect(refresh).toHaveBeenCalledTimes(1);
        expect(mocks.message.success).toHaveBeenCalledWith("档案已解锁");
        expect(lifecycle.busyAction.value).toBeUndefined();
    });

    it("删除确认后调用资源 DELETE 并刷新当前查询", async () => {
        mocks.messageBox.confirm.mockResolvedValue("confirm");
        const refresh = vi.fn();
        const lifecycle = useArchiveItemLifecycle(refresh);

        await lifecycle.remove(9);

        expect(mocks.deleteArchiveRecord).toHaveBeenCalledWith(9);
        expect(refresh).toHaveBeenCalledTimes(1);
        expect(mocks.message.success).toHaveBeenCalledWith("档案已删除");
        expect(lifecycle.busyAction.value).toBeUndefined();
    });

    it.each(["cancel", "close"])("取消或关闭 %s 确认时不提交、不报错", async (action) => {
        mocks.messageBox.confirm.mockRejectedValue(action);
        const refresh = vi.fn();
        const lifecycle = useArchiveItemLifecycle(refresh);

        await lifecycle.remove(9);

        expect(mocks.deleteArchiveRecord).not.toHaveBeenCalled();
        expect(refresh).not.toHaveBeenCalled();
        expect(mocks.message.error).not.toHaveBeenCalled();
        expect(lifecycle.busyAction.value).toBeUndefined();
    });

    it("失败时展示 ProblemDetail 原因且不刷新", async () => {
        const failure = new Error("服务端拒绝");
        mocks.messageBox.confirm.mockResolvedValue("confirm");
        mocks.unlockArchiveRecord.mockRejectedValue(failure);
        mocks.errorMessage.mockReturnValue("档案状态已变化");
        const refresh = vi.fn();
        const lifecycle = useArchiveItemLifecycle(refresh);

        await lifecycle.unlock(9);

        expect(mocks.errorMessage).toHaveBeenCalledWith(failure, "解锁档案失败");
        expect(mocks.message.error).toHaveBeenCalledWith("档案状态已变化");
        expect(refresh).not.toHaveBeenCalled();
        expect(lifecycle.busyAction.value).toBeUndefined();
    });

    it("动作进行中忽略并发提交", async () => {
        let resolveConfirm: ((value: string) => void) | undefined;
        mocks.messageBox.confirm.mockImplementation(
            () => new Promise<string>((resolve) => (resolveConfirm = resolve)),
        );
        const lifecycle = useArchiveItemLifecycle(vi.fn());

        const first = lifecycle.remove(9);
        const second = lifecycle.remove(9);
        expect(lifecycle.busyAction.value).toBe("delete");
        expect(mocks.messageBox.confirm).toHaveBeenCalledTimes(1);
        resolveConfirm?.("confirm");
        await Promise.all([first, second]);

        expect(mocks.deleteArchiveRecord).toHaveBeenCalledTimes(1);
        expect(lifecycle.busyAction.value).toBeUndefined();
    });
});
