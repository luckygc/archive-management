import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vite-plus/test";

import type { ArchiveRecordListDto } from "@/shared/types/archive";

import { ArchiveResultTable } from "./ArchiveResultTable";

afterEach(() => {
    cleanup();
});

describe("ArchiveResultTable", () => {
    it("uses searchable dynamic field code for remote sorting", () => {
        const onOrderChange = vi.fn();

        render(<ArchiveResultTable result={archiveResult()} onOrderChange={onOrderChange} />);

        fireEvent.click(screen.getAllByText("成文日期")[0]);

        expect(onOrderChange).toHaveBeenCalledWith([{ field: "formed_date", direction: "ASC" }]);
    });
});

function archiveResult(): ArchiveRecordListDto {
    return {
        tableBuilt: true,
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
                enabled: true,
                sortOrder: 10,
                createdAt: "2026-06-29T00:00:00",
                updatedAt: "2026-06-29T00:00:00",
            },
        ],
        items: [
            {
                id: 1,
                archive_no: "GW-2026-001",
                fonds_name: "集团全宗",
                category_name: "公文档案",
                archive_year: 2026,
                electronic_status: "DRAFT",
                f_formed_date: "2026-01-12",
            },
        ],
    };
}
