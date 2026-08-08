import { describe, expect, it, vi } from "vite-plus/test";

import { totpQrCodeDataUrl } from "./totpQrCode";

describe("totpQrCodeDataUrl", () => {
    it("在浏览器内生成二维码且不把 otpauth URI 发往远程服务", () => {
        const fetchSpy = vi.spyOn(window, "fetch");

        const result = totpQrCodeDataUrl(
            "otpauth://totp/Archive%20Management:admin?secret=ABC123&issuer=Archive%20Management",
        );

        expect(result).toMatch(/^data:image\/svg\+xml;charset=utf-8,/);
        expect(decodeURIComponent(result.split(",", 2)[1] ?? "")).toContain("<svg");
        expect(fetchSpy).not.toHaveBeenCalled();
        fetchSpy.mockRestore();
    });
});
