import { strict as assert } from "node:assert";
import { test } from "node:test";
import { reportCounts, requireCompleteReports, requireDocker } from "./verify-integration.mjs";

const xml = (attributes = 'tests="3" failures="0" errors="0" skipped="0"') =>
    `<testsuite name="容器集成测试" ${attributes}></testsuite>`;
const report = (source = xml()) => ({ name: "TEST-example.IntegrationTests.xml", xml: source });

test("Docker 无守护进程、不可执行或超时均阻断完整验证", () => {
    for (const result of [{ status: 1 }, { error: new Error("ENOENT") }, { status: null, error: new Error("ETIMEDOUT") }, { status: 0, stdout: "" }]) {
        assert.throws(() => requireDocker(() => result), /不能跳过集成测试/);
    }
    requireDocker(() => ({ status: 0, stdout: "28.0.0\n" }));
});

test("Surefire 只读取根计数，不读取测试日志或嵌套内容", () => {
    assert.deepEqual(reportCounts(xml()), { tests: 3, failures: 0, errors: 0, skipped: 0 });
    assert.throws(() => reportCounts("<report/>"), /缺少 testsuite/);
    assert.throws(() => reportCounts(xml('tests="3" failures="0" errors="0" skipped="invalid"')), /skipped/);
});

test("缺少报告或必需的容器集成测试报告不能通过", () => {
    assert.throws(() => requireCompleteReports([], []), /未生成/);
    assert.throws(() => requireCompleteReports([report()], []), /未发现/);
    assert.throws(() => requireCompleteReports([report()], ["other.RequiredTests"]), /未执行/);
});

test("零用例、失败、错误和跳过都阻断完整验证", () => {
    for (const attributes of [
        'tests="0" failures="0" errors="0" skipped="0"',
        'tests="3" failures="1" errors="0" skipped="0"',
        'tests="3" failures="0" errors="1" skipped="0"',
        'tests="3" failures="0" errors="0" skipped="3"',
    ]) assert.throws(() => requireCompleteReports([report(xml(attributes))], ["example.IntegrationTests"]), /验证不完整/);
    assert.equal(requireCompleteReports([report()], ["example.IntegrationTests"]), 3);
});
