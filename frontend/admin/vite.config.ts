import vue from "@vitejs/plugin-vue";
import { fileURLToPath, URL } from "node:url";
import Components from "unplugin-vue-components/vite";
import { ElementPlusResolver } from "unplugin-vue-components/resolvers";
import { defineConfig } from "vite-plus";

export default defineConfig({
    plugins: [
        vue(),
        Components({
            dts: "src/components.d.ts",
            directives: true,
            resolvers: [
                ElementPlusResolver({
                    importStyle: process.env.NODE_ENV === "test" ? false : "css",
                }),
            ],
        }),
    ],
    resolve: {
        alias: {
            "@": fileURLToPath(new URL("./src", import.meta.url)),
        },
    },
    server: {
        proxy: {
            "^/(?:approval-|archive-|authentication-(?:events|user-options|users)(?:/|:|$)|authorization-|file-links(?:/|:|$)|intake(?:/|:|$)|login-|me(?:/|:|$)|operations(?:/|$)|organization-|public-file-links(?:/|:|$)|totp-|unified-todos(?:/|:|$)|workspace-summary(?:/|:|$))":
                {
                    target: "http://localhost:8080",
                    changeOrigin: true,
                },
            "/actuator": {
                target: "http://localhost:8080",
                changeOrigin: true,
            },
        },
    },
    build: {
        chunkSizeWarningLimit: 1200,
    },
    test: {
        environment: "jsdom",
        setupFiles: ["./src/test/setup.ts"],
    },
});
