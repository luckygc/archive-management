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
        "*": "pnpm exec vp check --fix",
    },
    run: {
        cache: true,
    },
    test: {
        environment: "jsdom",
        setupFiles: ["./test/setup.ts"],
        testTimeout: 15_000,
    },
});

function workspaceAliasPlugin() {
    const webSrc = fileURLToPath(new URL("./web/src", import.meta.url));
    return {
        name: "archive-management-workspace-alias",
        async resolveId(source: string, importer?: string) {
            if (!source.startsWith("@/")) {
                return null;
            }
            const resolved = await this.resolve(source.replace("@", webSrc), importer, {
                skipSelf: true,
            });
            return resolved?.id ?? null;
        },
    };
}
