import { describe, expect, it } from "vite-plus/test";

import { queryString } from "./query-string";

describe("查询字符串", () => {
    it("省略未提供的值并保留零和 false", () => {
        expect(
            queryString({ absent: undefined, nullable: null, empty: "", zero: 0, flag: false }),
        ).toBe("?zero=0&flag=false");
        expect(queryString({ empty: "" })).toBe("");
    });

    it("按 URL 编码中文和排序方向", () => {
        expect(queryString({ keyword: "合同 & 文书", sort: "+archiveNo" })).toBe(
            "?keyword=%E5%90%88%E5%90%8C+%26+%E6%96%87%E4%B9%A6&sort=%2BarchiveNo",
        );
    });
});
