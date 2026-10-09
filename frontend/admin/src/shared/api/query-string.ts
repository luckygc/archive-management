import { queryString } from "@archive-management/frontend-core/api";

export { queryString };

export function pageUrl(
    path: string,
    params: Record<string, string | number | boolean | null | undefined> & {
        cursor?: string | null;
    },
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
