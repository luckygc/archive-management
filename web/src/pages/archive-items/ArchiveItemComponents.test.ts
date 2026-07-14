import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { afterEach, describe, expect, it, vi } from "vitest";

import DynamicArchiveFields from "@/pages/archive-library/DynamicArchiveFields.vue";
import type { ArchiveFieldDto } from "@/shared/types/archive-metadata";
import ArchiveItemActions from "./ArchiveItemActions.vue";
import ArchiveItemEditorDrawer from "./ArchiveItemEditorDrawer.vue";

afterEach(cleanup);

describe("档案管理拆分组件", () => {
    it("选择导入文件后向页面发出文件对象", async () => {
        const onImportFile = vi.fn();
        const { container } = render(ArchiveItemActions, {
            props: {
                categorySelected: true,
                canImport: true,
                canExport: true,
                canCreate: true,
                downloadingTemplate: false,
                importing: false,
                exporting: false,
                onImportFile,
            },
            global: { plugins: [ElementPlus], stubs: { TransitionGroup: false } },
        });
        const input = container.querySelector<HTMLInputElement>('input[type="file"]')!;
        const file = new File(["archive"], "archives.xlsx", {
            type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        });

        await fireEvent.change(input, { target: { files: [file] } });

        expect(onImportFile).toHaveBeenCalledWith(file);
        expect(input.value).toBe("");
    });

    it("编辑抽屉仅在必填项有效时发出保存事件", async () => {
        const invalidSave = vi.fn();
        const invalid = renderEditor("", invalidSave);
        await fireEvent.click(await screen.findByRole("button", { name: "保存" }));
        await waitFor(() => expect(invalidSave).not.toHaveBeenCalled());
        invalid.unmount();

        const validSave = vi.fn();
        renderEditor("F001", validSave);
        await fireEvent.click(await screen.findByRole("button", { name: "保存" }));
        await waitFor(() => expect(validSave).toHaveBeenCalledOnce());
    });

    it("详情模式以禁用控件展示固定参考、实物字段和动态字段", async () => {
        render(ArchiveItemEditorDrawer, {
            props: {
                state: { mode: "detail", archiveItemId: 9 },
                detail: detail(),
                form: editorForm(),
                fields: [dynamicField],
                physicalFields: [physicalField],
                categories: [category],
                fonds: [{ fondsCode: "F001", fondsName: "默认全宗" }],
                securityLevels: [
                    {
                        id: 2,
                        levelName: "秘密",
                        enabled: true,
                        sortOrder: 1,
                        createdAt: "",
                        updatedAt: "",
                    },
                ],
                retentionPeriods: [
                    {
                        id: 3,
                        periodName: "长期",
                        enabled: true,
                        sortOrder: 1,
                        createdAt: "",
                        updatedAt: "",
                    },
                ],
                fieldErrors: {},
                loading: false,
                saving: false,
            },
            global: { plugins: [ElementPlus] },
        });

        expect(await screen.findByLabelText("密级")).toBeDisabled();
        expect(screen.getByLabelText("保管期限")).toBeDisabled();
        expect(screen.getByLabelText("盒号")).toBeDisabled();
        expect(screen.getByLabelText("题名")).toBeDisabled();
        expect(screen.getByLabelText("盒号")).toHaveValue("BOX-001");
        expect(screen.getByLabelText("题名")).toHaveValue("建设工程档案");
        expect(screen.queryByRole("button", { name: "保存" })).not.toBeInTheDocument();
    });

    it("动态字段按详情布局展示并将结构化错误放到对应控件", async () => {
        const detailOnlyField = createField({
            id: 4,
            fieldCode: "detail_note",
            fieldName: "详情备注",
            editVisible: false,
            detailVisible: true,
        });
        render(DynamicArchiveFields, {
            props: {
                modelValue: { detail_note: "仅详情展示" },
                fields: [detailOnlyField],
                disabled: true,
                surface: "detail",
                fieldErrors: { detail_note: "详情备注格式不合法" },
            },
            global: { plugins: [ElementPlus], stubs: { TransitionGroup: false } },
        });

        expect(await screen.findByRole("textbox")).toBeDisabled();
        expect(await screen.findByText("详情备注格式不合法")).toBeInTheDocument();
    });

    it("详情表面使用详情列宽而不是编辑列宽", async () => {
        const view = render(DynamicArchiveFields, {
            props: {
                modelValue: { detail_note: "详情" },
                fields: [
                    createField({
                        fieldCode: "detail_note",
                        fieldName: "详情备注",
                        detailColSpan: 3,
                        editColSpan: 1,
                    }),
                ],
                disabled: true,
                surface: "detail",
            },
            global: { plugins: [ElementPlus], stubs: { TransitionGroup: false } },
        });

        expect(view.container.querySelector(".el-col")).toHaveClass("el-col-24");
        expect(view.container.querySelector(".el-col")).not.toHaveClass("el-col-8");
    });

    it("当前参考项停用时保留 ID 提示而不伪造选项名称", async () => {
        render(ArchiveItemEditorDrawer, {
            props: {
                state: { mode: "detail", archiveItemId: 9 },
                detail: detail(),
                form: editorForm(),
                fields: [],
                physicalFields: [],
                categories: [category],
                fonds: [{ fondsCode: "F001", fondsName: "默认全宗" }],
                securityLevels: [],
                retentionPeriods: [],
                fieldErrors: {},
                loading: false,
                saving: false,
            },
            global: { plugins: [ElementPlus] },
        });

        expect(await screen.findByText("当前密级 ID：2（已停用或不可用）")).toBeInTheDocument();
        expect(screen.getByText("当前保管期限 ID：3（已停用或不可用）")).toBeInTheDocument();
    });
});

