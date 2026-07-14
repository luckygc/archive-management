import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { defineComponent } from "vue";
import { afterEach, describe, expect, it, vi } from "vitest";

import ArchiveItemActions from "./ArchiveItemActions.vue";
import ArchiveItemEditorDrawer from "./ArchiveItemEditorDrawer.vue";

vi.mock("@/pages/archive-library/DynamicArchiveFields.vue", () => ({
    default: defineComponent({ template: `<div />` }),
}));

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
            global: { plugins: [ElementPlus] },
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
});

function renderEditor(fondsCode: string, onSave: () => void) {
    return render(ArchiveItemEditorDrawer, {
        props: {
            state: { mode: "create" },
            form: {
                categoryId: 1,
                fondsCode,
                archiveNo: "",
                archiveYear: 2026,
                electronicStatus: "DRAFT",
                dynamicFields: {},
            },
            fields: [],
            categories: [
                {
                    id: 1,
                    schemeId: 1,
                    categoryCode: "contract",
                    categoryName: "合同档案",
                    managementMode: "ITEM_ONLY",
                    enabled: true,
                    sortOrder: 0,
                    tableStatus: "NOT_BUILT",
                    createdAt: "",
                    updatedAt: "",
                },
            ],
            fonds: [{ fondsCode: "F001", fondsName: "默认全宗" }],
            loading: false,
            saving: false,
            onSave,
        },
        global: { plugins: [ElementPlus] },
    });
}
