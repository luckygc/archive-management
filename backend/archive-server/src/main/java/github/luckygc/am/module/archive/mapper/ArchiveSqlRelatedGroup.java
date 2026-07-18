package github.luckygc.am.module.archive.mapper;

import java.util.List;

import github.luckygc.am.module.archive.item.ArchiveItemRelationDirection;

public record ArchiveSqlRelatedGroup(
        String tableName,
        String categoryCode,
        ArchiveItemRelationDirection direction,
        List<ArchiveSqlCondition> conditions) {

    public String directionName() {
        return direction.name();
    }
}
