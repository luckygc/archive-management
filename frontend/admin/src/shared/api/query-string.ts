export function queryString(params: Record<string, string | number | boolean | undefined>) {
    const search = new URLSearchParams();
    for (const [key, value] of Object.entries(params)) {
        if (value !== undefined && value !== "") {
            search.set(key, String(value));
        }
    }
    const text = search.toString();
    return text ? `?${text}` : "";
}

export function pageUrl(
    path: string,
    params: Record<string, string | number | boolean | undefined> & { cursor?: string },
) {
    const { cursor, ...query } = params;
    if (!cursor) {
        return `${path}${queryString(query)}`;
    }
    const link = new URL(cursor, "http://localhost");
    if (
        !cursor.startsWith("/") ||
        cursor.startsWith("//") ||
        link.origin !== "http://localhost" ||
        link.pathname !== path ||
        !link.searchParams.has("cursor") ||
        !link.searchParams.has("limit")
    ) {
        throw new Error("分页链接无效");
    }
    return `${link.pathname}${link.search}`;
}
