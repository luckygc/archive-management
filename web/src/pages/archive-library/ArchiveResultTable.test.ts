import { cleanup, render, screen } from "@testing-library/vue";
import ElementPlus from "element-plus";
import { afterEach, describe, expect, it } from "vitest";
import type { ArchiveRecordListDto } from "@/shared/types/archive-records";
import ArchiveResultTable from "./ArchiveResultTable.vue";
import { toArchiveRecordOrder } from "./archiveResultTable";
afterEach(cleanup);
describe("ArchiveResultTable", () => {
    it("使用可检索动态字段编码进行远程排序", async () => {
        render(ArchiveResultTable, {
            props: { result: archiveResult() },
            global: { plugins: [ElementPlus] },
        });
        expect(await screen.findAllByText("成文日期")).not.toHaveLength(0);
        expect(toArchiveRecordOrder("f_formed_date", "ascending", archiveResult().fields)).toEqual([
            { field: "formed_date", direction: "ASC" },
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
