import { httpClient } from "@archive-management/frontend-core/api";

import type { WorkspaceSummaryResponse } from "../types/workspace";

export function getWorkspaceSummary() {
    return httpClient.get<WorkspaceSummaryResponse>("/api/v1/workspace-summary");
}
