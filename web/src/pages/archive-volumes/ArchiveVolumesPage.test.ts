import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/vue";
import ElementPlus, { ElMessage } from "element-plus";
import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { usePermissionStore } from "@/stores/permissionStore";
import ArchiveVolumeEditorDrawer from "./ArchiveVolumeEditorDrawer.vue";
import ArchiveVolumeItemsDrawer from "./ArchiveVolumeItemsDrawer.vue";
import ArchiveVolumesPage from "./ArchiveVolumesPage.vue";

const mocks = vi.hoisted(() => ({
    addArchiveItemToVolume: vi.fn(),
    createArchiveVolume: vi.fn(),
    getArchiveVolume: vi.fn(),
    listArchiveCategories: vi.fn(),
    listArchiveFonds: vi.fn(),
    listArchiveVolumes: vi.fn(),
    searchArchiveRecords: vi.fn(),
}));

vi.mock("@/shared/api/archive-volumes", () => ({
    addArchiveItemToVolume: mocks.addArchiveItemToVolume,
    createArchiveVolume: mocks.createArchiveVolume,
    getArchiveVolume: mocks.getArchiveVolume,
    listArchiveVolumes: mocks.listArchiveVolumes,
}));
vi.mock("@/shared/api/archive-metadata", () => ({
    listArchiveCategories: mocks.listArchiveCategories,
    listArchiveFonds: mocks.listArchiveFonds,
}));
vi.mock("@/shared/api/archive-records", () => ({
    searchArchiveRecords: mocks.searchArchiveRecords,
}));

beforeEach(() => {
    setActivePinia(createPinia());
    usePermissionStore().permissionCodes = ["archive:item:read", "archive:item:create"];
    mocks.listArchiveFonds.mockResolvedValue({
        items: [
            {
                id: 1,
                fondsCode: "F001",
                fondsName: "全宗一",
                enabled: true,
                sortOrder: 0,
                createdAt: "",
                updatedAt: "",
            },
        ],
    });
    mocks.listArchiveCategories.mockResolvedValue({ items: [category()] });
    mocks.listArchiveVolumes.mockResolvedValue({ items: [volume()], next: "next-volume" });
    mocks.searchArchiveRecords.mockResolvedValue({ fields: [], items: [] });
    mocks.addArchiveItemToVolume.mockResolvedValue(undefined);
});

afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
    vi.clearAllMocks();
});

describe("ArchiveVolumesPage", () => {
    it("草稿筛选不污染翻页，提交后从第一页查询并打开卷内档案", async () => {
        renderPage();
        await waitFor(() => expect(mocks.listArchiveVolumes).toHaveBeenCalledWith({ limit: 100 }));
        expect(await screen.findByText("V-2026-001")).toBeInTheDocument();

        await chooseOption("全宗", "F001 全宗一");
        await fireEvent.click(screen.getByRole("button", { name: "下一页" }));
        await waitFor(() =>
            expect(mocks.listArchiveVolumes).toHaveBeenLastCalledWith({
                limit: 100,
                cursor: "next-volume",
            }),
        );

        await fireEvent.click(screen.getByRole("button", { name: "查询" }));
        await waitFor(() =>
            expect(mocks.listArchiveVolumes).toHaveBeenLastCalledWith({
                fondsCode: "F001",
                limit: 100,
            }),
        );
        await fireEvent.click(screen.getByRole("button", { name: "查看卷内档案" }));
        expect(await screen.findByRole("heading", { name: "V-2026-001 卷内档案" })).toBeVisible();
    });

    it("加载失败保留已有结果并以同一已提交请求重试", async () => {
        mocks.listArchiveVolumes
            .mockResolvedValueOnce({ items: [volume()], next: "next-volume" })
            .mockRejectedValueOnce(new Error("案卷服务暂不可用"))
            .mockResolvedValueOnce({ items: [volume()] });
        renderPage();
        expect(await screen.findByText("V-2026-001")).toBeInTheDocument();

        await fireEvent.click(screen.getByRole("button", { name: "下一页" }));
        expect(await screen.findByText("案卷服务暂不可用")).toBeVisible();
        expect(screen.getByText("V-2026-001")).toBeInTheDocument();
        await fireEvent.click(screen.getByRole("button", { name: "重试" }));

        await waitFor(() => expect(mocks.listArchiveVolumes).toHaveBeenCalledTimes(3));
        expect(mocks.listArchiveVolumes).toHaveBeenLastCalledWith({
            limit: 100,
            cursor: "next-volume",
        });
    });

    it("旧请求后返回时不覆盖最新筛选结果", async () => {
        const oldRequest = deferred<{ items: ReturnType<typeof volume>[] }>();
        const latestRequest = deferred<{ items: ReturnType<typeof volume>[] }>();
        mocks.listArchiveVolumes
            .mockImplementationOnce(() => oldRequest.promise)
            .mockImplementationOnce(() => latestRequest.promise);
        renderPage();
        await chooseOption("全宗", "F001 全宗一");
        await fireEvent.click(screen.getByRole("button", { name: "查询" }));

        latestRequest.resolve({ items: [{ ...volume(), archiveNo: "LATEST" }] });
        expect(await screen.findByText("LATEST")).toBeInTheDocument();
        oldRequest.resolve({ items: [{ ...volume(), archiveNo: "OLD" }] });
        await oldRequest.promise;

        await waitFor(() => expect(screen.queryByText("OLD")).not.toBeInTheDocument());
        expect(screen.getByText("LATEST")).toBeInTheDocument();
    });
});

