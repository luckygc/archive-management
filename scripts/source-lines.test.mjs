import assert from "node:assert/strict";
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { spawnSync } from "node:child_process";
import { test } from "node:test";
import { fileURLToPath } from "node:url";
import { checkSourceLines, effectiveLines, sourceLimits } from "./source-lines.mjs";

test("前后端沿用各自软提示与硬失败阈值", () => {
    assert.deepEqual(sourceLimits("frontend/admin/src/example.ts"), { soft: 300, hard: 500 });
    assert.deepEqual(sourceLimits("C:\\project\\frontend\\admin\\src\\example.ts"), { soft: 300, hard: 500 });
    assert.deepEqual(sourceLimits("server/src/main/java/Example.java"), { soft: 500, hard: 700 });
});

test("有效行数排除空白和注释，保留字符串中的注释标记", () => {
    assert.equal(effectiveLines('\n// 注释\n/* 多行\n注释 */\nconst url = "http://example"; // 注释\n'), 1);
    assert.equal(effectiveLines("const value = `\n/* literal */\n// literal\n`;\nconst after = 1;"), 5);
    assert.equal(effectiveLines('String value = """\n/* literal */\n// literal\n""";\nint after = 1;'), 5);
    assert.equal(effectiveLines('<template>\n<!-- 多行\n注释 -->\n<div>内容</div>\n</template>'), 3);
    assert.equal(effectiveLines('const pattern = /[/*]/;\nconst after = 1;'), 2);
    assert.equal(effectiveLines('const pattern = /[\\/]/; // 注释\nconst after = 1;'), 2);
    assert.equal(effectiveLines('const ratio = numerator / denominator; /* 注释\n仍是注释 */\nconst after = 1;'), 2);
});

test("命令在硬阈值内成功、超出时失败，report 仍输出但不失败", (context) => {
    const temporary = mkdtempSync(join(tmpdir(), "archive-source-lines-"));
    context.after(() => rmSync(temporary, { recursive: true, force: true }));
    const root = join(temporary, "frontend", "admin", "src");
    mkdirSync(root, { recursive: true });
    const file = join(root, "Example.ts");
    const run = (...arguments_) => spawnSync(process.execPath, [
        fileURLToPath(new URL("./source-lines.mjs", import.meta.url)),
        ...arguments_, root,
    ], { encoding: "utf8" });
    writeFileSync(file, "const value = 1;\n".repeat(301));
    assert.equal(run().status, 0);
    assert.match(run().stdout, /^301\t/m);
    writeFileSync(file, "const value = 1;\n".repeat(501));
    assert.equal(run().status, 1);
    assert.equal(run("--report").status, 0);
    assert.match(run("--report").stdout, /^501\t/m);
    assert.equal(checkSourceLines([root])[0].lines, 501);
});
