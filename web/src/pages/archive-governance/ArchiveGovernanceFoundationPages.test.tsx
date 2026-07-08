import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vite-plus/test";

import { ArchiveGovernancePage } from "./ArchiveGovernancePage";
import { ArchiveOntologyPage } from "../archive-ontology/ArchiveOntologyPage";
import { ArchiveRuleTracesPage } from "../archive-rule-traces/ArchiveRuleTracesPage";
import { ArchiveRulesPage } from "../archive-rules/ArchiveRulesPage";

const archiveApiMocks = vi.hoisted(() => ({
    listArchiveGovernanceSchemeVersions: vi.fn(),
    listArchiveGovernanceBindings: vi.fn(),
    listArchiveGovernanceSchemes: vi.fn(),
    listArchiveGovernanceScopes: vi.fn(),
    listArchiveOntologyAttributeMappings: vi.fn(),
    listArchiveOntologyAttributeTypes: vi.fn(),
    listArchiveOntologyEventTypes: vi.fn(),
    listArchiveOntologyObjectTypes: vi.fn(),
    listArchiveOntologyRelationTypes: vi.fn(),
    listArchiveRules: vi.fn(),
    resolveDefaultArchiveGovernanceVersion: vi.fn(),
    searchArchiveRuleTraces: vi.fn(),
}));

vi.mock("@/shared/api/archive", async (importOriginal) => {
    const actual = await importOriginal<typeof import("@/shared/api/archive")>();
    return {
        ...actual,
        ...archiveApiMocks,
    };
});

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
    archiveApiMocks.listArchiveGovernanceScopes.mockResolvedValue({
        items: [
            {
                id: 51,
                schemeVersionId: 11,
                scopeType: "FONDS",
                fondsCode: "F001",
                defaultFlag: true,
            },
        ],
    });
    archiveApiMocks.listArchiveGovernanceBindings.mockResolvedValue({
        items: [
            {
                id: 61,
                schemeVersionId: 11,
                bindingType: "RULE_SET",
                targetType: "RULE",
                targetId: 71,
                targetCode: "retention_rules",
                bindingOrder: 1,
            },
        ],
    });
    archiveApiMocks.resolveDefaultArchiveGovernanceVersion.mockResolvedValue({
        id: 11,
        schemeId: 1,
        versionCode: "v1",
        versionDescription: "默认版本",
        status: "PUBLISHED",
    });
    archiveApiMocks.listArchiveOntologyObjectTypes.mockResolvedValue({
        items: [
            {
                id: 21,
                typeCode: "ARCHIVE_ITEM",
                typeName: "档案条目",
                builtin: true,
                enabled: true,
            },
        ],
    });
    archiveApiMocks.listArchiveOntologyAttributeTypes.mockResolvedValue({
        items: [
            {
                id: 31,
                attributeCode: "document_no",
                attributeName: "文号",
                objectTypeId: 21,
                dataType: "TEXT",
                metadataDomain: "DESCRIPTION",
                cardinality: "SINGLE",
                exactSearchable: true,
                sortable: false,
                descriptionParticipating: true,
                referenceCodeParticipating: true,
                ruleFactVisible: true,
                enabled: true,
            },
        ],
    });
    archiveApiMocks.listArchiveOntologyAttributeMappings.mockResolvedValue({
        items: [
            {
                id: 41,
                attributeTypeId: 31,
                mappingKind: "FIXED_FIELD",
                fixedFieldCode: "archive_no",
            },
        ],
    });
    archiveApiMocks.listArchiveOntologyRelationTypes.mockResolvedValue({ items: [] });
    archiveApiMocks.listArchiveOntologyEventTypes.mockResolvedValue({ items: [] });
    archiveApiMocks.listArchiveRules.mockResolvedValue({ items: [] });
    archiveApiMocks.searchArchiveRuleTraces.mockResolvedValue({ items: [] });
});

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

describe("档案治理基础页面", () => {
    it("渲染治理方案页面的方案和版本操作", async () => {
        renderWithQuery(<ArchiveGovernancePage />);

        expect(
            await screen.findByRole("button", { name: "新建方案" }, { timeout: 5_000 }),
        ).toBeInTheDocument();
        expect(
            await screen.findByText("默认治理方案", undefined, { timeout: 5_000 }),
        ).toBeInTheDocument();
        expect(await screen.findAllByText("v1", undefined, { timeout: 5_000 })).not.toHaveLength(0);
    }, 10_000);

    it("展示治理版本工作台的适用范围、装配绑定和默认解析入口", async () => {
        renderWithQuery(<ArchiveGovernancePage />);

        expect(
            await screen.findByText("版本工作台", undefined, { timeout: 5_000 }),
        ).toBeInTheDocument();
        expect(
            await screen.findByDisplayValue("F001", undefined, { timeout: 5_000 }),
        ).toBeInTheDocument();
        expect(
            await screen.findByDisplayValue("retention_rules", undefined, { timeout: 5_000 }),
        ).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "解析默认版本" })).toBeInTheDocument();
    }, 10_000);

    it("渲染本体页面的对象、属性和映射入口", async () => {
        renderWithQuery(<ArchiveOntologyPage />);

        expect(await screen.findByRole("button", { name: "新建对象" })).toBeInTheDocument();
        expect(await screen.findByText("档案条目")).toBeInTheDocument();
        fireEvent.click(screen.getByRole("tab", { name: "属性类型" }));
        expect(await screen.findByText("document_no")).toBeInTheDocument();
        fireEvent.click(screen.getByRole("tab", { name: "属性映射" }));
        expect(await screen.findByText("archive_no")).toBeInTheDocument();
    });

    it("渲染本地规则页面的列表和试算操作", async () => {
        renderWithQuery(<ArchiveRulesPage />);

        expect(await screen.findByRole("button", { name: "新建规则" })).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "规则试算" })).toBeInTheDocument();
    });

    it("渲染规则追踪页面的筛选和查询操作", async () => {
        renderWithQuery(<ArchiveRuleTracesPage />);

        expect(await screen.findByRole("button", { name: /查\s*询/ })).toBeInTheDocument();
        expect(screen.getByLabelText("对象 ID")).toBeInTheDocument();
    });
});

function renderWithQuery(children: ReactNode) {
    const queryClient = new QueryClient({
        defaultOptions: {
            queries: { retry: false },
            mutations: { retry: false },
        },
    });
    return render(<QueryClientProvider client={queryClient}>{children}</QueryClientProvider>);
}
