package github.luckygc.am.module.archive.mapper;

import org.jspecify.annotations.Nullable;

public record ArchiveSqlAssignment(String columnName, @Nullable Object value) {}
