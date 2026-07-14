import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { defineComponent, ref } from "vue";
import { afterEach, describe, expect, it } from "vitest";
import type { ArchiveFieldDto } from "@/shared/types/archive-metadata";
import DynamicArchiveFields, { normalizeArchiveRecordFormValues } from "./DynamicArchiveFields.vue";
afterEach(cleanup);
const fields = [
    createField({ id: 1, fieldCode: "title", fieldName: "题名", editControl: "INPUT" }),
    createField({
        id: 2,
        fieldCode: "amount",
        fieldName: "金额",
        fieldType: "DECIMAL",
        editControl: "NUMBER",
    }),
    createField({ id: 3, fieldCode: "remark", fieldName: "备注", editControl: "TEXTAREA" }),
];
describe("DynamicArchiveFields", () => {
    it("支持按字段编码批量赋值和提交", async () => {
        const Harness = defineComponent({
            components: { DynamicArchiveFields },
            setup() {
                const values = ref<Record<string, unknown>>({
                    title: "项目建设审批材料",
                    amount: 1200.5,
                    remark: "批量回填备注",
                });
                const submitted = ref("");
                return {
                    fields,
                    values,
                    submitted,
                    submit: () => {
                        submitted.value = JSON.stringify(
                            normalizeArchiveRecordFormValues({ dynamicFields: values.value }),
                        );
                    },
                };
            },
            template: `<el-form label-position="top"><DynamicArchiveFields v-model="values" :fields="fields" /><el-button aria-label="保存" @click="submit">保存</el-button><output aria-label="提交值">{{ submitted }}</output></el-form>`,
        });
        render(Harness, { global: { plugins: [ElementPlus] } });
        await waitFor(() => expect(screen.getByLabelText("题名")).toHaveValue("项目建设审批材料"));
        await fireEvent.update(screen.getByLabelText("题名"), "项目建设审批材料（修订）");
        await fireEvent.click(screen.getByRole("button", { name: "保存" }));
        expect(screen.getByLabelText("提交值").textContent).toContain(
            '"title":"项目建设审批材料（修订）"',
        );
        expect(screen.getByLabelText("提交值").textContent).toContain('"amount":1200.5');
    });
});
function createField(field: Partial<ArchiveFieldDto>): ArchiveFieldDto {
    return {
        archiveLevel: "ITEM",
        categoryId: 1,
        columnName: `f_${field.fieldCode ?? "field"}`,
        createdAt: "",
        decimalPrecision: 18,
        decimalScale: 2,
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
