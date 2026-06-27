/// <reference types="vite/client" />

interface Window {
    CAP_CUSTOM_FETCH?: typeof fetch;
    CAP_CUSTOM_WASM_URL?: string;
}
