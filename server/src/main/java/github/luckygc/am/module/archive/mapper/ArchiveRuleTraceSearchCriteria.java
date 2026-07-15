package github.luckygc.am.module.archive.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;

public record ArchiveRuleTraceSearchCriteria(
        @Nullable Long schemeVersionId,
        @Nullable String triggerCode,
        @Nullable String objectTypeCode,
        @Nullable Long objectId,
        @Nullable String ruleType,
        boolean allData,
        Long userId,
        List<ArchiveRuleTraceTargetScope> itemScopes,
        List<ArchiveRuleTraceTargetScope> volumeScopes,
        ArchiveRuleTracePageWindow page) {

    public ArchiveRuleTraceSearchCriteria {
        itemScopes = List.copyOf(itemScopes);
        volumeScopes = List.copyOf(volumeScopes);
    }

    public record ArchiveRuleTraceTargetScope(
            String categoryCode, String tableName, List<ArchiveDataScopeSqlGroup> groups) {
        public ArchiveRuleTraceTargetScope {
            groups = List.copyOf(groups);
        }
    }

    public record ArchiveRuleTracePageWindow(
            boolean previous,
            @Nullable LocalDateTime cursorCreatedAt,
            @Nullable Long cursorId,
            int rowLimit) {}
}
