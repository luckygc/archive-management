package github.luckygc.am.module.archive.item.service;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.data.page.CursoredPage;
import jakarta.data.page.PageRequest;
import jakarta.data.page.PageRequest.Cursor;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.stereotype.Service;

import github.luckygc.am.common.api.KeysetCursoredPageRecord;
import github.luckygc.am.module.archive.mapper.ArchiveDataScopeSqlGroup;
import github.luckygc.am.module.archive.mapper.ArchiveDynamicItemCriteria;
import github.luckygc.am.module.archive.mapper.ArchiveDynamicItemPageWindow;
import github.luckygc.am.module.archive.mapper.ArchiveDynamicItemPageWindow.CursorComparison;
import github.luckygc.am.module.archive.mapper.ArchiveDynamicItemPageWindow.CursorPredicate;
import github.luckygc.am.module.archive.mapper.ArchiveDynamicItemProjection;
import github.luckygc.am.module.archive.mapper.ArchiveDynamicItemSource;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.mapper.ArchiveSqlCondition;
import github.luckygc.am.module.archive.mapper.ArchiveSqlOrder;
import github.luckygc.am.module.archive.mapper.ArchiveSqlOrder.Direction;
import github.luckygc.am.module.archive.mapper.ArchiveSqlRelatedGroup;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFieldDto;

@Service
class ArchiveItemCursorPageAssembler {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final ArchiveMapper archiveMapper;

    ArchiveItemCursorPageAssembler(ArchiveMapper archiveMapper) {
        this.archiveMapper = archiveMapper;
    }

    CursoredPage<Map<String, @Nullable Object>> queryDynamicItemPage(
            PageRequest pageRequest,
            String tableName,
            List<ArchiveFieldDto> visibleFields,
            boolean deleted,
            @Nullable String requestedFondsCode,
            List<ArchiveDataScopeSqlGroup> dataScopeGroups,
            List<ArchiveSqlCondition> conditions,
            List<ArchiveSqlRelatedGroup> relatedGroups,
            @Nullable String keyword,
            List<ArchiveSqlOrder> orderBy,
            @Nullable Cursor cursor) {
        int limit = pageRequest.size();
        List<ArchiveSqlOrder> queryOrderBy =
                isPreviousCursor(pageRequest) ? invert(orderBy) : orderBy;
        ArchiveDynamicItemSource source = new ArchiveDynamicItemSource(tableName, deleted);
        ArchiveDynamicItemProjection projection =
                new ArchiveDynamicItemProjection(projectionFields(visibleFields));
        ArchiveDynamicItemCriteria criteria =
                new ArchiveDynamicItemCriteria(
                        requestedFondsCode, dataScopeGroups, conditions, relatedGroups, keyword);
        ArchiveDynamicItemPageWindow pageWindow =
                new ArchiveDynamicItemPageWindow(
                        queryOrderBy, cursorPredicates(queryOrderBy, cursor), limit + 1);
        List<Map<String, @Nullable Object>> rows =
                archiveMapper.listDynamicItems(source, projection, criteria, pageWindow);
        boolean hasMore = rows.size() > limit;
        List<Map<String, @Nullable Object>> rawPageItems = hasMore ? rows.subList(0, limit) : rows;
        if (isPreviousCursor(pageRequest)) {
            rawPageItems = rawPageItems.reversed();
        }
        List<Map<String, @Nullable Object>> pageItems =
                normalizeDynamicFieldValues(rawPageItems, visibleFields);
        List<PageRequest.Cursor> cursors =
                rawPageItems.stream().map(row -> rowCursor(orderBy, row)).toList();
        Long total =
                pageRequest.requestTotal()
                        ? (long) archiveMapper.countDynamicItems(source, criteria)
                        : null;
        return new KeysetCursoredPageRecord<>(
                pageRequest,
                pageItems,
                cursors,
                cursor != null && !rawPageItems.isEmpty(),
                hasMore && !rawPageItems.isEmpty(),
                total);
    }

    private List<ArchiveSqlOrder> invert(List<ArchiveSqlOrder> orders) {
        return orders.stream()
                .map(
                        order ->
                                new ArchiveSqlOrder(
                                        order.expression(),
                                        order.direction() == Direction.ASC
                                                ? Direction.DESC
                                                : Direction.ASC))
                .toList();
    }

