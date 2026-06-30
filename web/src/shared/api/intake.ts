import { httpClient } from "@archive-management/frontend-core/api";

import type { IntakeOverviewDto } from "@/shared/types/intake";

export function getIntakeOverview() {
    return httpClient.get<IntakeOverviewDto>("/api/v1/intake");
}