describe("ArchiveVolumeItemsDrawer", () => {
    it("通过现有 cursor 搜索选择档案，防重复提交并在成功后刷新卷内列表", async () => {
        mocks.searchArchiveRecords
            .mockResolvedValueOnce({
                fields: [],
                items: [{ id: 71, archiveNo: "IN-VOLUME" }],
                next: "items-next",
            })
            .mockResolvedValueOnce({
                fields: [],
                items: [{ id: 91, archiveNo: "A-2026-091" }],
                next: "candidate-next",
            })
            .mockResolvedValueOnce({ fields: [], items: [{ id: 71, archiveNo: "IN-VOLUME" }] });
        const addRequest = deferred<void>();
        mocks.addArchiveItemToVolume.mockImplementationOnce(() => addRequest.promise);
        render(ArchiveVolumeItemsDrawer, {
            props: { volume: volume(), categoryId: 7 },
            global: { plugins: [ElementPlus] },
        });

        const candidates = await screen.findByRole("region", { name: "可加入档案" });
        await fireEvent.click(
            within(candidates).getByRole("radio", { name: "选择档案 A-2026-091" }),
        );
        const addButton = within(candidates).getByRole("button", { name: "加入案卷" });
        await fireEvent.click(addButton);
        await fireEvent.click(addButton);

        expect(mocks.addArchiveItemToVolume).toHaveBeenCalledTimes(1);
        expect(mocks.addArchiveItemToVolume).toHaveBeenCalledWith(12, 91, undefined);
        addRequest.resolve();
        await waitFor(() => expect(mocks.searchArchiveRecords).toHaveBeenCalledTimes(3));
        expect(mocks.searchArchiveRecords).toHaveBeenLastCalledWith({
            categoryId: 7,
            fondsCode: "F001",
            volumeId: 12,
            limit: 100,
        });
        expect(within(candidates).queryByLabelText("档案 ID")).not.toBeInTheDocument();
    });

    it("按提交关键词搜索候选档案、翻页保持条件并排除当前案卷条目", async () => {
        mocks.searchArchiveRecords
            .mockResolvedValueOnce({ fields: [], items: [] })
            .mockResolvedValueOnce({
                fields: [],
                items: [
                    { id: 90, archiveNo: "ALREADY-CAMEL", volumeId: 12 },
                    { id: 91, archiveNo: "ALREADY-SNAKE", volume_id: 12 },
                    { id: 92, archiveNo: "AVAILABLE", volumeId: null },
                ],
            })
            .mockResolvedValueOnce({
                fields: [],
                items: [{ id: 93, archiveNo: "MATCHED" }],
                next: "candidate-next",
            })
            .mockResolvedValueOnce({ fields: [], items: [{ id: 94, archiveNo: "NEXT" }] });
        render(ArchiveVolumeItemsDrawer, {
            props: { volume: volume(), categoryId: 7 },
            global: { plugins: [ElementPlus] },
        });

        const candidates = await screen.findByRole("region", { name: "可加入档案" });
        expect(
            within(candidates).queryByRole("radio", { name: "选择档案 ALREADY-CAMEL" }),
        ).not.toBeInTheDocument();
        expect(
            within(candidates).queryByRole("radio", { name: "选择档案 ALREADY-SNAKE" }),
        ).not.toBeInTheDocument();
        expect(
            within(candidates).getByRole("radio", { name: "选择档案 AVAILABLE" }),
        ).toBeInTheDocument();

        await fireEvent.update(
            within(candidates).getByRole("textbox", { name: "候选档案关键词" }),
            " 合同 ",
        );
        await fireEvent.click(within(candidates).getByRole("button", { name: "搜索候选档案" }));
        await waitFor(() =>
            expect(mocks.searchArchiveRecords).toHaveBeenLastCalledWith({
                categoryId: 7,
                fondsCode: "F001",
                keyword: "合同",
                limit: 100,
            }),
        );

        await fireEvent.click(within(candidates).getByRole("button", { name: "下一页" }));
        await waitFor(() =>
            expect(mocks.searchArchiveRecords).toHaveBeenLastCalledWith({
                categoryId: 7,
                fondsCode: "F001",
                keyword: "合同",
                limit: 100,
                cursor: "candidate-next",
            }),
        );
    });

    it("切换案卷后忽略旧加入成功且不结束新案卷的加入状态", async () => {
        const oldAddRequest = deferred<void>();
        const currentAddRequest = deferred<void>();
        const success = vi.spyOn(ElMessage, "success");
        mocks.addArchiveItemToVolume
            .mockImplementationOnce(() => oldAddRequest.promise)
            .mockImplementationOnce(() => currentAddRequest.promise);
        mockDrawerSearchSessions();
        const view = render(ArchiveVolumeItemsDrawer, {
            props: { volume: volume(), categoryId: 7 },
            global: { plugins: [ElementPlus] },
        });
        let candidates = await screen.findByRole("region", { name: "可加入档案" });
        await fireEvent.click(
            within(candidates).getByRole("radio", { name: "选择档案 OLD-CANDIDATE" }),
        );
        await fireEvent.click(within(candidates).getByRole("button", { name: "加入案卷" }));

        await view.rerender({
            volume: { ...volume(), id: 13, archiveNo: "V-2026-002" },
            categoryId: 7,
        });
        candidates = await screen.findByRole("region", { name: "可加入档案" });
        const currentRadio = await within(candidates).findByRole("radio", {
            name: "选择档案 CURRENT-CANDIDATE",
        });
        await fireEvent.click(currentRadio);
        const currentAddButton = within(candidates).getByRole("button", { name: "加入案卷" });
        expect(currentAddButton).not.toBeDisabled();
        await fireEvent.click(currentAddButton);
        await waitFor(() => expect(mocks.addArchiveItemToVolume).toHaveBeenCalledTimes(2));
        expect(currentAddButton).toBeDisabled();

        oldAddRequest.resolve();
        await oldAddRequest.promise;
        await waitFor(() => expect(success).not.toHaveBeenCalled());
        expect(currentAddButton).toBeDisabled();
        expect(mocks.searchArchiveRecords).toHaveBeenCalledTimes(4);

        currentAddRequest.resolve();
        await currentAddRequest.promise;
    });

    it("关闭后忽略旧加入失败且不提示错误", async () => {
        const addRequest = deferred<void>();
        const error = vi.spyOn(ElMessage, "error");
        mocks.addArchiveItemToVolume.mockImplementationOnce(() => addRequest.promise);
        mocks.searchArchiveRecords
            .mockResolvedValueOnce({ fields: [], items: [] })
            .mockResolvedValueOnce({
                fields: [],
                items: [{ id: 91, archiveNo: "A-2026-091" }],
            });
        const view = render(ArchiveVolumeItemsDrawer, {
            props: { volume: volume(), categoryId: 7 },
            global: { plugins: [ElementPlus] },
        });
        const candidates = await screen.findByRole("region", { name: "可加入档案" });
        await fireEvent.click(
            within(candidates).getByRole("radio", { name: "选择档案 A-2026-091" }),
        );
        await fireEvent.click(within(candidates).getByRole("button", { name: "加入案卷" }));

        await view.rerender({ volume: undefined, categoryId: undefined });
        addRequest.reject(new Error("旧案卷加入失败"));
        await addRequest.promise.catch(() => undefined);

        await waitFor(() => expect(error).not.toHaveBeenCalled());
        expect(mocks.searchArchiveRecords).toHaveBeenCalledTimes(2);
    });
});

