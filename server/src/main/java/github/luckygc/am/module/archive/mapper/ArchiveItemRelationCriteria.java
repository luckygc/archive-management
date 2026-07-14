package github.luckygc.am.module.archive.mapper;

import java.util.List;

import org.jspecify.annotations.Nullable;

public record ArchiveItemRelationCriteria(
        boolean allData, List<ArchiveItemRelationTargetScope> targetScopes) {

    public ArchiveItemRelationCriteria {
        targetScopes = List.copyOf(targetScopes);
    }

    public record ArchiveItemRelationTargetScope(
            String categoryCode, String tableName, List<ArchiveDataScopeSqlGroup> groups) {

        public ArchiveItemRelationTargetScope {
            groups = List.copyOf(groups);
        }
    }

    public record ArchiveItemRelationPageWindow(
            boolean previous, @Nullable Long cursorId, int rowLimit) {}
}
