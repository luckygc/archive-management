import { request } from "@archive-management/frontend-core/api";

import type { IntakeOverviewDto } from "@/shared/types/intake";

export function getIntakeOverview() {
    return request<IntakeOverviewDto>("/api/v1/intake");
}
