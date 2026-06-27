import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import capWasmUrl from "@cap.js/wasm/browser/cap_wasm_bg.wasm?url";

import { App } from "./app/App";

import "antd-mobile/es/global";
import "./app/styles.css";

window.CAP_CUSTOM_WASM_URL = capWasmUrl;

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