describe("ArchiveVolumeEditorDrawer", () => {
    it("创建中阻止重复提交，取消后忽略已发出请求的结果", async () => {
        const createRequest = deferred<ReturnType<typeof volume>>();
        mocks.createArchiveVolume.mockImplementationOnce(() => createRequest.promise);
        const view = render(ArchiveVolumeEditorDrawer, {
            props: {
                state: { mode: "create" },
                categories: [category()],
                fonds: [{ fondsCode: "F001", fondsName: "全宗一" }],
            },
            global: { plugins: [ElementPlus] },
        });

        await chooseOption("创建档案分类", "ACCOUNTING 会计档案");
        await chooseOption("创建全宗", "F001 全宗一");
        const createButton = screen.getByRole("button", { name: "创建" });
        await fireEvent.click(createButton);
        await fireEvent.click(createButton);
        await waitFor(() => expect(mocks.createArchiveVolume).toHaveBeenCalledTimes(1));

        await fireEvent.click(screen.getByRole("button", { name: "取消" }));
        createRequest.resolve(volume());
        await createRequest.promise;
        await waitFor(() => expect(view.emitted("close")).toHaveLength(1));
        expect(view.emitted("created")).toBeUndefined();
    });
});

function renderPage() {
    return render(ArchiveVolumesPage, { global: { plugins: [ElementPlus] } });
}

