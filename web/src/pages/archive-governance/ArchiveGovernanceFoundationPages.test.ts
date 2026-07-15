import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/vue";
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

    it("规则追踪使用已提交条件翻页并在修改页大小时清除游标", async () => {
        archiveApiMocks.searchArchiveRuleTraces.mockResolvedValue({
            items: [],
            next: "next-token",
        });
        renderPage(ArchiveRuleTracesPage);

        await fireEvent.update(triggerCodeInput(), " BEFORE_SAVE ");
        await fireEvent.click(screen.getByRole("button", { name: "查询" }));
        await waitFor(() =>
            expect(archiveApiMocks.searchArchiveRuleTraces).toHaveBeenLastCalledWith({
                schemeVersionId: undefined,
                triggerCode: "BEFORE_SAVE",
                objectTypeCode: undefined,
                objectId: undefined,
                ruleType: undefined,
                limit: 100,
                cursor: undefined,
            }),
        );

        await fireEvent.update(triggerCodeInput(), "AFTER_SAVE");
        await fireEvent.click(screen.getByRole("button", { name: "下一页" }));
        await waitFor(() =>
            expect(archiveApiMocks.searchArchiveRuleTraces).toHaveBeenLastCalledWith({
                schemeVersionId: undefined,
                triggerCode: "BEFORE_SAVE",
                objectTypeCode: undefined,
                objectId: undefined,
                ruleType: undefined,
                limit: 100,
                cursor: "next-token",
            }),
        );

        await fireEvent.click(screen.getByRole("combobox", { name: "每页条数" }));
        await fireEvent.click(await screen.findByRole("option", { name: "200 条" }));
        await waitFor(() =>
            expect(archiveApiMocks.searchArchiveRuleTraces).toHaveBeenLastCalledWith({
                schemeVersionId: undefined,
                triggerCode: "BEFORE_SAVE",
                objectTypeCode: undefined,
                objectId: undefined,
                ruleType: undefined,
                limit: 200,
                cursor: undefined,
            }),
        );
    });

    it("规则追踪加载失败时保留上次结果并复用已提交条件重试", async () => {
        archiveApiMocks.searchArchiveRuleTraces
            .mockResolvedValueOnce({
                items: [
                    {
                        id: 1,
                        schemeVersionId: 11,
                        triggerCode: "BEFORE_SAVE",
                        objectTypeCode: "ARCHIVE_ITEM",
                        matchedFlag: true,
                        blockingFlag: false,
                        effectJson: {},
                        message: "已命中",
                        createdAt: "2026-07-15T10:00:00Z",
                    },
                ],
                next: "next-token",
            })
            .mockRejectedValueOnce(new Error("追踪查询失败"))
            .mockResolvedValueOnce({ items: [] });
        renderPage(ArchiveRuleTracesPage);

        await fireEvent.update(triggerCodeInput(), "BEFORE_SAVE");
        await fireEvent.click(screen.getByRole("button", { name: "查询" }));
        expect(await screen.findByText("已命中")).toBeInTheDocument();

        await fireEvent.update(triggerCodeInput(), "AFTER_SAVE");
        await fireEvent.click(screen.getByRole("button", { name: "下一页" }));
        expect((await screen.findAllByRole("alert"))[0]).toHaveTextContent("追踪查询失败");
        expect(screen.getByText("已命中")).toBeInTheDocument();

        await fireEvent.click(screen.getByRole("button", { name: "重试" }));
        await waitFor(() =>
            expect(archiveApiMocks.searchArchiveRuleTraces).toHaveBeenCalledTimes(3),
        );
        expect(archiveApiMocks.searchArchiveRuleTraces).toHaveBeenLastCalledWith({
            schemeVersionId: undefined,
            triggerCode: "BEFORE_SAVE",
            objectTypeCode: undefined,
            objectId: undefined,
            ruleType: undefined,
            limit: 100,
            cursor: undefined,
        });
    });
});

function renderPage(component: Component) {
    return render(component, { global: { plugins: [ElementPlus] } });
}

function triggerCodeInput() {
    return (
        screen.queryByRole("textbox", { name: "触发点" }) ??
        within(screen.getByRole("group", { name: "触发点" })).getByRole("textbox")
    );
}
