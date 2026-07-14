export function downloadFromLink(href: string) {
    const anchor = document.createElement("a");
    anchor.href = href;
    anchor.click();
}
