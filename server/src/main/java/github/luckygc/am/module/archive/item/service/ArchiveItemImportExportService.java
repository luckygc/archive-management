package github.luckygc.am.module.archive.item.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.fesod.sheet.FesodSheet;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.api.CursorPageTokenContext;
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeResolutionTypes.ArchiveDataScopeFilter;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService;
import github.luckygc.am.module.archive.item.ArchiveItem;
import github.luckygc.am.module.archive.item.ArchiveItemAudit;
import github.luckygc.am.module.archive.item.repository.ArchiveItemAuditDataRepository;
import github.luckygc.am.module.archive.item.repository.ArchiveItemDataRepository;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataReferenceService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFieldDto;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;
import github.luckygc.am.module.storage.FileLinkTargetType;
import github.luckygc.am.module.storage.service.FileLinkService;
import github.luckygc.am.module.storage.service.StorageObjectService;
import github.luckygc.am.module.storage.service.StorageObjectService.StoreStorageObjectCommand;

@Service
public class ArchiveItemImportExportService {

    private static final String HEADER_FONDS_CODE = "全宗编码";
    private static final String HEADER_ARCHIVE_NO = "档号";
    private static final String HEADER_ARCHIVE_YEAR = "年度";
    private static final String HEADER_ELECTRONIC_STATUS = "电子状态";
    private static final int EXPORT_BATCH_LIMIT = 1000;
    private static final int EXPORT_MAX_ROWS = 5000;
    private static final Duration DOWNLOAD_LINK_TTL = Duration.ofMinutes(10);
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ArchiveMetadataService archiveMetadataService;
    private final ArchiveMetadataReferenceService archiveMetadataReferenceService;
    private final ArchiveCategoryService archiveCategoryService;
    private final ArchiveItemCommandService archiveItemRoutingService;
    private final ArchiveItemQueryService archiveItemQueryService;
    private final AuthorizationPermissionService permissionService;
    private final ArchiveDataScopeService dataScopeService;
    private final ArchiveItemDataRepository archiveItemRepository;
    private final ArchiveItemAuditDataRepository auditRepository;
    private final StorageObjectService storageObjectService;
    private final FileLinkService fileLinkService;
    private final Clock clock;

    public ArchiveItemImportExportService(
            ArchiveMetadataService archiveMetadataService,
            ArchiveMetadataReferenceService archiveMetadataReferenceService,
            ArchiveCategoryService archiveCategoryService,
            ArchiveItemCommandService archiveItemRoutingService,
            ArchiveItemQueryService archiveItemQueryService,
            AuthorizationPermissionService permissionService,
            ArchiveDataScopeService dataScopeService,
            ArchiveItemDataRepository archiveItemRepository,
            ArchiveItemAuditDataRepository auditRepository,
            StorageObjectService storageObjectService,
            FileLinkService fileLinkService,
            Clock clock) {
        this.archiveMetadataService = archiveMetadataService;
        this.archiveMetadataReferenceService = archiveMetadataReferenceService;
        this.archiveCategoryService = archiveCategoryService;
        this.archiveItemRoutingService = archiveItemRoutingService;
        this.archiveItemQueryService = archiveItemQueryService;
        this.permissionService = permissionService;
        this.dataScopeService = dataScopeService;
        this.archiveItemRepository = archiveItemRepository;
        this.auditRepository = auditRepository;
        this.storageObjectService = storageObjectService;
        this.fileLinkService = fileLinkService;
        this.clock = clock;
    }

    @Transactional
    public DownloadLinkCreated createImportTemplateDownloadLink(Long categoryId, Long userId) {
        requireAnyPermission(
                userId,
                AuthorizationPermissionCode.ARCHIVE_ITEM_CREATE,
                AuthorizationPermissionCode.ARCHIVE_ITEM_UPDATE);
        ensureCategoryInDataScope(categoryId, userId);
        return createDownloadLink(generateImportTemplate(categoryId), userId);
    }

    private ArchiveExcelFile generateImportTemplate(Long categoryId) {
        ArchiveCategoryDto category = archiveCategoryService.getCategory(categoryId);
        List<ArchiveFieldDto> fields =
                archiveMetadataService.listEnabledFields(categoryId, ArchiveLevel.ITEM);
        List<List<String>> head = importHead(fields);
        List<List<@Nullable Object>> rows = new ArrayList<>();
        rows.add(List.of("FONDS001", "A-2026-001", Year.now().getValue(), "DRAFT"));
        return new ArchiveExcelFile(
                "archive-import-template-" + category.categoryCode() + ".xlsx",
                writeExcel(head, rows, "导入模板"));
    }

