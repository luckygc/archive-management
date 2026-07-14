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
import java.util.regex.Pattern;

import jakarta.data.page.PageRequest;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.item.service.ArchiveItemLineTableService.ArchiveItemLineFieldDto;
import github.luckygc.am.module.archive.mapper.ArchiveItemLineRowCommands.ArchiveItemLineRowDeleteCommand;
import github.luckygc.am.module.archive.mapper.ArchiveItemLineRowCommands.ArchiveItemLineRowInsertCommand;
import github.luckygc.am.module.archive.mapper.ArchiveItemLineRowCommands.ArchiveItemLineRowLookup;
import github.luckygc.am.module.archive.mapper.ArchiveItemLineRowCommands.ArchiveItemLineRowPageQuery;
import github.luckygc.am.module.archive.mapper.ArchiveItemLineRowCommands.ArchiveItemLineRowUpdateCommand;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.mapper.ArchiveSqlAssignment;
import github.luckygc.am.module.archive.metadata.ArchiveFieldType;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@Service
public class ArchiveItemLineRowService {

    private static final int POSTGRESQL_IDENTIFIER_LIMIT = 63;
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[a-z][a-z0-9_]*");
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ArchiveMapper archiveMapper;
    private final ArchiveItemReadService archiveItemReadService;
    private final AuthorizationPermissionService permissionService;
    private final ArchiveItemFieldValueConverter fieldValueConverter;

    public ArchiveItemLineRowService(
            ArchiveMapper archiveMapper,
            ArchiveItemReadService archiveItemReadService,
            AuthorizationPermissionService permissionService,
            ArchiveItemFieldValueConverter fieldValueConverter) {
        this.archiveMapper = archiveMapper;
        this.archiveItemReadService = archiveItemReadService;
        this.permissionService = permissionService;
        this.fieldValueConverter = fieldValueConverter;
    }

    public CursorPageResponse<ArchiveItemLineRowResponse> listRows(
            Long archiveItemId, Long lineTableId, PageRequest pageRequest, Long userId) {
        requirePositiveId(archiveItemId, "archiveItem", "档案条目 ID 不合法");
        requirePositiveId(lineTableId, "lineTable", "明细表 ID 不合法");
        permissionService.requirePermission(userId, AuthorizationPermissionCode.ARCHIVE_ITEM_READ);
        archiveItemReadService.assertItemInDataScope(archiveItemId, userId);
        LineTableDefinition table = loadBuiltTable(archiveItemId, lineTableId);
        ArchiveItemLineRowPageQuery query = pageQuery(table, archiveItemId, pageRequest);
        List<Map<String, Object>> rows = archiveMapper.listItemLineRows(query);
        return toCursorPage(table, rows, pageRequest);
    }

    public List<ArchiveItemLineTableDefinitionResponse> listLineTables(
            Long archiveItemId, Long userId) {
        requirePositiveId(archiveItemId, "archiveItem", "档案条目 ID 不合法");
        permissionService.requirePermission(userId, AuthorizationPermissionCode.ARCHIVE_ITEM_READ);
        archiveItemReadService.assertItemInDataScope(archiveItemId, userId);
        var item = archiveItemReadService.getItem(archiveItemId);
        var category = archiveItemReadService.getCategoryByCode(item.categoryCode());
        List<ArchiveItemLineTableDefinitionResponse> definitions = new ArrayList<>();
        for (Map<String, Object> tableRow : archiveMapper.listItemLineTables(category.id())) {
            if (!bool(tableRow, "enabled")) {
                continue;
            }
            String tableName = string(tableRow, "physicalTableName");
            validateIdentifier(tableName, "动态明细表名非法");
            if (archiveMapper.tableExists(tableName) == 0) {
                continue;
            }
            Long lineTableId = number(tableRow, "id").longValue();
            List<ArchiveItemLineFieldDto> fields =
                    archiveMapper.listItemLineFields(lineTableId).stream()
                            .map(this::toField)
                            .filter(ArchiveItemLineFieldDto::enabled)
                            .toList();
            for (ArchiveItemLineFieldDto field : fields) {
                validateIdentifier(field.columnName(), "字段列名非法");
            }
            definitions.add(
                    new ArchiveItemLineTableDefinitionResponse(
                            lineTableId,
                            string(tableRow, "tableCode"),
                            string(tableRow, "tableName"),
                            number(tableRow, "sortOrder").intValue(),
                            fields.stream()
                                    .map(
                                            field ->
                                                    new ArchiveItemLineFieldDefinitionResponse(
                                                            field.id(),
                                                            field.fieldCode(),
                                                            field.fieldName(),
                                                            field.fieldType(),
                                                            field.sortOrder()))
                                    .toList()));
        }
        return List.copyOf(definitions);
    }

