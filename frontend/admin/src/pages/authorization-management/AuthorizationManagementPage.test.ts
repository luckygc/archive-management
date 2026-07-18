import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { usePermissionStore } from "@/stores/permissionStore";
import AuthorizationManagementPage from "./AuthorizationManagementPage.vue";

const archiveApiMocks = vi.hoisted(() => ({
    getAuthenticationUser: vi.fn(),
    getCurrentUserPermissions: vi.fn(),
    getDepartmentArchiveDataScopes: vi.fn(),
    getRoleArchiveDataScopes: vi.fn(),
    getRolePermissions: vi.fn(),
    getUserArchiveDataScopes: vi.fn(),
    listArchiveDataScopes: vi.fn(),
    listAuthenticationUserOptions: vi.fn(),
    listAuthorizationPermissions: vi.fn(),
    listAuthorizationRoles: vi.fn(),
    listOrganizationDepartments: vi.fn(),
    saveDepartmentArchiveDataScopes: vi.fn(),
    saveRoleArchiveDataScopes: vi.fn(),
    saveRolePermissions: vi.fn(),
    saveUserArchiveDataScopes: vi.fn(),
}));

vi.mock("@/shared/api/authentication", () => archiveApiMocks);
vi.mock("@/shared/api/authorization", () => archiveApiMocks);
vi.mock("@/shared/api/organization", () => archiveApiMocks);

beforeEach(() => {
    archiveApiMocks.listAuthorizationRoles.mockResolvedValue({
        items: [{ id: 2, roleName: "档案管理员", enabled: true, createdAt: "2026-07-03" }],
    });
    archiveApiMocks.listAuthenticationUserOptions.mockResolvedValue({ items: [] });
    archiveApiMocks.listOrganizationDepartments.mockResolvedValue({
        items: [
            {
                id: 3,
                departmentCode: "D003",
                departmentName: "综合部",
                enabled: true,
                sortOrder: 0,
                createdAt: "2026-07-03",
                updatedAt: "2026-07-03",
            },
        ],
    });
    archiveApiMocks.getRolePermissions.mockResolvedValue({
        roleId: 2,
        permissionCodes: ["archive:item:read"],
    });
    archiveApiMocks.getRoleArchiveDataScopes.mockResolvedValue({ roleId: 2, scopeIds: [1] });
    archiveApiMocks.getDepartmentArchiveDataScopes.mockResolvedValue({
        departmentId: 3,
        scopeIds: [1],
    });
    archiveApiMocks.listAuthorizationPermissions.mockResolvedValue({
        items: [permission("archive:item:read", "读取档案")],
    });
    archiveApiMocks.listArchiveDataScopes.mockResolvedValue({
        items: [
            {
                id: 1,
                scopeCode: "*",
                scopeName: "全部档案",
                scopeType: "ALL",
                dimensions: [],
                enabled: true,
            },
        ],
    });
});

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

