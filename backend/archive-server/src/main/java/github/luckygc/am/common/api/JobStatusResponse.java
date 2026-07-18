package github.luckygc.am.common.api;

import java.time.LocalDateTime;
import java.util.Map;

public record JobStatusResponse(
        String jobId,
        String status,
        int progress,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Map<String, Object> result,
        String errorCode,
        String errorMessage) {}
