import ElementPlus from "element-plus";
import "element-plus/dist/index.css";
import "./shared/styles/index.scss";
import capWasmUrl from "@cap.js/wasm/browser/cap_wasm_bg.wasm?url";

import { createPinia } from "pinia";
import { createApp } from "vue";
import App from "./app/App.vue";
import router from "./app/router";

window.CAP_CUSTOM_WASM_URL = capWasmUrl;

await import("cap-widget");

const app = createApp(App);

app.use(createPinia());
app.use(router);
app.use(ElementPlus);

app.mount("#app");