    @Transactional
    public ArchiveItemLineRowResponse createRow(
            Long archiveItemId,
            Long lineTableId,
            @Nullable CreateArchiveItemLineRowRequest request,
            Long userId) {
        requireWriteBoundary(archiveItemId, lineTableId, userId);
        if (request == null) {
            throw badRequest("请求体不能为空");
        }
        int lineOrder = requireLineOrder(request.lineOrder());
        LineTableDefinition table = loadBuiltTable(archiveItemId, lineTableId);
        Map<String, @Nullable Object> values = normalizeValues(request.values());
        List<ArchiveSqlAssignment> assignments = assignments(table.fields(), values);
        Long rowId =
                archiveMapper.insertItemLineRow(
                        new ArchiveItemLineRowInsertCommand(
                                table.tableName(), archiveItemId, lineOrder, assignments));
        return loadRow(table, archiveItemId, rowId);
    }

    @Transactional
    public ArchiveItemLineRowResponse patchRow(
            Long archiveItemId,
            Long lineTableId,
            Long rowId,
            @Nullable PatchArchiveItemLineRowRequest request,
            Long userId) {
        requirePositiveId(rowId, "row", "明细行 ID 不合法");
        requireWriteBoundary(archiveItemId, lineTableId, userId);
        if (request == null) {
            throw badRequest("请求体不能为空");
        }
        LineTableDefinition table = loadBuiltTable(archiveItemId, lineTableId);
        Map<String, Object> current = loadRowMap(table, archiveItemId, rowId);
        @Nullable Integer lineOrder =
                request.lineOrderPresent() ? requireLineOrder(request.lineOrder()) : null;
        Map<String, @Nullable Object> values =
                request.valuesPresent() ? normalizeValues(request.values()) : Map.of();
        List<ArchiveSqlAssignment> assignments = assignments(table.fields(), values);
        if (request.lineOrderPresent() || !assignments.isEmpty()) {
            int updated =
                    archiveMapper.updateItemLineRow(
                            new ArchiveItemLineRowUpdateCommand(
                                    table.tableName(),
                                    archiveItemId,
                                    rowId,
                                    request.lineOrderPresent(),
                                    lineOrder,
                                    assignments));
            if (updated == 0) {
                throw notFound();
            }
            return loadRow(table, archiveItemId, rowId);
        }
        return toResponse(table, current);
    }

    @Transactional
    public void deleteRow(Long archiveItemId, Long lineTableId, Long rowId, Long userId) {
        requirePositiveId(rowId, "row", "明细行 ID 不合法");
        requireWriteBoundary(archiveItemId, lineTableId, userId);
        LineTableDefinition table = loadBuiltTable(archiveItemId, lineTableId);
        loadRowMap(table, archiveItemId, rowId);
        if (archiveMapper.deleteItemLineRow(
                        new ArchiveItemLineRowDeleteCommand(
                                table.tableName(), archiveItemId, rowId, userId))
                == 0) {
            throw notFound();
        }
    }

    private void requireWriteBoundary(Long archiveItemId, Long lineTableId, Long userId) {
        requirePositiveId(archiveItemId, "archiveItem", "档案条目 ID 不合法");
        requirePositiveId(lineTableId, "lineTable", "明细表 ID 不合法");
        permissionService.requirePermission(
                userId, AuthorizationPermissionCode.ARCHIVE_ITEM_UPDATE);
        archiveItemReadService.assertItemInDataScope(archiveItemId, userId);
        archiveItemReadService.ensureItemEditable(archiveItemId);
    }

