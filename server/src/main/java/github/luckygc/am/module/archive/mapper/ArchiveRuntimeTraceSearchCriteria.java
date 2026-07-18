package github.luckygc.am.module.archive.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;

public record ArchiveRuntimeTraceSearchCriteria(
        @Nullable Long schemeVersionId,
        @Nullable String triggerPoint,
        @Nullable String objectTypeCode,
        @Nullable Long objectId,
        @Nullable String definitionKind,
        boolean allData,
        Long userId,
        List<ArchiveRuntimeTraceTargetScope> itemScopes,
        List<ArchiveRuntimeTraceTargetScope> volumeScopes,
        ArchiveRuntimeTracePageWindow page) {

    public ArchiveRuntimeTraceSearchCriteria {
        itemScopes = List.copyOf(itemScopes);
        volumeScopes = List.copyOf(volumeScopes);
    }

    public record ArchiveRuntimeTraceTargetScope(
            String categoryCode, String tableName, List<ArchiveDataScopeSqlGroup> groups) {
        public ArchiveRuntimeTraceTargetScope {
            groups = List.copyOf(groups);
        }
    }

    public record ArchiveRuntimeTracePageWindow(
            boolean previous,
            @Nullable LocalDateTime cursorCreatedAt,
            @Nullable Long cursorId,
            int rowLimit) {}
}
