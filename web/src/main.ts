import { createPinia } from "pinia";
import { createApp } from "vue";
import capWasmUrl from "@cap.js/wasm/browser/cap_wasm_bg.wasm?url";

import App from "./app/App.vue";
import { router } from "./app/routes";

import "element-plus/es/components/message/style/css";
import "element-plus/es/components/message-box/style/css";
import "./app/styles.css";

window.CAP_CUSTOM_WASM_URL = capWasmUrl;
await import("cap-widget");

const app = createApp(App);
app.use(createPinia());
app.use(router);
app.mount("#app");