    private LineTableDefinition loadBuiltTable(Long archiveItemId, Long lineTableId) {
        var item = archiveItemReadService.getItem(archiveItemId);
        Map<String, Object> tableRow = archiveMapper.getItemLineTable(lineTableId);
        if (tableRow == null
                || !item.categoryCode().equals(string(tableRow, "categoryCode"))
                || !bool(tableRow, "enabled")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "明细表不存在");
        }
        String tableName = string(tableRow, "physicalTableName");
        validateIdentifier(tableName, "动态明细表名非法");
        List<ArchiveItemLineFieldDto> fields =
                archiveMapper.listItemLineFields(lineTableId).stream()
                        .map(this::toField)
                        .filter(ArchiveItemLineFieldDto::enabled)
                        .toList();
        for (ArchiveItemLineFieldDto field : fields) {
            validateIdentifier(field.columnName(), "字段列名非法");
        }
        if (archiveMapper.tableExists(tableName) == 0) {
            throw badRequest("明细表尚未构建");
        }
        return new LineTableDefinition(lineTableId, tableName, fields);
    }

    private ArchiveItemLineRowPageQuery pageQuery(
            LineTableDefinition table, Long archiveItemId, PageRequest pageRequest) {
        @Nullable Integer cursorLineOrder = null;
        @Nullable Long cursorId = null;
        if (pageRequest.cursor().isPresent()) {
            List<?> elements = pageRequest.cursor().orElseThrow().elements();
            if (elements.size() != 2
                    || !(elements.get(0) instanceof Number lineOrder)
                    || !(elements.get(1) instanceof Number id)) {
                throw badRequest("分页 cursor 无效", "cursor", "明细行 cursor 必须包含行顺序和 ID");
            }
            cursorLineOrder = lineOrder.intValue();
            cursorId = id.longValue();
        }
        return new ArchiveItemLineRowPageQuery(
                table.tableName(),
                archiveItemId,
                selectColumns(table),
                pageRequest.mode() == PageRequest.Mode.CURSOR_PREVIOUS,
                cursorLineOrder,
                cursorId,
                pageRequest.size() + 1);
    }

    private CursorPageResponse<ArchiveItemLineRowResponse> toCursorPage(
            LineTableDefinition table, List<Map<String, Object>> rows, PageRequest pageRequest) {
        int limit = pageRequest.size();
        boolean hasMore = rows.size() > limit;
        List<Map<String, Object>> pageRows =
                new ArrayList<>(hasMore ? rows.subList(0, limit) : rows);
        boolean previousQuery = pageRequest.mode() == PageRequest.Mode.CURSOR_PREVIOUS;
        if (previousQuery) {
            pageRows = pageRows.reversed();
        }
        boolean hasPrevious = previousQuery ? hasMore : pageRequest.cursor().isPresent();
        boolean hasNext = previousQuery ? pageRequest.cursor().isPresent() : hasMore;
        List<?> self = pageRows.isEmpty() ? null : cursorValues(pageRows.getFirst());
        List<?> prev =
                hasPrevious && !pageRows.isEmpty() ? cursorValues(pageRows.getFirst()) : null;
        List<?> next = hasNext && !pageRows.isEmpty() ? cursorValues(pageRows.getLast()) : null;
        return CursorPageResponse.withCursorValues(
                pageRows.stream().map(row -> toResponse(table, row)).toList(),
                limit,
                self,
                prev,
                next,
                null,
                null);
    }

    private List<?> cursorValues(Map<String, Object> row) {
        return List.of(number(row, "lineOrder").intValue(), number(row, "id").longValue());
    }

    private List<ArchiveSqlAssignment> assignments(
            List<ArchiveItemLineFieldDto> fields, Map<String, @Nullable Object> values) {
        Map<String, ArchiveItemLineFieldDto> fieldsByCode =
                fields.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        ArchiveItemLineFieldDto::fieldCode, field -> field));
        for (String fieldCode : values.keySet()) {
            if (!fieldsByCode.containsKey(fieldCode)) {
                throw badRequest("字段不存在：" + fieldCode, "values." + fieldCode, "字段不存在");
            }
        }
        List<ArchiveItemLineFieldDto> requestedFields =
                fields.stream().filter(field -> values.containsKey(field.fieldCode())).toList();
        Map<String, @Nullable Object> converted =
                fieldValueConverter.convertLineFields(requestedFields, values, "values");
        return requestedFields.stream()
                .map(
                        field ->
                                new ArchiveSqlAssignment(
                                        field.columnName(), converted.get(field.fieldCode())))
                .toList();
    }

    private ArchiveItemLineRowResponse loadRow(
            LineTableDefinition table, Long archiveItemId, Long rowId) {
        return toResponse(table, loadRowMap(table, archiveItemId, rowId));
    }

    private Map<String, Object> loadRowMap(
            LineTableDefinition table, Long archiveItemId, Long rowId) {
        Map<String, Object> row =
                archiveMapper.getItemLineRow(
                        new ArchiveItemLineRowLookup(
                                table.tableName(), archiveItemId, rowId, selectColumns(table)));
        if (row == null) {
            throw notFound();
        }
        return row;
    }

    private ArchiveItemLineRowResponse toResponse(
            LineTableDefinition table, Map<String, Object> row) {
        Map<String, @Nullable Object> values = new LinkedHashMap<>();
        for (ArchiveItemLineFieldDto field : table.fields()) {
            values.put(
                    field.fieldCode(),
                    normalizeReadValue(field.fieldType(), value(row, field.columnName())));
        }
        return new ArchiveItemLineRowResponse(
                number(row, "id").longValue(),
                number(row, "itemId").longValue(),
                table.id(),
                number(row, "lineOrder").intValue(),
                values);
    }

    private @Nullable Object normalizeReadValue(
            ArchiveFieldType fieldType, @Nullable Object value) {
        if (value == null) {
            return null;
        }
        return switch (fieldType) {
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
                if (value instanceof LocalDateTime dateTime) {
                    yield dateTime.format(DATE_TIME_FORMATTER);
                }
                yield value;
            }
            default -> value;
        };
    }

    private List<String> selectColumns(LineTableDefinition table) {
        return table.fields().stream().map(ArchiveItemLineFieldDto::columnName).toList();
    }

    private ArchiveItemLineFieldDto toField(Map<String, Object> row) {
        return new ArchiveItemLineFieldDto(
                number(row, "id").longValue(),
                number(row, "lineTableId").longValue(),
                string(row, "fieldCode"),
                string(row, "fieldName"),
                ArchiveFieldType.fromValue(string(row, "fieldType")),
                string(row, "columnName"),
                bool(row, "exactSearchable"),
                number(row, "sortOrder").intValue(),
                bool(row, "enabled"));
    }

    private int requireLineOrder(@Nullable Integer lineOrder) {
        if (lineOrder == null || lineOrder < 0) {
            throw badRequest("行顺序不合法", "lineOrder", "行顺序必须为非负整数");
        }
        return lineOrder;
    }

    private void requirePositiveId(@Nullable Long value, String field, String message) {
        if (value == null || value <= 0) {
            throw badRequest(message, field, "必须为正数");
        }
    }

    private Map<String, @Nullable Object> normalizeValues(
            @Nullable Map<String, @Nullable Object> values) {
        return values == null ? Map.of() : values;
    }

    private void validateIdentifier(@Nullable String value, String message) {
        if (StringUtils.isBlank(value)
                || value.length() > POSTGRESQL_IDENTIFIER_LIMIT
                || !IDENTIFIER_PATTERN.matcher(value).matches()) {
            throw badRequest(message);
        }
    }

    private Number number(Map<String, ?> row, String key) {
        Object value = value(row, key);
        if (value instanceof Number number) {
            return number;
        }
        throw new IllegalStateException("缺少数值字段：" + key);
    }

    private boolean bool(Map<String, ?> row, String key) {
        Object value = value(row, key);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private String string(Map<String, ?> row, String key) {
        Object value = value(row, key);
        return value == null ? null : value.toString();
    }

    private @Nullable Object value(Map<String, ?> row, String key) {
        return row.containsKey(key)
                ? row.get(key)
                : row.get(JdbcUtils.convertPropertyNameToUnderscoreName(key));
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "明细行不存在");
    }

    private BadRequestException badRequest(String message) {
        return new BadRequestException(message);
    }

    private BadRequestException badRequest(String message, String field, String description) {
        return new BadRequestException(message, field, description);
    }

    private record LineTableDefinition(
            Long id, String tableName, List<ArchiveItemLineFieldDto> fields) {}

    public record CreateArchiveItemLineRowRequest(
            @Nullable Integer lineOrder, @Nullable Map<String, @Nullable Object> values) {}

    public record PatchArchiveItemLineRowRequest(
            boolean lineOrderPresent,
            @Nullable Integer lineOrder,
            boolean valuesPresent,
            Map<String, @Nullable Object> values) {}

    public record ArchiveItemLineRowResponse(
            Long id,
            Long archiveItemId,
            Long lineTableId,
            int lineOrder,
            Map<String, @Nullable Object> values) {}

    public record ArchiveItemLineTableDefinitionResponse(
            Long id,
            String tableCode,
            String tableName,
            int sortOrder,
            List<ArchiveItemLineFieldDefinitionResponse> fields) {}

    public record ArchiveItemLineFieldDefinitionResponse(
            Long id,
            String fieldCode,
            String fieldName,
            ArchiveFieldType fieldType,
            int sortOrder) {}
}
