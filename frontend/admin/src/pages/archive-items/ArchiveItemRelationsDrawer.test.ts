import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/vue";
import ElementPlus, { ElMessage, ElMessageBox } from "element-plus";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import ArchiveItemRelationsDrawer from "./ArchiveItemRelationsDrawer.vue";

const mocks = vi.hoisted(() => ({
    createArchiveItemRelation: vi.fn(),
    deleteArchiveItemRelation: vi.fn(),
    discoverArchiveRecords: vi.fn(),
    listArchiveCategories: vi.fn(),
    listArchiveItemRelations: vi.fn(),
}));

vi.mock("@/shared/api/archive-metadata", () => ({
    listArchiveCategories: mocks.listArchiveCategories,
}));
vi.mock("@/shared/api/archive-records", () => ({
    createArchiveItemRelation: mocks.createArchiveItemRelation,
    deleteArchiveItemRelation: mocks.deleteArchiveItemRelation,
    discoverArchiveRecords: mocks.discoverArchiveRecords,
    listArchiveItemRelations: mocks.listArchiveItemRelations,
}));

beforeEach(() => {
    mocks.listArchiveCategories.mockResolvedValue({
        items: [
            {
                id: 7,
                categoryCode: "contract",
                categoryName: "合同档案",
                enabled: true,
            },
            {
                id: 8,
                categoryCode: "accounting",
                categoryName: "会计档案",
                enabled: true,
            },
        ],
    });
    mocks.listArchiveItemRelations.mockResolvedValue({
        items: [relation(8, 2, "A-2026-002")],
        next: "relation-next",
    });
    mocks.discoverArchiveRecords.mockResolvedValue({
        fields: [],
        items: [
            { id: 1, archiveNo: "A-2026-001" },
            { id: 2, archiveNo: "A-2026-002" },
        ],
        next: "candidate-next",
    });
    mocks.createArchiveItemRelation.mockResolvedValue(relation(9, 2, "A-2026-002"));
    mocks.deleteArchiveItemRelation.mockResolvedValue(undefined);
});

afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
    vi.clearAllMocks();
});

