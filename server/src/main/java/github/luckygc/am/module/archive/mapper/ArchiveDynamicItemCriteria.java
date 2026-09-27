package github.luckygc.am.module.archive.mapper;

import java.util.List;

import org.jspecify.annotations.Nullable;

public record ArchiveDynamicItemCriteria(
        @Nullable String requestedFondsCode,
        @Nullable Long volumeId,
        List<ArchiveDataScopeSqlGroup> dataScopeGroups,
        List<ArchiveSqlCondition> conditions,
        List<ArchiveSqlRelatedGroup> relatedGroups,
        @Nullable String fullTextKeyword) {

    public ArchiveDynamicItemCriteria {
        dataScopeGroups = List.copyOf(dataScopeGroups);
        conditions = List.copyOf(conditions);
        relatedGroups = List.copyOf(relatedGroups);
    }
}
