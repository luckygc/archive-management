package github.luckygc.am.module.archive.item.service;

import java.math.BigDecimal;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.support.JdbcUtils;

public record ArchiveWorkspaceCategorySummary(
        long archiveItemCount, long draftCount, long lockedCount, long electronicFileCount) {

    public static ArchiveWorkspaceCategorySummary empty() {
        return new ArchiveWorkspaceCategorySummary(0, 0, 0, 0);
    }

    public static ArchiveWorkspaceCategorySummary fromMapperRow(Map<String, @Nullable Object> row) {
        return new ArchiveWorkspaceCategorySummary(
                aggregateCount(row, "archiveItemCount"),
                aggregateCount(row, "draftCount"),
                aggregateCount(row, "lockedCount"),
                aggregateCount(row, "electronicFileCount"));
    }

    private static long aggregateCount(Map<String, @Nullable Object> row, String key) {
        Object value = value(row, key);
        if (value == null) {
            return 0;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("工作台摘要字段不是数值：" + key);
        }
        try {
            long count = new BigDecimal(number.toString()).longValueExact();
            if (count < 0) {
                throw new IllegalStateException("工作台摘要字段不能为负数：" + key);
            }
            return count;
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalStateException("工作台摘要字段超出 long 范围：" + key, exception);
        }
    }

    private static @Nullable Object value(Map<String, @Nullable Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        return row.get(JdbcUtils.convertPropertyNameToUnderscoreName(key));
    }
}
