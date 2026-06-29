package github.luckygc.am.module.archive.item.service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.api.OffsetPageResponse;
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;

@Service
public class ArchiveItemAuditQueryService {

    private static final int DEFAULT_PAGE_LIMIT = 100;
    private static final int MAX_PAGE_LIMIT = 1000;

    private final ArchiveMapper archiveMapper;

    public ArchiveItemAuditQueryService(ArchiveMapper archiveMapper) {
        this.archiveMapper = archiveMapper;
    }

    @Transactional(readOnly = true)
    public OffsetPageResponse<ArchiveItemAuditResponse> listAudits(
            @Nullable ArchiveItemAuditQuery query) {
        ArchiveItemAuditQuery effectiveQuery =
                query == null ? ArchiveItemAuditQuery.empty() : query;
        long offset = pageOffset(effectiveQuery.offset());
        int limit = pageLimit(effectiveQuery.limit());
        String fondsCode = StringUtils.trimToNull(effectiveQuery.fondsCode());
        String categoryCode = StringUtils.trimToNull(effectiveQuery.categoryCode());
        String operationType =
                StringUtils.upperCase(StringUtils.trimToNull(effectiveQuery.operationType()));
        long total =
                archiveMapper.countArchiveItemAudits(
                        effectiveQuery.archiveItemId(),
                        fondsCode,
                        categoryCode,
                        operationType,
                        effectiveQuery.operatedAfter(),
                        effectiveQuery.operatedBefore());
        List<ArchiveItemAuditResponse> items =
                archiveMapper
                        .listArchiveItemAudits(
                                effectiveQuery.archiveItemId(),
                                fondsCode,
                                categoryCode,
                                operationType,
                                effectiveQuery.operatedAfter(),
                                effectiveQuery.operatedBefore(),
                                limit,
                                offset)
                        .stream()
                        .map(this::toResponse)
                        .toList();
        return new OffsetPageResponse<>(items, limit, offset, total);
    }

    private int pageLimit(@Nullable Integer limit) {
        if (limit == null) {
            return DEFAULT_PAGE_LIMIT;
        }
        if (limit <= 0) {
            throw new BadRequestException("分页参数不合法", "limit", "limit 必须大于 0");
        }
        if (limit > MAX_PAGE_LIMIT) {
            throw new BadRequestException("分页参数不合法", "limit", "limit 不能大于 1000");
        }
        return limit;
    }

    private long pageOffset(@Nullable Long offset) {
        if (offset == null) {
            return 0L;
        }
        if (offset < 0) {
            throw new BadRequestException("分页参数不合法", "offset", "offset 不能小于 0");
        }
        return offset;
    }

    private ArchiveItemAuditResponse toResponse(Map<String, Object> row) {
        return new ArchiveItemAuditResponse(
                number(row, "id").longValue(),
                string(row, "sourceTableName"),
                number(row, "sourceItemId").longValue(),
                longOrNull(row, "archiveItemId"),
                stringOrNull(row, "fondsCode"),
                stringOrNull(row, "categoryCode"),
                string(row, "operationType"),
                stringOrNull(row, "operationReason"),
                longOrNull(row, "operatedBy"),
                dateTime(row, "operatedAt"));
    }

    private Number number(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value instanceof Number number) {
            return number;
        }
        throw new IllegalStateException("缺少数值字段：" + key);
    }

    private String string(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value instanceof String string) {
            return string;
        }
        throw new IllegalStateException("缺少文本字段：" + key);
    }

    private @Nullable String stringOrNull(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value instanceof String string ? string : null;
    }

    private @Nullable Long longOrNull(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value instanceof Number number ? number.longValue() : null;
    }

    private LocalDateTime dateTime(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        throw new IllegalStateException("缺少时间字段：" + key);
    }

    private @Nullable Object value(Map<String, Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        return row.get(JdbcUtils.convertPropertyNameToUnderscoreName(key));
    }

    public record ArchiveItemAuditQuery(
            @Nullable Long archiveItemId,
            @Nullable String fondsCode,
            @Nullable String categoryCode,
            @Nullable String operationType,
            @Nullable LocalDateTime operatedAfter,
            @Nullable LocalDateTime operatedBefore,
            @Nullable Integer limit,
            @Nullable Long offset) {

        private static ArchiveItemAuditQuery empty() {
            return new ArchiveItemAuditQuery(null, null, null, null, null, null, null, null);
        }
    }

    public record ArchiveItemAuditResponse(
            Long id,
            String sourceTableName,
            Long sourceItemId,
            @Nullable Long archiveItemId,
            @Nullable String fondsCode,
            @Nullable String categoryCode,
            String operationType,
            @Nullable String operationReason,
            @Nullable Long operatedBy,
            LocalDateTime operatedAt) {}
}
