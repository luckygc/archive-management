import { cleanup, fireEvent, render, screen } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { afterEach, describe, expect, it } from "vitest";
import type { ArchiveRecordListDto } from "@/shared/types/archive-records";
import ArchiveResultTable from "./ArchiveResultTable.vue";
afterEach(cleanup);
describe("ArchiveResultTable", () => {
    it("按优先级提交固定字段和动态字段的远程多列排序", async () => {
        const view = render(ArchiveResultTable, {
            props: { result: archiveResult() },
            global: { plugins: [ElementPlus] },
        });
        expect(await screen.findAllByText("成文日期")).not.toHaveLength(0);
        expect(screen.queryByText("状态")).not.toBeInTheDocument();
        await fireEvent.click(screen.getByRole("button", { name: /^档号，未排序/ }));
        await fireEvent.click(screen.getByRole("button", { name: /^成文日期，未排序/ }), {
            shiftKey: true,
        });
        expect(view.emitted().orderChange).toEqual([
            [[{ field: "archiveNo", direction: "ASC" }]],
            [
                [
                    { field: "archiveNo", direction: "ASC" },
                    { field: "formed_date", direction: "ASC" },
                ],
            ],
        ]);
    });
});
function archiveResult(): ArchiveRecordListDto {
    return {
        fields: [
            {
                id: 1,
                categoryId: 1,
                archiveLevel: "ITEM",
                fieldScope: "METADATA",
                fieldCode: "formed_date",
                fieldName: "成文日期",
                fieldType: "DATE",
                columnName: "f_formed_date",
                editControl: "DATE",
                listVisible: true,
                listWidth: 120,
                listSortOrder: 10,
                detailVisible: true,
                detailColSpan: 1,
                detailSortOrder: 10,
                editVisible: true,
                editColSpan: 1,
                editSortOrder: 10,
                exactSearchable: true,
                dataScopeFilterable: false,
                enabled: true,
                sortOrder: 10,
                createdAt: "",
                updatedAt: "",
            },
        ],
        items: [{ id: 1, f_formed_date: "2026-01-12" }],
    };
}