describe("ArchiveItemRelationsDrawer", () => {
    it("使用 cursor 读取关系并保持 depth", async () => {
        renderDrawer();

        expect(await screen.findByText("A-2026-002")).toBeInTheDocument();
        await fireEvent.click(screen.getByRole("button", { name: "下一页" }));

        await waitFor(() =>
            expect(mocks.listArchiveItemRelations).toHaveBeenLastCalledWith(1, {
                depth: 1,
                limit: 100,
                cursor: "relation-next",
            }),
        );
    });

    it("按分类和关键词 discover，排除当前档案后创建关系并只刷新关系", async () => {
        renderDrawer();
        await screen.findByText("A-2026-002");

        await fireEvent.click(screen.getByRole("combobox", { name: "目标档案分类" }));
        await fireEvent.click(await screen.findByRole("option", { name: "合同档案" }));
        await fireEvent.update(screen.getByRole("textbox", { name: "目标档案关键词" }), "2026");
        await fireEvent.click(screen.getByRole("button", { name: "搜索目标档案" }));

        await waitFor(() =>
            expect(mocks.discoverArchiveRecords).toHaveBeenCalledWith({
                categoryId: 7,
                keyword: "2026",
                limit: 100,
                cursor: undefined,
            }),
        );
        expect(screen.queryByRole("radio", { name: /A-2026-001/ })).not.toBeInTheDocument();
        await fireEvent.click(await screen.findByRole("radio", { name: /A-2026-002/ }));
        await fireEvent.click(screen.getByRole("button", { name: "确认关联" }));

        await waitFor(() => expect(mocks.createArchiveItemRelation).toHaveBeenCalledWith(1, 2));
        expect(mocks.listArchiveItemRelations).toHaveBeenCalledTimes(2);
        expect(mocks.discoverArchiveRecords).toHaveBeenCalledTimes(1);
    });

    it("关闭或切换档案后忽略旧请求结果", async () => {
        const oldRequest = deferred<{ items: ReturnType<typeof relation>[] }>();
        mocks.listArchiveItemRelations.mockReset();
        mocks.listArchiveItemRelations
            .mockReturnValueOnce(oldRequest.promise)
            .mockResolvedValueOnce({ items: [relation(10, 3, "B-003")] });
        const view = renderDrawer(1);
        await view.rerender({ archiveItemId: 3, active: true, canUpdate: true });
        oldRequest.resolve({ items: [relation(8, 2, "OLD-002")] });

        expect(await screen.findByText("B-003")).toBeInTheDocument();
        await waitFor(() => expect(screen.queryByText("OLD-002")).not.toBeInTheDocument());
    });

    it("删除确认期间切换档案后不向新档案提交旧关系", async () => {
        const confirmation = deferred<string>();
        vi.spyOn(ElMessageBox, "confirm").mockReturnValue(
            confirmation.promise as ReturnType<typeof ElMessageBox.confirm>,
        );
        const view = renderDrawer(1);
        await screen.findByText("A-2026-002");

        await fireEvent.click(screen.getByRole("button", { name: "删除" }));
        await view.rerender({ archiveItemId: 3, active: true, canUpdate: true });
        confirmation.resolve("confirm");

        await waitFor(() => expect(mocks.listArchiveItemRelations).toHaveBeenCalledTimes(2));
        expect(mocks.deleteArchiveItemRelation).not.toHaveBeenCalled();
    });

    it("切换目标分类立即清空候选和已选择目标", async () => {
        renderDrawer();
        await searchCategory("合同档案");
        await fireEvent.click(await screen.findByRole("radio", { name: /A-2026-002/ }));
        expect(screen.getByRole("button", { name: "确认关联" })).toBeEnabled();

        await selectCategory("会计档案");

        expect(screen.queryByRole("radio", { name: /A-2026-002/ })).not.toBeInTheDocument();
        expect(screen.getByRole("button", { name: "确认关联" })).toBeDisabled();
    });

    it("切换目标分类后忽略旧分类的在途候选响应", async () => {
        const oldRequest = deferred<{
            fields: never[];
            items: Array<{ id: number; archiveNo: string }>;
        }>();
        mocks.discoverArchiveRecords.mockReset();
        mocks.discoverArchiveRecords.mockReturnValueOnce(oldRequest.promise);
        renderDrawer();
        await selectCategory("合同档案");
        await fireEvent.click(screen.getByRole("button", { name: "搜索目标档案" }));
        await waitFor(() => expect(mocks.discoverArchiveRecords).toHaveBeenCalledTimes(1));

        await selectCategory("会计档案");
        oldRequest.resolve({ fields: [], items: [{ id: 2, archiveNo: "OLD-002" }] });
        await flushAsync();

        expect(screen.queryByRole("radio", { name: /OLD-002/ })).not.toBeInTheDocument();
    });

    it("候选翻页后清除上一页选择", async () => {
        renderDrawer();
        await searchCategory("合同档案");
        await fireEvent.click(await screen.findByRole("radio", { name: /A-2026-002/ }));

        await fireEvent.click(screen.getAllByRole("button", { name: "下一页" }).at(-1)!);

        expect(screen.getByRole("button", { name: "确认关联" })).toBeDisabled();
        expect(mocks.discoverArchiveRecords).toHaveBeenLastCalledWith({
            categoryId: 7,
            keyword: undefined,
            limit: 100,
            cursor: "candidate-next",
        });
    });

    it("修改候选页大小后清除上一页选择", async () => {
        renderDrawer();
        await searchCategory("合同档案");
        await fireEvent.click(await screen.findByRole("radio", { name: /A-2026-002/ }));

        await fireEvent.click(screen.getAllByRole("combobox", { name: "每页条数" }).at(-1)!);
        await fireEvent.click((await screen.findAllByRole("option", { name: "200 条" })).at(-1)!);

        expect(screen.getByRole("button", { name: "确认关联" })).toBeDisabled();
        await waitFor(() =>
            expect(mocks.discoverArchiveRecords).toHaveBeenLastCalledWith({
                categoryId: 7,
                keyword: undefined,
                limit: 200,
                cursor: undefined,
            }),
        );
    });

    it("组件卸载后完成删除确认也不发送请求", async () => {
        const confirmation = deferred<string>();
        vi.spyOn(ElMessageBox, "confirm").mockReturnValue(
            confirmation.promise as ReturnType<typeof ElMessageBox.confirm>,
        );
        const view = renderDrawer();
        await screen.findByText("A-2026-002");
        await fireEvent.click(screen.getByRole("button", { name: "删除" }));

        view.unmount();
        confirmation.resolve("confirm");
        await flushAsync();

        expect(mocks.deleteArchiveItemRelation).not.toHaveBeenCalled();
    });

    it("组件卸载后忽略在途创建结果且不提示或刷新", async () => {
        const createRequest = deferred<ReturnType<typeof relation>>();
        const success = vi.spyOn(ElMessage, "success");
        mocks.createArchiveItemRelation.mockReset();
        mocks.createArchiveItemRelation.mockReturnValueOnce(createRequest.promise);
        const view = renderDrawer();
        await searchCategory("合同档案");
        await fireEvent.click(await screen.findByRole("radio", { name: /A-2026-002/ }));
        await fireEvent.click(screen.getByRole("button", { name: "确认关联" }));
        await waitFor(() => expect(mocks.createArchiveItemRelation).toHaveBeenCalledTimes(1));

        view.unmount();
        createRequest.resolve(relation(9, 2, "A-2026-002"));
        await flushAsync();

        expect(success).not.toHaveBeenCalled();
        expect(mocks.listArchiveItemRelations).toHaveBeenCalledTimes(1);
    });

    it("卸载后旧列表结果不污染新组件", async () => {
        const oldRequest = deferred<{ items: ReturnType<typeof relation>[] }>();
        mocks.listArchiveItemRelations.mockReset();
        mocks.listArchiveItemRelations
            .mockReturnValueOnce(oldRequest.promise)
            .mockResolvedValueOnce({ items: [relation(10, 4, "CURRENT-004")] });
        const oldView = renderDrawer(1);
        oldView.unmount();
        renderDrawer(3);
        oldRequest.resolve({ items: [relation(8, 2, "OLD-002")] });

        expect(await screen.findByText("CURRENT-004")).toBeInTheDocument();
        expect(screen.queryByText("OLD-002")).not.toBeInTheDocument();
    });

    it("分类加载失败后原位重试分类请求", async () => {
        mocks.listArchiveCategories.mockReset();
        mocks.listArchiveCategories
            .mockRejectedValueOnce(new Error("分类服务不可用"))
            .mockResolvedValueOnce({
                items: [
                    {
                        id: 7,
                        categoryCode: "contract",
                        categoryName: "合同档案",
                        enabled: true,
                    },
                ],
            });
        renderDrawer();

        await fireEvent.click(await screen.findByRole("button", { name: "重试加载分类" }));

        await waitFor(() => expect(mocks.listArchiveCategories).toHaveBeenCalledTimes(2));
        await fireEvent.click(screen.getByRole("combobox", { name: "目标档案分类" }));
        expect(await screen.findByRole("option", { name: "合同档案" })).toBeInTheDocument();
        expect(mocks.discoverArchiveRecords).not.toHaveBeenCalled();
    });
});

