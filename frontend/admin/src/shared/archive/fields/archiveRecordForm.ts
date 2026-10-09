import { z } from "zod";

const archiveRecordFormValuesSchema = z.object({
    dynamicFields: z.record(z.string(), z.unknown()).default({}),
});

export function normalizeArchiveRecordFormValues(values: unknown) {
    return archiveRecordFormValuesSchema.parse(values);
}
