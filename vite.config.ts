import { defineConfig } from "vite-plus";

const nonFrontendSourcePatterns = [
  ".agents/**",
  ".codex/**",
  "openspec/**",
  "server/**",
  "AGENTS.md",
  "PRODUCT.md",
  "pnpm-workspace.yaml",
];

export default defineConfig({
  staged: {
    "*": "vp check --fix",
  },
  fmt: {
    ignorePatterns: nonFrontendSourcePatterns,
  },
  lint: {
    ignorePatterns: nonFrontendSourcePatterns,
    jsPlugins: [{ name: "vite-plus", specifier: "vite-plus/oxlint-plugin" }],
    rules: { "vite-plus/prefer-vite-plus-imports": "error" },
    options: { typeAware: true, typeCheck: true },
  },
  run: {
    cache: true,
  },
});