function renderEditor(fondsCode: string, onSave: () => void) {
    return render(ArchiveItemEditorDrawer, {
        props: {
            state: { mode: "create" },
            form: {
                ...editorForm(),
                fondsCode,
            },
            fields: [],
            physicalFields: [],
            categories: [category],
            fonds: [{ fondsCode: "F001", fondsName: "默认全宗" }],
            securityLevels: [],
            retentionPeriods: [],
            fieldErrors: {},
            loading: false,
            saving: false,
            onSave,
        },
        global: { plugins: [ElementPlus] },
    });
}

const category = {
    id: 1,
    schemeId: 1,
    categoryCode: "contract",
    categoryName: "合同档案",
    managementMode: "ITEM_ONLY" as const,
    enabled: true,
    sortOrder: 0,
    tableStatus: "NOT_BUILT" as const,
    createdAt: "",
    updatedAt: "",
};
const physicalField = createField({
    id: 1,
    fieldScope: "PHYSICAL",
    fieldCode: "box_no",
    fieldName: "盒号",
});
const dynamicField = createField({ id: 2, fieldCode: "title", fieldName: "题名" });

function editorForm() {
    return {
        categoryId: 1,
        fondsCode: "F001",
        archiveNo: "A-001",
        archiveYear: 2026,
        electronicStatus: "DRAFT" as const,
        securityLevelId: 2,
        retentionPeriodId: 3,
        physicalFields: { box_no: "BOX-001" },
        dynamicFields: { title: "建设工程档案" },
    };
}

function detail() {
    return {
        item: {
            id: 9,
            fondsCode: "F001",
            fondsName: "默认全宗",
            categoryCode: "contract",
            categoryName: "合同档案",
            archiveNo: "A-001",
            electronicStatus: "DRAFT" as const,
            securityLevelId: 2,
            retentionPeriodId: 3,
            archiveYear: 2026,
            lockedFlag: false,
        },
        category,
        fields: [dynamicField],
        dynamicFields: { title: "建设工程档案" },
        physicalFields: [physicalField],
        physicalFieldValues: { box_no: "BOX-001" },
    };
}

function createField(field: Partial<ArchiveFieldDto>): ArchiveFieldDto {
    return {
        archiveLevel: "ITEM",
        categoryId: 1,
        columnName: `f_${field.fieldCode ?? "field"}`,
        createdAt: "",
        detailColSpan: 1,
        detailSortOrder: field.id ?? 1,
        detailVisible: true,
        editColSpan: 1,
        editControl: "INPUT",
        editSortOrder: field.id ?? 1,
        editVisible: true,
        enabled: true,
        exactSearchable: false,
        dataScopeFilterable: false,
        fieldScope: "METADATA",
        fieldCode: "field",
        fieldName: "字段",
        fieldType: "TEXT",
        id: 1,
        listSortOrder: field.id ?? 1,
        listVisible: true,
        sortOrder: field.id ?? 1,
        textLength: 200,
        updatedAt: "",
        ...field,
    };
}
