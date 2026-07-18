import "@testing-library/jest-dom/vitest";

import { vi } from "vite-plus/test";

const getComputedStyle = window.getComputedStyle.bind(window);

Object.defineProperty(window, "getComputedStyle", {
    writable: true,
    value: vi.fn().mockImplementation((element: Element) => getComputedStyle(element)),
});

Object.defineProperty(window, "matchMedia", {
    writable: true,
    value: vi.fn().mockImplementation((query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
    })),
});

class TestResizeObserver {
    observe = vi.fn();

    unobserve = vi.fn();

    disconnect = vi.fn();
}

Object.defineProperty(window, "ResizeObserver", {
    writable: true,
    value: TestResizeObserver,
});

Object.defineProperty(globalThis, "ResizeObserver", {
    writable: true,
    value: TestResizeObserver,
});
