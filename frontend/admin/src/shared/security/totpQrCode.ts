import { renderSVG } from "uqr";

export function totpQrCodeDataUrl(otpauthUri: string) {
    const svg = renderSVG(otpauthUri, {
        border: 4,
        boostEcc: true,
        ecc: "M",
        pixelSize: 8,
    });
    return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
}
