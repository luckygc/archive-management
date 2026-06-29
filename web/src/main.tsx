import { StrictMode } from "react";
import { registerSessionResetHandler } from "@archive-management/frontend-core/authentication";
import { createRoot } from "react-dom/client";
import capWasmUrl from "@cap.js/wasm/browser/cap_wasm_bg.wasm?url";

import { App } from "./app/App";
import { resetPageTabsStore } from "./shared/tabs/pageTabsStore";

import "antd/dist/reset.css";

window.CAP_CUSTOM_WASM_URL = capWasmUrl;
registerSessionResetHandler(resetPageTabsStore);

await import("cap-widget");

const rootElement = document.getElementById("root");

if (rootElement === null) {
    throw new Error("React root element #root does not exist.");
}

createRoot(rootElement).render(
    <StrictMode>
        <App />
    </StrictMode>,
);
