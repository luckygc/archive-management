import { describe, expect, it } from "vite-plus/test";

import { toSearchQuery } from "./archiveQuery";

describe("archiveQuery", () => {
    it("normalizes search conditions without exposing logic switch", () => {
        const query = toSearchQuery({
            categoryId: 1,
            conditions: [
                {
                    fieldCode: "archiveNo",
                    op: "EQ",
                    value: " A-001 ",
                },
            ],
        });

        expect(query.where).toEqual({
            conditions: [
                {
                    fieldCode: "archiveNo",
                    op: "EQ",
                    value: "A-001",
                    startValue: undefined,
                    endValue: undefined,
                },
            ],
        });
        expect(query.where).not.toHaveProperty("logic");
    });
});
