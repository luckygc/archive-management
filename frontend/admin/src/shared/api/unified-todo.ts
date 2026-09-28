import { httpClient } from "@archive-management/frontend-core/api";

import type { CursorPageResponse } from "../types/pagination";
import type { UnifiedTodoDto } from "../types/unified-todo";
import { pageUrl } from "./query-string";

export function listMyUnifiedTodos(
    params: { completed?: boolean; limit?: number; cursor?: string } = {},
) {
    return httpClient.get<CursorPageResponse<UnifiedTodoDto>>(
        pageUrl("/unified-todos", { limit: 100, ...params }),
    );
}
