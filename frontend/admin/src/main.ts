import { createPinia } from "pinia";
import { createApp } from "vue";

import App from "./app/App.vue";
import { router } from "./app/routes";

import "element-plus/es/components/message/style/css";
import "element-plus/es/components/message-box/style/css";
import "./app/styles.css";

const app = createApp(App);
app.use(createPinia());
app.use(router);
app.mount("#app");
