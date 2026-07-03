package github.luckygc.am.module.archive.mapper;

import java.util.List;

public record ArchiveDataScopeSqlGroup(
        List<String> fondsCodes,
        List<Long> securityLevelIds,
        List<Long> retentionPeriodIds,
        List<Long> orgUnitIds,
        List<ArchiveSqlCondition> conditions) {

    public ArchiveDataScopeSqlGroup(
            List<String> fondsCodes,
            List<Long> securityLevelIds,
            List<ArchiveSqlCondition> conditions) {
        this(fondsCodes, securityLevelIds, List.of(), List.of(), conditions);
    }
}