    @Transactional
    public ArchiveImportResult importItems(Long categoryId, InputStream inputStream, Long userId) {
        ensureCategoryInDataScope(categoryId, userId);
        ArchiveCategoryDto category = archiveCategoryService.getCategory(categoryId);
        List<ArchiveFieldDto> fields =
                archiveMetadataService.listEnabledFields(categoryId, ArchiveLevel.ITEM);
        List<ArchiveImportRow> rows = parseImportRows(categoryId, inputStream, fields);
        List<ArchiveImportRowError> errors = validateImportRows(category, fields, rows, userId);
        if (!errors.isEmpty()) {
            return new ArchiveImportResult(0, errors);
        }
        int imported = 0;
        for (ArchiveImportRow row : rows) {
            if (row.existingItem() == null) {
                archiveItemRoutingService.createItem(row.createRequest(), userId);
            } else {
                archiveItemRoutingService.updateItem(
                        row.existingItem().getId(), row.updateRequest(), userId);
            }
            imported++;
        }
        return new ArchiveImportResult(imported, List.of());
    }

    @Transactional
    public DownloadLinkCreated createExportDownloadLink(
            ArchiveItemQueryService.@Nullable SearchArchiveItemsRequest request, Long userId) {
        requirePermission(userId, AuthorizationPermissionCode.ARCHIVE_EXPORT);
        ArchiveItemQueryService.SearchArchiveItemsRequest base =
                request == null
                        ? new ArchiveItemQueryService.SearchArchiveItemsRequest(
                                null, null, null, null, null, EXPORT_BATCH_LIMIT, null, null)
                        : request;
        List<ArchiveFieldDto> fields = List.of();
        List<Map<String, @Nullable Object>> exportedRows = new ArrayList<>();
        @Nullable String cursor = null;
        do {
            ArchiveItemQueryService.SearchArchiveItemsRequest pageRequest =
                    base.withPage(EXPORT_BATCH_LIMIT, cursor);
            ArchiveItemQueryService.ArchiveItemListDto page =
                    archiveItemQueryService.searchItems(pageRequest, userId);
            ArchiveItemQueryService.ArchiveItemListDto encodedPage =
                    page.encodeCursorTokens(new CursorPageTokenContext(""));
            fields = page.fields();
            exportedRows.addAll(page.items());
            cursor = encodedPage.next();
        } while (cursor != null && exportedRows.size() < EXPORT_MAX_ROWS);
        byte[] bytes = writeExcel(exportHead(fields), exportBody(fields, exportedRows), "导出结果");
        writeExportAudit(base, userId, exportedRows.size());
        return createDownloadLink(new ArchiveExcelFile("archive-export.xlsx", bytes), userId);
    }

    private DownloadLinkCreated createDownloadLink(ArchiveExcelFile file, Long userId) {
        LocalDateTime expiresAt = LocalDateTime.now(clock).plus(DOWNLOAD_LINK_TTL);
        var storageObject =
                storageObjectService.storeObject(
                        new StoreStorageObjectCommand(
                                file.filename(),
                                XLSX_CONTENT_TYPE,
                                file.bytes().length,
                                new ByteArrayInputStream(file.bytes()),
                                expiresAt),
                        userId);
        var link =
                fileLinkService.createUserLinkUntil(
                        FileLinkTargetType.STORAGE_OBJECT,
                        null,
                        storageObject.id(),
                        expiresAt,
                        userId);
        return new DownloadLinkCreated(link.code(), link.expiresAt());
    }