async function chooseOption(label: string, option: string) {
    await fireEvent.click(await screen.findByRole("combobox", { name: label }));
    await fireEvent.click(await screen.findByRole("option", { name: option }));
}

function category() {
    return {
        id: 7,
        schemeId: 1,
        categoryCode: "ACCOUNTING",
        categoryName: "会计档案",
        managementMode: "VOLUME_ITEM" as const,
        tableStatus: "BUILT" as const,
        enabled: true,
        sortOrder: 0,
        createdAt: "",
        updatedAt: "",
    };
}

function volume() {
    return {
        id: 12,
        fondsCode: "F001",
        fondsName: "全宗一",
        categoryCode: "ACCOUNTING",
        categoryName: "会计档案",
        archiveNo: "V-2026-001",
        electronicStatus: "DRAFT" as const,
        archiveYear: 2026,
        lockedFlag: false,
        createdAt: "2026-07-15T10:00:00",
    };
}

function deferred<T>() {
    let resolve!: (value: T | PromiseLike<T>) => void;
    let reject!: (reason?: unknown) => void;
    const promise = new Promise<T>((resolvePromise, rejectPromise) => {
        resolve = resolvePromise;
        reject = rejectPromise;
    });
    return { promise, reject, resolve };
}

function mockDrawerSearchSessions() {
    mocks.searchArchiveRecords
        .mockResolvedValueOnce({ fields: [], items: [] })
        .mockResolvedValueOnce({
            fields: [],
            items: [{ id: 91, archiveNo: "OLD-CANDIDATE" }],
        })
        .mockResolvedValueOnce({ fields: [], items: [] })
        .mockResolvedValueOnce({
            fields: [],
            items: [{ id: 92, archiveNo: "CURRENT-CANDIDATE" }],
        })
        .mockResolvedValueOnce({ fields: [], items: [] });
}
