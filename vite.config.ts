import { fileURLToPath, URL } from "node:url";
import { defineConfig } from "vite-plus";

export default defineConfig({
    plugins: [workspaceAliasPlugin()],
    lint: {
        ignorePatterns: [
            ".agents/**",
            ".codex/**",
            "docs/**",
            "openspec/**",
            "server/**",
            "*.md",
            "AGENTS.md",
        ],
    },
    fmt: {
        ignorePatterns: [
            ".agents/**",
            ".codex/**",
            "docs/**",
            "openspec/**",
            "server/**",
            "*.md",
            "AGENTS.md",
        ],
    },
    staged: {
        "*": "vp check --fix",
    },
    run: {
        cache: true,
    },
    test: {
        environment: "jsdom",
        setupFiles: ["./test/setup.ts"],
        testTimeout: 15_000,
        server: {
            deps: {
                inline: ["@ant-design/pro-components"],
            },
        },
    },
});

function workspaceAliasPlugin() {
    const webSrc = fileURLToPath(new URL("./web/src", import.meta.url));
    const mobileSrc = fileURLToPath(new URL("./mobile/src", import.meta.url));
    return {
        name: "archive-management-workspace-alias",
        async resolveId(source: string, importer?: string) {
            if (!source.startsWith("@/")) {
                return null;
            }
            const srcRoot = importer?.includes("/mobile/") ? mobileSrc : webSrc;
            const resolved = await this.resolve(source.replace("@", srcRoot), importer, {
                skipSelf: true,
            });
            return resolved?.id ?? null;
        },
    };
}
