package github.luckygc.am.module.archive.mapper;

import java.util.regex.Pattern;

public record ArchiveDynamicItemSource(String tableName, boolean deleted) {

    private static final int POSTGRESQL_IDENTIFIER_LIMIT = 63;
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[a-z][a-z0-9_]*");

    public ArchiveDynamicItemSource {
        if (tableName == null
                || tableName.length() > POSTGRESQL_IDENTIFIER_LIMIT
                || !IDENTIFIER_PATTERN.matcher(tableName).matches()) {
            throw new IllegalArgumentException("动态表名非法");
        }
    }
}
