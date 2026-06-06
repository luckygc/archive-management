import vue from "@vitejs/plugin-vue";
import { fileURLToPath, URL } from "node:url";
import { defineConfig } from "vite-plus";

const nonFrontendSourcePatterns = [
  ".agents/**",
  ".codex/**",
  "openspec/**",
  "server/**",
  "AGENTS.md",
  "PRODUCT.md",
];

export default defineConfig({
  plugins: [
    vue({
      template: {
        compilerOptions: { isCustomElement: (tag) => tag.startsWith("cap-") },
      },
    }),
  ],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  server: {
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
      "/actuator": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
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
