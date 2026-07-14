import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/vue";
import ElementPlus from "element-plus";
import type { Component } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vite-plus/test";

import ArchiveGovernancePage from "./ArchiveGovernancePage.vue";
import ArchiveGovernanceDialogs from "./ArchiveGovernanceDialogs.vue";
import ArchiveOntologyPage from "../archive-ontology/ArchiveOntologyPage.vue";
import ArchiveRuleTracesPage from "../archive-rule-traces/ArchiveRuleTracesPage.vue";
import ArchiveRulesPage from "../archive-rules/ArchiveRulesPage.vue";

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

vi.mock("@/shared/api/archive-governance", () => archiveApiMocks);
vi.mock("@/shared/api/archive-ontology", () => archiveApiMocks);
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
        renderPage(ArchiveGovernancePage);
        expect(await screen.findByRole("button", { name: "新建方案" })).toBeInTheDocument();
        expect(await screen.findByText("默认治理方案")).toBeInTheDocument();
        expect((await screen.findAllByText("v1")).length).toBeGreaterThan(0);
    });

    it("展示治理版本工作台的适用范围、装配绑定和默认解析入口", async () => {
        renderPage(ArchiveGovernancePage);
        expect(await screen.findByText("版本工作台")).toBeInTheDocument();
        expect(await screen.findByDisplayValue("F001")).toBeInTheDocument();
        expect(await screen.findByDisplayValue("retention_rules")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "解析默认版本" })).toBeInTheDocument();
    });

    it("治理方案弹层通过语义事件提交有效表单", async () => {
        const onSubmitScheme = vi.fn();
        render(ArchiveGovernanceDialogs, {
            props: {
                schemeOpen: true,
                versionOpen: false,
                schemeForm: {
                    schemeCode: "default_governance",
                    schemeName: "默认治理方案",
                    description: "",
                    enabled: true,
                    sortOrder: 0,
                },
                versionForm: { versionCode: "", versionDescription: "" },
                submitting: false,
                onSubmitScheme,
            },
            global: { plugins: [ElementPlus] },
        });

        await fireEvent.click(await screen.findByRole("button", { name: "确定" }));

        await waitFor(() => expect(onSubmitScheme).toHaveBeenCalledOnce());
    });

    it("渲染本体页面的对象、属性和映射入口", async () => {
        renderPage(ArchiveOntologyPage);
        expect(await screen.findByRole("button", { name: "新建对象" })).toBeInTheDocument();
        expect(await screen.findByText("档案条目")).toBeInTheDocument();
        await fireEvent.click(screen.getByRole("tab", { name: "属性类型" }));
        expect(await screen.findByText("document_no")).toBeInTheDocument();
        await fireEvent.click(screen.getByRole("tab", { name: "属性映射" }));
        expect(await screen.findByText("archive_no")).toBeInTheDocument();
    });

    it("渲染本地规则页面的列表和试算操作", async () => {
        renderPage(ArchiveRulesPage);
        expect(await screen.findByRole("button", { name: "新建规则" })).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "规则试算" })).toBeInTheDocument();
    });

    it("渲染规则追踪页面的筛选和查询操作", async () => {
        renderPage(ArchiveRuleTracesPage);
        expect(await screen.findByRole("button", { name: "查询" })).toBeInTheDocument();
        expect(screen.getByLabelText("对象 ID")).toBeInTheDocument();
    });
});

function renderPage(component: Component) {
    return render(component, { global: { plugins: [ElementPlus] } });
}
