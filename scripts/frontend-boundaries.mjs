import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { dirname, relative, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";
import { sourceFiles } from "./source-lines.mjs";

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const adminRoot = resolve(projectRoot, "frontend/admin/src");
const coreRoot = resolve(projectRoot, "frontend/packages/core/src");
const require = createRequire(resolve(projectRoot, "frontend/admin/package.json"));
const ts = require("typescript");

export function importPaths(source, vue = false) {
    const paths = [];
    const scripts = vue
        ? [...source.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/g)].map((match) => {
            const external = /\bsrc\s*=\s*(["'])(.*?)\1/.exec(match[1]);
            if (external) paths.push(external[2]);
            return match[2];
        })
        : [source];
    for (const script of scripts) {
        const ast = ts.createSourceFile("source.ts", script, ts.ScriptTarget.Latest, true);
        function visit(node) {
            if ((ts.isImportDeclaration(node) || ts.isExportDeclaration(node))
                && node.moduleSpecifier && ts.isStringLiteral(node.moduleSpecifier)) {
                paths.push(node.moduleSpecifier.text);
            } else if (ts.isCallExpression(node)
                && (node.expression.kind === ts.SyntaxKind.ImportKeyword
                    || (ts.isIdentifier(node.expression) && node.expression.text === "require"))
                && node.arguments.length && ts.isStringLiteralLike(node.arguments[0])) {
                paths.push(node.arguments[0].text);
            } else if (ts.isImportTypeNode(node) && ts.isLiteralTypeNode(node.argument)
                && ts.isStringLiteral(node.argument.literal)) {
                paths.push(node.argument.literal.text);
            }
            ts.forEachChild(node, visit);
        }
        visit(ast);
    }
    return paths;
}

function within(path, root) {
    return path === root || path.startsWith(root + sep);
}

export function dependencyViolation(file, dependency) {
    const target = dependency.startsWith("@/")
        ? resolve(adminRoot, dependency.slice(2))
        : dependency.startsWith(".") ? resolve(dirname(file), dependency) : undefined;
    if (within(file, coreRoot)) {
        if ((target && within(target, resolve(projectRoot, "frontend/admin")))
            || /^(vue|vue-router|pinia|element-plus)(\/|$)|^@(vue|vueuse|element-plus)\//.test(dependency)) {
            return "core 只能依赖框架无关能力，不能引用管理端或 UI 框架";
        }
        return;
    }
    if (!target || !within(target, adminRoot)) return;
    const source = relative(adminRoot, file).replaceAll("\\", "/");
    const destination = relative(adminRoot, target).replaceAll("\\", "/");
    if (source.startsWith("shared/") && /^(pages|app|layout)\//.test(destination)) {
        return "共享能力不能反向引用页面或应用壳层";
    }
    if (!destination.startsWith("pages/")) return;
    if (source.startsWith("shared/") || source.startsWith("stores/")) {
        return "共享能力和全局状态不能反向引用页面";
    }
    if (source.startsWith("pages/") && source.split("/")[1] !== destination.split("/")[1]) {
        return "页面不能引用其他页面内部实现，请提取到所属共享业务目录";
    }
}

export function checkFrontendBoundaries() {
    const files = [adminRoot, coreRoot].flatMap(sourceFiles).filter((file) =>
        /\.(ts|vue)$/.test(file) && !/\.(test|spec)(-support)?\./.test(file)
        && !relative(adminRoot, file).replaceAll("\\", "/").startsWith("test/"));
    return files.flatMap((file) => importPaths(readFileSync(file, "utf8"), file.endsWith(".vue"))
        .flatMap((dependency) => {
            const violation = dependencyViolation(file, dependency);
            return violation ? [`${relative(projectRoot, file).replaceAll("\\", "/")} -> ${dependency}: ${violation}`] : [];
        }));
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    const violations = checkFrontendBoundaries();
    if (violations.length) {
        console.error(violations.join("\n"));
        process.exitCode = 1;
    } else {
        console.log("前端依赖边界检查通过");
    }
}
