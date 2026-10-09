import { readdirSync, readFileSync } from "node:fs";
import { dirname, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");

function regularExpressionEnd(line, start, prefix) {
    if (!/(?:^|[=(:,\[!&|?;{}])\s*$|\b(?:return|throw|case|yield|await)\s+$/.test(prefix)) return -1;
    let characterClass = false;
    for (let index = start + 1; index < line.length; index++) {
        const character = line[index];
        if (character === "\\") {
            index++;
        } else if (character === "[") {
            characterClass = true;
        } else if (character === "]") {
            characterClass = false;
        } else if (character === "/" && !characterClass) {
            return index + 1;
        }
    }
    return -1;
}

export function effectiveLines(source) {
    let blockComment = false;
    let htmlComment = false;
    let quote = "";
    let count = 0;
    for (const line of source.split(/\r?\n/)) {
        let code = "";
        let index = 0;
        while (index < line.length) {
            if (blockComment || htmlComment) {
                const marker = blockComment ? "*/" : "-->";
                const end = line.indexOf(marker, index);
                if (end < 0) break;
                blockComment = false;
                htmlComment = false;
                index = end + marker.length;
                continue;
            }
            if (quote) {
                if (quote === '"""' && line.startsWith(quote, index)) {
                    code += quote;
                    index += quote.length;
                    quote = "";
                    continue;
                }
                const character = line[index];
                code += character;
                if (quote !== '"""' && character === "\\") {
                    index++;
                    code += line[index] ?? "";
                } else if (quote !== '"""' && character === quote) {
                    quote = "";
                }
                index++;
                continue;
            }
            if (line.startsWith('"""', index)) {
                quote = '"""';
                code += quote;
                index += quote.length;
                continue;
            }
            const character = line[index];
            if ('"\'`'.includes(character)) {
                quote = character;
                code += character;
                index++;
            } else if (line.startsWith("<!--", index)) {
                htmlComment = true;
                index += 4;
            } else if (line.startsWith("/*", index)) {
                blockComment = true;
                index += 2;
            } else if (line.startsWith("//", index)) {
                break;
            } else if (character === "/") {
                const end = regularExpressionEnd(line, index, code);
                if (end > index) {
                    code += line.slice(index, end);
                    index = end;
                } else {
                    code += character;
                    index++;
                }
            } else {
                code += character;
                index++;
            }
        }
        if (/\S/.test(code)) count++;
    }
    return count;
}

export function sourceLimits(path) {
    return /(?:^|\/)frontend(?:\/|$)/.test(path.replaceAll("\\", "/"))
        ? { soft: 300, hard: 500 }
        : { soft: 500, hard: 700 };
}

export function sourceFiles(root) {
    return readdirSync(root, { withFileTypes: true }).flatMap((entry) => {
        const path = resolve(root, entry.name);
        if (entry.isDirectory()) return sourceFiles(path);
        return entry.isFile() && /\.(java|ts|vue|css)$/.test(entry.name) ? [path] : [];
    });
}

export function checkSourceLines(roots) {
    return roots.flatMap(sourceFiles).map((path) => ({
        path,
        lines: effectiveLines(readFileSync(path, "utf8")),
        ...sourceLimits(path),
    })).sort((a, b) => b.lines - a.lines || a.path.localeCompare(b.path));
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    const arguments_ = process.argv.slice(2);
    const report = arguments_.includes("--report");
    const requested = arguments_.filter((argument) => argument !== "--report");
    const roots = requested.length ? requested : [
        "server/src/main/java", "frontend/admin/src", "frontend/packages/core/src",
    ];
    const results = checkSourceLines(roots.map((root) => resolve(projectRoot, root)));
    for (const result of results.filter((result) => result.lines > result.soft)) {
        console.log(`${result.lines}\t${relative(projectRoot, result.path).replaceAll("\\", "/")}`);
    }
    const violations = results.filter((result) => result.lines > result.hard);
    if (!report && violations.length) {
        console.error(`源码存在 ${violations.length} 个超过硬阈值的文件`);
        process.exitCode = 1;
    }
}
