import ElementPlus from "element-plus";
import "element-plus/dist/index.css";
import "./shared/styles/index.scss";

import { createPinia } from "pinia";
import { createApp } from "vue";
import App from "./app/App.vue";
import router from "./app/router";

const app = createApp(App);

app.use(createPinia());
app.use(router);
app.use(ElementPlus);

app.mount("#app");