    private List<CursorPredicate> cursorPredicates(
            List<ArchiveSqlOrder> orders, @Nullable Cursor cursor) {
        if (cursor == null) {
            return List.of();
        }
        List<?> values = cursor.elements();
        List<CursorPredicate> predicates = new ArrayList<>();
        for (int i = 0; i < orders.size(); i++) {
            List<CursorComparison> equalsComparisons = new ArrayList<>();
            for (int j = 0; j < i; j++) {
                equalsComparisons.add(
                        new CursorComparison(
                                orders.get(j).expression(),
                                orders.get(j).direction(),
                                values.get(j)));
            }
            predicates.add(
                    new CursorPredicate(
                            equalsComparisons,
                            new CursorComparison(
                                    orders.get(i).expression(),
                                    orders.get(i).direction(),
                                    values.get(i))));
        }
        return predicates;
    }

    private boolean isPreviousCursor(PageRequest pageRequest) {
        return switch (pageRequest.mode()) {
            case CURSOR_PREVIOUS -> true;
            case CURSOR_NEXT, OFFSET -> false;
        };
    }

    private PageRequest.Cursor rowCursor(
            List<ArchiveSqlOrder> orders, Map<String, @Nullable Object> row) {
        return PageRequest.Cursor.forKey(cursorRowValues(orders, row).toArray(Object[]::new));
    }

    private List<Object> cursorRowValues(
            List<ArchiveSqlOrder> orders, Map<String, @Nullable Object> row) {
        List<Object> values = new ArrayList<>();
        for (ArchiveSqlOrder order : orders) {
            values.add(cursorRowValue(row, order.expression()));
        }
        return values;
    }

    private @Nullable Object cursorRowValue(Map<String, @Nullable Object> row, String expression) {
        return switch (expression) {
            case "i.created_at" -> value(row, "createdAt");
            case "i.deleted_at" -> value(row, "deletedAt");
            case "i.id" -> longCursorValue(row, "id");
            case "i.archive_no" -> value(row, "archiveNo");
            case "i.archive_year" -> integerCursorValue(row, "archiveYear");
            case "i.fonds_code" -> value(row, "fondsCode");
            case "i.category_code" -> value(row, "categoryCode");
            case "i.electronic_status" -> value(row, "electronicStatus");
            default -> dynamicCursorRowValue(row, expression);
        };
    }

    private @Nullable Object dynamicCursorRowValue(
            Map<String, @Nullable Object> row, String expression) {
        if (!expression.startsWith("d.")) {
            throw new IllegalStateException("不支持的 cursor 排序字段：" + expression);
        }
        return value(row, expression.substring(2));
    }

    private Long longCursorValue(Map<String, @Nullable Object> row, String key) {
        Object value = value(row, key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }

    private Integer integerCursorValue(Map<String, @Nullable Object> row, String key) {
        Object value = value(row, key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(value.toString());
    }

    private List<String> projectionFields(List<ArchiveFieldDto> fields) {
        return fields.stream().map(ArchiveFieldDto::columnName).toList();
    }

    private List<Map<String, @Nullable Object>> normalizeDynamicFieldValues(
            List<Map<String, @Nullable Object>> rows, List<ArchiveFieldDto> fields) {
        if (rows.isEmpty()) {
            return rows;
        }
        return rows.stream()
                .map(
                        row -> {
                            Map<String, @Nullable Object> normalized = new LinkedHashMap<>(row);
                            for (ArchiveFieldDto field : fields) {
                                normalized.compute(
                                        field.columnName(),
                                        (_, value) -> normalizeDynamicFieldValue(field, value));
                            }
                            return normalized;
                        })
                .toList();
    }

    private @Nullable Object normalizeDynamicFieldValue(
            ArchiveFieldDto field, @Nullable Object value) {
        if (value == null) {
            return null;
        }
        return switch (field.fieldType()) {
            case DATE -> {
                if (value instanceof Date date) {
                    yield date.toLocalDate().toString();
                }
                if (value instanceof LocalDate localDate) {
                    yield localDate.toString();
                }
                yield value;
            }
            case DATETIME -> {
                if (value instanceof Timestamp timestamp) {
                    yield timestamp.toLocalDateTime().format(DATE_TIME_FORMATTER);
                }
                if (value instanceof LocalDateTime localDateTime) {
                    yield localDateTime.format(DATE_TIME_FORMATTER);
                }
                yield value;
            }
            default -> value;
        };
    }

    private @Nullable Object value(Map<String, @Nullable Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        return row.get(JdbcUtils.convertPropertyNameToUnderscoreName(key));
    }
}
