import assert from "node:assert/strict";
import { dirname, resolve } from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";
import { dependencyViolation, importPaths } from "./frontend-boundaries.mjs";

const project = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const admin = resolve(project, "frontend/admin/src");
const core = resolve(project, "frontend/packages/core/src");

test("识别静态、动态与再导出依赖，忽略字符串和注释", () => {
    assert.deepEqual(importPaths(`
        import type { Value } from "./types";
        export { value } from "./values";
        const page = () => import("@/pages/example/Page.vue");
        // import value from "./comment";
        const text = 'import value from "./literal"';
    `), ["./types", "./values", "@/pages/example/Page.vue"]);
    assert.deepEqual(importPaths('<script setup lang="ts">import x from "./x";</script><template>import x from "./ignored"</template>', true), ["./x"]);
});

test("识别模板字符串动态导入、类型导入及 Vue 外部脚本", () => {
    assert.deepEqual(importPaths('const page = () => import(`@/pages/archive-library/Page.vue`);'), ["@/pages/archive-library/Page.vue"]);
    assert.deepEqual(importPaths('type Value = import("./types").Value;'), ["./types"]);
    assert.deepEqual(importPaths('<script lang="ts" src="../archive-library/logic.ts"></script>', true), ["../archive-library/logic.ts"]);
    assert.deepEqual(importPaths('const page = () => import(`./${name}.vue`);'), []);
});

test("页面只引用自己的内部实现，路由装配可引用页面", () => {
    assert.ok(dependencyViolation(resolve(admin, "pages/archive-items/Page.vue"), "../archive-library/Query.vue"));
    assert.ok(dependencyViolation(resolve(admin, "pages/archive-items/Page.vue"), "@/pages/archive-library/Query.vue"));
    assert.equal(dependencyViolation(resolve(admin, "pages/archive-items/Page.vue"), "./Editor.vue"), undefined);
    assert.equal(dependencyViolation(resolve(admin, "app/routes.ts"), "@/pages/archive-items/Page.vue"), undefined);
});

test("共享能力和 core 不得反向依赖页面与 UI 框架", () => {
    assert.ok(dependencyViolation(resolve(admin, "shared/archive/query.ts"), "@/pages/archive-library/query"));
    assert.ok(dependencyViolation(resolve(admin, "shared/archive/query.ts"), "@/app/routes"));
    assert.ok(dependencyViolation(resolve(admin, "shared/archive/query.ts"), "@/layout/AppShell.vue"));
    assert.ok(dependencyViolation(resolve(admin, "stores/session.ts"), "../pages/login/LoginPage.vue"));
    assert.ok(dependencyViolation(resolve(core, "api/client.ts"), "vue"));
    for (const framework of ["@vue/runtime-core", "@vue/reactivity", "@vueuse/core", "@element-plus/icons-vue"]) {
        assert.ok(dependencyViolation(resolve(core, "api/client.ts"), framework));
    }
    assert.ok(dependencyViolation(resolve(core, "api/client.ts"), "../../../../admin/src/pages/login/LoginPage.vue"));
    assert.equal(dependencyViolation(resolve(core, "api/client.ts"), "axios"), undefined);
});
