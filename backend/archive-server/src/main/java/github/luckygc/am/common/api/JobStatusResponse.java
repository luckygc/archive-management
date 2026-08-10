package github.luckygc.am.common.api;

import java.time.LocalDateTime;
import java.util.Map;

import org.jspecify.annotations.Nullable;

public record JobStatusResponse(
        Long jobId,
        String status,
        int progress,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        @Nullable Map<String, Object> result,
        @Nullable String errorCode,
        @Nullable String errorMessage) {}
