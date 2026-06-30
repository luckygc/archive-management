package github.luckygc.am.module.archive.mapper;

import java.util.List;

public record ArchiveDataScopeSqlGroup(
        List<String> fondsCodes,
        List<Long> securityLevelIds,
        List<ArchiveSqlCondition> conditions) {}