    private void requirePermission(Long userId, AuthorizationPermissionCode permissionCode) {
        userId = AuthenticatedUsers.requireResolvedUserId(userId);
        if (!permissionService.hasPermission(userId, permissionCode.code())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "权限不足");
        }
    }

    private void requireAnyPermission(
            Long userId, AuthorizationPermissionCode first, AuthorizationPermissionCode second) {
        userId = AuthenticatedUsers.requireResolvedUserId(userId);
        if (!permissionService.hasPermission(userId, first.code())
                && !permissionService.hasPermission(userId, second.code())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "权限不足");
        }
    }

    private void ensureCategoryInDataScope(Long categoryId, Long userId) {
        userId = AuthenticatedUsers.requireResolvedUserId(userId);
        ArchiveDataScopeFilter filter = dataScopeService.buildItemFilter(userId, categoryId, null);
        if (filter.empty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "数据范围不足");
        }
    }

    private List<List<String>> importHead(List<ArchiveFieldDto> fields) {
        List<List<String>> head = new ArrayList<>();
        head.add(List.of(HEADER_FONDS_CODE));
        head.add(List.of(HEADER_ARCHIVE_NO));
        head.add(List.of(HEADER_ARCHIVE_YEAR));
        head.add(List.of(HEADER_ELECTRONIC_STATUS));
        fields.stream()
                .sorted(
                        java.util.Comparator.comparingInt(ArchiveFieldDto::editSortOrder)
                                .thenComparing(ArchiveFieldDto::id))
                .map(ArchiveFieldDto::fieldName)
                .map(List::of)
                .forEach(head::add);
        return head;
    }

    private List<List<String>> exportHead(List<ArchiveFieldDto> fields) {
        List<List<String>> head = new ArrayList<>();
        head.add(List.of("ID"));
        head.add(List.of(HEADER_FONDS_CODE));
        head.add(List.of("全宗名称"));
        head.add(List.of("分类编码"));
        head.add(List.of("分类名称"));
        head.add(List.of(HEADER_ARCHIVE_NO));
        head.add(List.of(HEADER_ARCHIVE_YEAR));
        fields.stream().map(ArchiveFieldDto::fieldName).map(List::of).forEach(head::add);
        return head;
    }

    private List<List<@Nullable Object>> exportBody(
            List<ArchiveFieldDto> fields, List<Map<String, @Nullable Object>> rows) {
        List<List<@Nullable Object>> body = new ArrayList<>();
        for (Map<String, @Nullable Object> row : rows) {
            List<@Nullable Object> values = new ArrayList<>();
            values.add(row.get("id"));
            values.add(row.get("fondsCode"));
            values.add(row.get("fondsName"));
            values.add(row.get("categoryCode"));
            values.add(row.get("categoryName"));
            values.add(row.get("archiveNo"));
            values.add(row.get("archiveYear"));
            for (ArchiveFieldDto field : fields) {
                values.add(row.get(field.fieldCode()));
            }
            body.add(values);
        }
        return body;
    }

    private List<ArchiveImportRow> parseImportRows(
            Long categoryId, InputStream inputStream, List<ArchiveFieldDto> fields) {
        List<Map<Integer, String>> rawRows =
                FesodSheet.read(inputStream)
                        .headRowNumber(0)
                        .sheet()
                        .<Map<Integer, String>>doReadSync();
        if (rawRows.isEmpty()) {
            return List.of();
        }
        Map<Integer, String> header = rawRows.getFirst();
        Map<String, Integer> indexes = headerIndexes(header);
        List<ArchiveImportRow> rows = new ArrayList<>();
        for (int i = 1; i < rawRows.size(); i++) {
            int rowNumber = i + 1;
            Map<Integer, String> rawRow = rawRows.get(i);
            if (rawRow.values().stream().allMatch(StringUtils::isBlank)) {
                continue;
            }
            List<ArchiveImportRowError> parseErrors = new ArrayList<>();
            @Nullable Integer archiveYear =
                    parseArchiveYear(
                            cell(rawRow, indexes.get(HEADER_ARCHIVE_YEAR)), rowNumber, parseErrors);
            Map<String, @Nullable Object> dynamicFields = new LinkedHashMap<>();
            for (ArchiveFieldDto field : fields) {
                dynamicFields.put(field.fieldCode(), cell(rawRow, indexes.get(field.fieldName())));
            }
            ArchiveItemCommandService.CreateArchiveItemRequest request =
                    new ArchiveItemCommandService.CreateArchiveItemRequest(
                            categoryId,
                            null,
                            cell(rawRow, indexes.get(HEADER_FONDS_CODE)),
                            cell(rawRow, indexes.get(HEADER_ARCHIVE_NO)),
                            archiveYear,
                            cell(rawRow, indexes.get(HEADER_ELECTRONIC_STATUS)),
                            null,
                            null,
                            null,
                            dynamicFields);
            rows.add(new ArchiveImportRow(rowNumber, request, null, parseErrors));
        }
        return rows;
    }

    private Map<String, Integer> headerIndexes(Map<Integer, String> header) {
        Map<String, Integer> indexes = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> entry : header.entrySet()) {
            String value = StringUtils.trimToNull(entry.getValue());
            if (value != null) {
                indexes.put(value, entry.getKey());
            }
        }
        return indexes;
    }

    private @Nullable String cell(Map<Integer, String> row, @Nullable Integer index) {
        return index == null ? null : StringUtils.trimToNull(row.get(index));
    }

    private @Nullable Integer parseArchiveYear(
            @Nullable String value, int rowNumber, List<ArchiveImportRowError> errors) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            errors.add(new ArchiveImportRowError(rowNumber, HEADER_ARCHIVE_YEAR, "年度不合法"));
            return null;
        }
    }

    private List<ArchiveImportRowError> validateImportRows(
            ArchiveCategoryDto category,
            List<ArchiveFieldDto> fields,
            List<ArchiveImportRow> rows,
            Long userId) {
        List<ArchiveImportRowError> errors = new ArrayList<>();
        Set<String> batchArchiveNos = new LinkedHashSet<>();
        for (ArchiveImportRow row : rows) {
            errors.addAll(row.parseErrors());
            ArchiveItem existingItem = validateFixedFields(category, row, batchArchiveNos, errors);
            row.bindExistingItem(existingItem);
            validateRowPermission(row, userId, errors);
            Map<String, @Nullable Object> convertedFields =
                    validateDynamicFields(
                            row.rowNumber(), fields, row.createRequest().dynamicFields(), errors);
            validateRowDataScope(
                    new ImportRowDataScopeCheck(
                            category,
                            fields,
                            row.createRequest(),
                            convertedFields,
                            userId,
                            row.rowNumber(),
                            errors));
        }
        return errors;
    }

    private @Nullable ArchiveItem validateFixedFields(
            ArchiveCategoryDto category,
            ArchiveImportRow row,
            Set<String> batchArchiveNos,
            List<ArchiveImportRowError> errors) {
        ArchiveItemCommandService.CreateArchiveItemRequest request = row.createRequest();
        if (StringUtils.isBlank(request.fondsCode())) {
            errors.add(new ArchiveImportRowError(row.rowNumber(), HEADER_FONDS_CODE, "全宗不能为空"));
        } else {
            try {
                archiveMetadataReferenceService.getEnabledFondsByCode(request.fondsCode());
            } catch (RuntimeException exception) {
                errors.add(
                        new ArchiveImportRowError(row.rowNumber(), HEADER_FONDS_CODE, "全宗不存在或已停用"));
            }
        }
        if (request.archiveYear() != null && request.archiveYear() < 1) {
            errors.add(new ArchiveImportRowError(row.rowNumber(), HEADER_ARCHIVE_YEAR, "年度不合法"));
        }
        String archiveNo = StringUtils.trimToNull(request.archiveNo());
        if (archiveNo != null && !batchArchiveNos.add(archiveNo)) {
            errors.add(new ArchiveImportRowError(row.rowNumber(), HEADER_ARCHIVE_NO, "同批次档号重复"));
        }
        return archiveNo == null
                ? null
                : archiveItemRepository.findByArchiveNo(category.categoryCode(), archiveNo);
    }

    private void validateRowPermission(
            ArchiveImportRow row, Long userId, List<ArchiveImportRowError> errors) {
        AuthorizationPermissionCode permissionCode =
                row.existingItem() == null
                        ? AuthorizationPermissionCode.ARCHIVE_ITEM_CREATE
                        : AuthorizationPermissionCode.ARCHIVE_ITEM_UPDATE;
        if (!permissionService.hasPermission(userId, permissionCode.code())) {
            String message = row.existingItem() == null ? "缺少创建权限" : "缺少编辑权限";
            errors.add(new ArchiveImportRowError(row.rowNumber(), "*", message));
        }
    }

    private Map<String, @Nullable Object> validateDynamicFields(
            int rowNumber,
            List<ArchiveFieldDto> fields,
            @Nullable Map<String, @Nullable Object> values,
            List<ArchiveImportRowError> errors) {
        Map<String, @Nullable Object> converted = new LinkedHashMap<>();
        Map<String, @Nullable Object> source = values == null ? Map.of() : values;
        for (ArchiveFieldDto field : fields) {
            try {
                converted.put(
                        field.fieldCode(), convertFieldValue(field, source.get(field.fieldCode())));
            } catch (IllegalArgumentException exception) {
                errors.add(
                        new ArchiveImportRowError(
                                rowNumber, field.fieldName(), exception.getMessage()));
            }
        }
        return converted;
    }

    private void validateRowDataScope(ImportRowDataScopeCheck check) {
        if (StringUtils.isBlank(check.request().fondsCode())) {
            return;
        }
        ArchiveDataScopeFilter filter =
                dataScopeService.buildItemFilter(
                        check.userId(), check.category().id(), check.request().fondsCode());
        if (filter.empty()) {
            check.errors().add(new ArchiveImportRowError(check.rowNumber(), "*", "数据范围不足"));
            return;
        }
        if (filter.allData()) {
            return;
        }
        Map<String, @Nullable Object> dynamicRow = new LinkedHashMap<>();
        for (ArchiveFieldDto field : check.fields()) {
            dynamicRow.put(field.columnName(), check.convertedFields().get(field.fieldCode()));
        }
        if (!dataScopeService.matchesItemFilter(
                filter,
                check.request().fondsCode(),
                check.request().securityLevelId(),
                check.request().retentionPeriodId(),
                dynamicRow)) {
            check.errors().add(new ArchiveImportRowError(check.rowNumber(), "*", "数据范围不足"));
        }
    }

    private @Nullable Object convertFieldValue(ArchiveFieldDto field, @Nullable Object value) {
        if (value instanceof String text) {
            value = StringUtils.trimToNull(text);
        }
        if (value == null) {
            return null;
        }
        try {
            return switch (field.fieldType()) {
                case TEXT -> convertTextValue(field, value);
                case INTEGER -> Integer.valueOf(value.toString());
                case DECIMAL -> new BigDecimal(value.toString());
                case DATE -> Date.valueOf(LocalDate.parse(value.toString()));
                case DATETIME -> Timestamp.valueOf(LocalDateTime.parse(value.toString()));
            };
        } catch (DateTimeParseException | IllegalArgumentException exception) {
            throw new IllegalArgumentException(field.fieldName() + "格式不合法", exception);
        }
    }

    private String convertTextValue(ArchiveFieldDto field, Object value) {
        String text = StringUtils.trimToNull(value.toString());
        if (text == null) {
            return "";
        }
        if (field.textLength() != null && text.length() > field.textLength()) {
            throw new IllegalArgumentException(field.fieldName() + "长度不能超过 " + field.textLength());
        }
        return text;
    }

    private byte[] writeExcel(
            List<List<String>> head, List<List<@Nullable Object>> rows, String sheetName) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            FesodSheet.write(outputStream).head(head).sheet(sheetName).doWrite(rows);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new BadRequestException("生成 Excel 失败");
        }
    }

    private void writeExportAudit(
            ArchiveItemQueryService.SearchArchiveItemsRequest request, Long userId, int rowCount) {
        ArchiveItemAudit audit = new ArchiveItemAudit();
        audit.setSourceTableName("am_archive_item");
        audit.setSourceRecordId(0L);
        audit.setFondsCode(StringUtils.trimToNull(request.fondsCode()));
        if (request.categoryId() != null) {
            ArchiveCategoryDto category = archiveCategoryService.getCategory(request.categoryId());
            audit.setCategoryCode(category.categoryCode());
        }
        audit.setOperationType("EXPORT");
        audit.setOperationReason("rows=" + rowCount + ", query=" + Objects.toString(request));
        audit.setOperatedBy(userId);
        auditRepository.insert(audit);
    }

    private record ImportRowDataScopeCheck(
            ArchiveCategoryDto category,
            List<ArchiveFieldDto> fields,
            ArchiveItemCommandService.CreateArchiveItemRequest request,
            Map<String, @Nullable Object> convertedFields,
            Long userId,
            int rowNumber,
            List<ArchiveImportRowError> errors) {}

    public record ArchiveImportResult(int importedCount, List<ArchiveImportRowError> errors) {}

    public record ArchiveImportRowError(int rowNumber, String fieldName, String message) {}

    public record ArchiveExcelFile(String filename, byte[] bytes) {}

    public record DownloadLinkCreated(String code, LocalDateTime expiresAt) {}
}