async function searchCategory(categoryName: string) {
    await selectCategory(categoryName);
    await fireEvent.click(screen.getByRole("button", { name: "搜索目标档案" }));
    await waitFor(() => expect(mocks.discoverArchiveRecords).toHaveBeenCalled());
}

async function selectCategory(categoryName: string) {
    await fireEvent.click(screen.getByRole("combobox", { name: "目标档案分类" }));
    await fireEvent.click(await screen.findByRole("option", { name: categoryName }));
}

function renderDrawer(archiveItemId = 1) {
    return render(ArchiveItemRelationsDrawer, {
        props: { archiveItemId, active: true, canUpdate: true },
        global: { plugins: [ElementPlus], stubs: { TransitionGroup: false } },
    });
}

function relation(id: number, relatedItemId: number, archiveNo: string) {
    return {
        id,
        sourceItemId: 1,
        targetItemId: relatedItemId,
        relatedItemId,
        direction: "OUTGOING" as const,
        createdAt: "2026-07-15T10:00:00",
        relatedItem: {
            itemId: relatedItemId,
            fondsCode: "F001",
            fondsName: "默认全宗",
            categoryCode: "contract",
            categoryName: "合同档案",
            archiveNo,
        },
    };
}

function deferred<T>() {
    let resolve!: (value: T) => void;
    const promise = new Promise<T>((resolvePromise) => {
        resolve = resolvePromise;
    });
    return { promise, resolve };
}

async function flushAsync() {
    await Promise.resolve();
    await Promise.resolve();
}
