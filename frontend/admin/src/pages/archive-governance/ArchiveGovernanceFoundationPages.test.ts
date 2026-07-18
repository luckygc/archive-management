import { cleanup, render, screen } from "@testing-library/vue";
import ElementPlus from "element-plus";
import type { Component } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vite-plus/test";

import ArchiveGovernancePage from "./ArchiveGovernancePage.vue";
import ArchiveRuleTracesPage from "../archive-rule-traces/ArchiveRuleTracesPage.vue";
import ArchiveRulesPage from "../archive-rules/ArchiveRulesPage.vue";

const archiveApiMocks = vi.hoisted(() => ({
    getArchiveRuntimeFields: vi.fn(),
    listArchiveGovernanceSchemeVersions: vi.fn(),
    listArchiveGovernanceBindings: vi.fn(),
    listArchiveGovernanceSchemes: vi.fn(),
    listArchiveGovernanceScopes: vi.fn(),
    listArchiveRuntimeDefinitions: vi.fn(),
    resolveDefaultArchiveGovernanceVersion: vi.fn(),
    searchArchiveRuntimeTraces: vi.fn(),
}));

vi.mock("vue-router", async (importOriginal) => ({
    ...(await importOriginal<typeof import("vue-router")>()),
    useRoute: () => ({ query: {} }),
}));
vi.mock("@/shared/api/archive-governance", () => archiveApiMocks);
vi.mock("@/shared/api/archive-rules", () => archiveApiMocks);

beforeEach(() => {
    archiveApiMocks.listArchiveGovernanceSchemes.mockResolvedValue({
        items: [
            {
                id: 1,
                schemeCode: "default_governance",
                schemeName: "默认治理方案",
                enabled: true,
                sortOrder: 0,
            },
        ],
    });
    archiveApiMocks.listArchiveGovernanceSchemeVersions.mockResolvedValue({
        items: [
            {
                id: 11,
                schemeId: 1,
                versionCode: "v1",
                versionDescription: "默认版本",
                status: "DRAFT",
            },
        ],
    });
    archiveApiMocks.listArchiveGovernanceScopes.mockResolvedValue({ items: [] });
    archiveApiMocks.listArchiveGovernanceBindings.mockResolvedValue({ items: [] });
    archiveApiMocks.listArchiveRuntimeDefinitions.mockResolvedValue({ items: [] });
    archiveApiMocks.searchArchiveRuntimeTraces.mockResolvedValue({ items: [] });
});

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

describe("档案治理基础页面", () => {
    it("治理版本工作台直接展示运行时配置摘要与入口", async () => {
        renderPage(ArchiveGovernancePage);

        expect(await screen.findByText("默认治理方案")).toBeInTheDocument();
        expect(await screen.findByText("运行时配置")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "打开运行时规则工作区" })).toBeInTheDocument();
    });

    it("运行时规则页面提供定义、试运行和迁移恢复入口", async () => {
        renderPage(ArchiveRulesPage);

        expect(
            await screen.findByRole("heading", { name: "运行时约束与规则" }),
        ).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "新建定义" })).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "试运行" })).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "迁移与恢复" })).toBeInTheDocument();
    });

    it("决策追踪页面使用固定触发点和定义类型筛选", async () => {
        renderPage(ArchiveRuleTracesPage);

        expect(await screen.findByRole("button", { name: "查询" })).toBeInTheDocument();
        expect(screen.getByText("定义类型")).toBeInTheDocument();
        expect(screen.getByText("触发点")).toBeInTheDocument();
    });
});

function renderPage(component: Component) {
    return render(component, {
        global: {
            plugins: [ElementPlus],
            mocks: { $router: { push: vi.fn() } },
        },
    });
}
