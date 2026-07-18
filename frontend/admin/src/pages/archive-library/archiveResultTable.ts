import type { ArchiveFieldDto } from "@/shared/types/archive-metadata";
import type { ArchiveRecordOrderBy, ArchiveRecordSortField } from "@/shared/types/archive-records";

export function toArchiveRecordOrder(
    prop: string | null,
    order: "ascending" | "descending" | null,
    fields: ArchiveFieldDto[],
): ArchiveRecordOrderBy[] {
    if (!prop || !order) return [];
    const fixed: Record<string, ArchiveRecordSortField> = {
        archive_no: "archiveNo",
        archive_year: "archiveYear",
        fonds_name: "fondsCode",
        category_name: "categoryCode",
        electronic_status: "electronicStatus",
    };
    const dynamic = fields.find((field) => field.columnName === prop);
    return [
        {
            field: fixed[prop] ?? dynamic?.fieldCode ?? prop,
            direction: order === "ascending" ? "ASC" : "DESC",
        },
    ];
}
