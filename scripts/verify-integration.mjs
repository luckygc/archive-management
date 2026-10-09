import { readFileSync, readdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { sourceFiles } from "./source-lines.mjs";

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");

export function requireDocker(run = spawnSync) {
    const result = run("docker", ["info", "--format", "{{.ServerVersion}}"], {
        encoding: "utf8", timeout: 30_000,
    });
    if (result.error || result.status !== 0 || !result.stdout?.trim()) {
        throw new Error("完整验证需要可用的 Docker；请启动 Docker 后重试，不能跳过集成测试。");
    }
}

export function reportCounts(xml) {
    const suite = /<testsuite\b([^>]*)>/.exec(xml)?.[1];
    if (!suite) throw new Error("Surefire 报告缺少 testsuite");
    return Object.fromEntries(["tests", "failures", "errors", "skipped"].map((name) => {
        const count = new RegExp(`\\b${name}="(\\d+)"`).exec(suite)?.[1];
        if (count === undefined) throw new Error(`Surefire 报告缺少有效的 ${name}`);
        return [name, Number(count)];
    }));
}

export function requireCompleteReports(reports, requiredClasses) {
    if (!reports.length) throw new Error("未生成后端测试报告");
    if (!requiredClasses.length) throw new Error("未发现必需的容器集成测试类");
    const names = new Set(reports.map((report) => report.name));
    const missing = requiredClasses.filter((name) => !names.has(`TEST-${name}.xml`));
    if (missing.length) throw new Error(`集成测试未执行：${missing.join("、")}`);
    let tests = 0;
    for (const report of reports) {
        const counts = reportCounts(report.xml);
        if (!counts.tests || counts.failures || counts.errors || counts.skipped) {
            throw new Error(`${report.name} 验证不完整：测试 ${counts.tests}，失败 ${counts.failures}，错误 ${counts.errors}，跳过 ${counts.skipped}`);
        }
        tests += counts.tests;
    }
    return tests;
}

function verifyReports() {
    const testRoot = resolve(projectRoot, "server/src/test/java");
    const requiredClasses = sourceFiles(testRoot).filter((file) => file.endsWith(".java"))
        .flatMap((file) => {
            const source = readFileSync(file, "utf8");
            if (!/@Testcontainers\b/.test(source)) return [];
            const packageName = /\bpackage\s+([\w.]+)\s*;/.exec(source)?.[1];
            const className = /\bclass\s+(\w+)/.exec(source)?.[1];
            if (!packageName || !className) throw new Error(`无法识别集成测试类：${file}`);
            return [`${packageName}.${className}`];
        });
    const reportRoot = resolve(projectRoot, "server/target/surefire-reports");
    const reports = readdirSync(reportRoot).filter((name) => /^TEST-.*\.xml$/.test(name))
        .map((name) => ({ name, xml: readFileSync(resolve(reportRoot, name), "utf8") }));
    const count = requireCompleteReports(reports, requiredClasses);
    console.log(`后端完整验证通过：${count} 项测试，${requiredClasses.length} 个容器集成测试类，零跳过`);
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    try {
        if (process.argv[2] === "docker") requireDocker();
        else if (process.argv[2] === "reports") verifyReports();
        else throw new Error("用法：node scripts/verify-integration.mjs docker|reports");
    } catch (error) {
        console.error(error.message);
        process.exitCode = 1;
    }
}