describe("AuthorizationManagementPage", () => {
    it("选择角色后加载并展示权限和数据范围", async () => {
        await renderPage(["authorization:permission:manage", "archive:data-scope:manage"]);
        await selectCurrentSubject("档案管理员");

        await waitFor(() => {
            expect(archiveApiMocks.getRolePermissions).toHaveBeenCalledWith(2);
            expect(archiveApiMocks.getRoleArchiveDataScopes).toHaveBeenCalledWith(2);
        });
        expect((await screen.findAllByText("读取档案")).length).toBeGreaterThan(0);
        expect((await screen.findAllByText("全部档案")).length).toBeGreaterThan(0);
    });

    it("仅功能权限管理员只加载和呈现角色功能权限", async () => {
        await renderPage(["authorization:permission:manage"]);
        await selectCurrentSubject("档案管理员");

        await waitFor(() => expect(archiveApiMocks.getRolePermissions).toHaveBeenCalledWith(2));
        expect(archiveApiMocks.listAuthorizationPermissions).toHaveBeenCalledTimes(1);
        expect(archiveApiMocks.listAuthenticationUserOptions).not.toHaveBeenCalled();
        expect(archiveApiMocks.listOrganizationDepartments).not.toHaveBeenCalled();
        expect(archiveApiMocks.listArchiveDataScopes).not.toHaveBeenCalled();
        expect(archiveApiMocks.getRoleArchiveDataScopes).not.toHaveBeenCalled();
        expect(screen.getByText("功能权限")).toBeInTheDocument();
        expect(screen.queryByText("数据范围")).not.toBeInTheDocument();
    });

    it("仅数据范围管理员只加载和呈现主体范围功能", async () => {
        archiveApiMocks.listAuthenticationUserOptions.mockResolvedValue({
            items: [
                {
                    id: 5,
                    username: "zhangsan",
                    displayName: "张三",
                },
            ],
        });
        archiveApiMocks.getUserArchiveDataScopes.mockResolvedValue({ userId: 5, scopeIds: [] });

        await renderPage(["archive:data-scope:manage"]);
        await fireEvent.click(await screen.findByText("部门"));
        await selectCurrentSubject("D003 综合部");

        await waitFor(() =>
            expect(archiveApiMocks.getDepartmentArchiveDataScopes).toHaveBeenCalledWith(3),
        );
        expect(archiveApiMocks.listAuthorizationRoles).toHaveBeenCalledTimes(1);
        expect(archiveApiMocks.listAuthenticationUserOptions).toHaveBeenCalledTimes(1);
        expect(archiveApiMocks.listOrganizationDepartments).toHaveBeenCalledTimes(1);
        expect(archiveApiMocks.listArchiveDataScopes).toHaveBeenCalledTimes(1);
        expect(archiveApiMocks.listAuthorizationPermissions).not.toHaveBeenCalled();
        expect(archiveApiMocks.getRolePermissions).not.toHaveBeenCalled();
        expect(screen.queryByText("功能权限")).not.toBeInTheDocument();
        expect(screen.getByText("数据范围")).toBeInTheDocument();
    });

    it("单能力变为双能力时等待当前批次后只补载新增能力目录", async () => {
        const roleCatalog = deferred<{
            items: Array<{ id: number; roleName: string; enabled: boolean; createdAt: string }>;
        }>();
        archiveApiMocks.listAuthorizationRoles.mockReturnValueOnce(roleCatalog.promise);
        const permissionStore = await renderPage(["authorization:permission:manage"]);

        await setPermissions(permissionStore, [
            "authorization:permission:manage",
            "archive:data-scope:manage",
        ]);
        await Promise.resolve();

        expect(archiveApiMocks.listAuthorizationRoles).toHaveBeenCalledTimes(1);
        expect(archiveApiMocks.listAuthenticationUserOptions).not.toHaveBeenCalled();

        roleCatalog.resolve({
            items: [{ id: 2, roleName: "档案管理员", enabled: true, createdAt: "2026-07-03" }],
        });

        await waitFor(() =>
            expect(archiveApiMocks.listAuthenticationUserOptions).toHaveBeenCalledTimes(1),
        );
        expect(archiveApiMocks.listOrganizationDepartments).toHaveBeenCalledTimes(1);
        expect(archiveApiMocks.listArchiveDataScopes).toHaveBeenCalledTimes(1);
        expect(archiveApiMocks.listAuthorizationRoles).toHaveBeenCalledTimes(1);
        expect(archiveApiMocks.listAuthorizationPermissions).toHaveBeenCalledTimes(1);
    });

    it("撤销数据范围能力时收敛主体、选择和编辑弹窗且旧详情不回填", async () => {
        const userScopes = deferred<{ userId: number; scopeIds: number[] }>();
        archiveApiMocks.listAuthenticationUserOptions.mockResolvedValue({
            items: [{ id: 5, username: "zhangsan", displayName: "张三" }],
        });
        archiveApiMocks.getUserArchiveDataScopes.mockReturnValue(userScopes.promise);
        const permissionStore = await renderPage([
            "authorization:permission:manage",
            "archive:data-scope:manage",
        ]);
        await fireEvent.click(await screen.findByText("用户"));
        await selectCurrentSubject("张三（zhangsan）");
        await fireEvent.click(screen.getByRole("button", { name: "编辑" }));
        expect(screen.getByRole("dialog", { name: "编辑数据范围" })).toBeInTheDocument();

        await setPermissions(permissionStore, ["authorization:permission:manage"]);

        await waitFor(() => expect(screen.queryByText("用户")).not.toBeInTheDocument());
        expect(screen.queryByRole("dialog", { name: "编辑数据范围" })).not.toBeInTheDocument();
        expect(screen.queryByText("数据范围")).not.toBeInTheDocument();
        expect(await screen.findByText("搜索并选择角色")).toBeInTheDocument();

        await setPermissions(permissionStore, [
            "authorization:permission:manage",
            "archive:data-scope:manage",
        ]);
        userScopes.resolve({ userId: 5, scopeIds: [1] });

        await waitFor(() => expect(screen.getByText("用户")).toBeInTheDocument());
        expect(screen.getByText("搜索并选择角色")).toBeInTheDocument();
        expect(screen.queryByText("全部档案")).not.toBeInTheDocument();
    });

    it("能力撤销后丢弃旧目录响应，重新授予时发起新请求", async () => {
        const oldUsers = deferred<{
            items: Array<{ id: number; username: string; displayName: string }>;
        }>();
        archiveApiMocks.listAuthenticationUserOptions
            .mockReturnValueOnce(oldUsers.promise)
            .mockResolvedValueOnce({
                items: [{ id: 6, username: "lisi", displayName: "李四" }],
            });
        const permissionStore = await renderPage(["archive:data-scope:manage"]);

        await setPermissions(permissionStore, ["authorization:permission:manage"]);
        oldUsers.resolve({ items: [{ id: 5, username: "zhangsan", displayName: "张三" }] });
        await setPermissions(permissionStore, [
            "authorization:permission:manage",
            "archive:data-scope:manage",
        ]);

        await waitFor(() =>
            expect(archiveApiMocks.listAuthenticationUserOptions).toHaveBeenCalledTimes(2),
        );
        await fireEvent.click(await screen.findByText("用户"));
        const selector = await screen.findByText("搜索并选择用户");
        await fireEvent.click(selector);
        expect(await screen.findByText("李四（lisi）")).toBeInTheDocument();
        expect(screen.queryByText("张三（zhangsan）")).not.toBeInTheDocument();
    });
});

async function renderPage(permissionCodes: string[]) {
    const pinia = createPinia();
    setActivePinia(pinia);
    const permissionStore = usePermissionStore();
    await setPermissions(permissionStore, permissionCodes);
    render(AuthorizationManagementPage, {
        global: { plugins: [ElementPlus, pinia] },
    });
    return permissionStore;
}

async function setPermissions(
    store: ReturnType<typeof usePermissionStore>,
    permissionCodes: string[],
) {
    archiveApiMocks.getCurrentUserPermissions.mockResolvedValueOnce({
        permissionCodes,
        superAdmin: false,
    });
    await store.fetchSummary();
}

function deferred<T>() {
    let resolve!: (value: T) => void;
    const promise = new Promise<T>((resolvePromise) => {
        resolve = resolvePromise;
    });
    return { promise, resolve };
}

async function selectCurrentSubject(option: string) {
    const selector = await screen.findByRole("combobox");
    await fireEvent.click(selector);
    await fireEvent.click(await screen.findByText(option));
}

function permission(permissionCode: string, permissionName: string) {
    return {
        permissionCode,
        permissionName,
        moduleCode: "archive",
        description: permissionName,
    };
}
