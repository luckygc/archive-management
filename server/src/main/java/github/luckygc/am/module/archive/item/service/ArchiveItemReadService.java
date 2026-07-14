package github.luckygc.am.module.archive.item.service;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService.ArchiveDataScopeFilter;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.metadata.ArchiveDynamicTableNames;
import github.luckygc.am.module.archive.metadata.ArchiveFieldScope;
import github.luckygc.am.module.archive.metadata.ArchiveLayoutSurface;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveFieldDto;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@Service
public class ArchiveItemReadService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final ArchiveMetadataService archiveMetadataService;
    private final ArchiveCategoryService archiveCategoryService;
    private final ArchiveMapper archiveMapper;
    private final ArchiveDataScopeService dataScopeService;
    private final AuthorizationPermissionService permissionService;

    public ArchiveItemReadService(
            ArchiveMetadataService archiveMetadataService,
            ArchiveCategoryService archiveCategoryService,
            ArchiveMapper archiveMapper,
            ArchiveDataScopeService dataScopeService,
            AuthorizationPermissionService permissionService) {
        this.archiveMetadataService = archiveMetadataService;
        this.archiveCategoryService = archiveCategoryService;
        this.archiveMapper = archiveMapper;
        this.dataScopeService = dataScopeService;
        this.permissionService = permissionService;
    }

    public void assertItemInDataScope(Long id, Long userId) {
        assertItemInDataScopeById(id, userId);
    }

    private void assertItemInDataScopeById(Long id, Long userId) {
        userId = AuthenticatedUsers.requireResolvedUserId(userId);
        ArchiveItemDto record = loadItem(id);
        ArchiveCategoryDto category = getCategoryByCode(record.categoryCode());
        assertItemInDataScope(userId, category, record);
    }

    public ArchiveItemDetailDto getItemDetail(
            Long id, Long userId, @Nullable ArchiveLayoutSurface surface) {
        return loadItemDetail(id, userId, surface);
    }

    private ArchiveItemDetailDto loadItemDetail(
            Long id, Long userId, @Nullable ArchiveLayoutSurface surface) {
        requirePermission(userId, "archive:item:read");
        ArchiveItemDto record = loadItem(id);
        ArchiveCategoryDto category = getCategoryByCode(record.categoryCode());
        assertItemInDataScope(userId, category, record);
        List<ArchiveFieldDto> fields =
                archiveMetadataService.listEffectiveFields(
                        category.id(),
                        ArchiveLevel.ITEM,
                        ArchiveFieldScope.METADATA,
                        surface == null ? ArchiveLayoutSurface.DETAIL : surface,
                        userId);
        List<ArchiveFieldDto> physicalFields =
                archiveMetadataService.listEffectiveFields(
                        category.id(),
                        ArchiveLevel.ITEM,
                        ArchiveFieldScope.PHYSICAL,
                        surface == null ? ArchiveLayoutSurface.DETAIL : surface,
                        userId);
        Map<String, @Nullable Object> dynamicRecord = loadDynamicRecord(category, record.id());
        Map<String, @Nullable Object> physicalRecord =
                loadDynamicRecord(category, record.id(), ArchiveFieldScope.PHYSICAL);
        return new ArchiveItemDetailDto(
                record,
                category,
                fields,
                dynamicFieldsByCode(dynamicRecord, fields),
                physicalFields,
                dynamicFieldsByCode(physicalRecord, physicalFields));
    }

    public ArchiveItemDto getItem(Long id) {
        return loadItem(id);
    }

    private ArchiveItemDto loadItem(Long id) {
        if (id == null || id <= 0) {
            throw badRequest("档案条目 ID 不合法");
        }
        Map<String, @Nullable Object> row = archiveMapper.getArchiveItem(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "档案条目不存在");
        }
        return new ArchiveItemDto(
                number(row, "id").longValue(),
                longOrNull(row, "volumeId"),
                string(row, "fondsCode"),
                string(row, "fondsName"),
                string(row, "categoryCode"),
                string(row, "categoryName"),
                string(row, "archiveNo"),
                string(row, "electronicStatus"),
                longOrNull(row, "securityLevelId"),
                longOrNull(row, "retentionPeriodId"),
                number(row, "archiveYear").intValue(),
                bool(row, "lockedFlag"),
                string(row, "lockReason"),
                longOrNull(row, "lockedBy"),
                dateTime(row, "lockedAt"));
    }

    private Map<String, @Nullable Object> loadDynamicRecord(ArchiveCategoryDto category, Long id) {
        ArchiveItemDto record = getItem(id);
        return loadDynamicRecord(category, ArchiveLevel.ITEM, id, ArchiveFieldScope.METADATA);
    }

    private Map<String, @Nullable Object> loadDynamicRecord(
            ArchiveCategoryDto category, Long id, ArchiveFieldScope fieldScope) {
        ArchiveItemDto record = getItem(id);
        return loadDynamicRecord(category, ArchiveLevel.ITEM, id, fieldScope);
    }

    private Map<String, @Nullable Object> loadDynamicRecord(
            ArchiveCategoryDto category,
            ArchiveLevel archiveLevel,
            Long id,
            ArchiveFieldScope fieldScope) {
        String tableName = dynamicTableName(category, archiveLevel, fieldScope);
        if (!isDynamicTableBuilt(category, archiveLevel, fieldScope)) {
            return Map.of();
        }
        Map<String, @Nullable Object> dynamicRecord =
                archiveMapper.loadDynamicRecord(tableName, id);
        return dynamicRecord == null ? Map.of() : dynamicRecord;
    }

    private Map<String, @Nullable Object> dynamicFieldsByCode(
            Map<String, @Nullable Object> dynamicRecord, List<ArchiveFieldDto> fields) {
        Map<String, @Nullable Object> dynamicFields = new LinkedHashMap<>();
        for (ArchiveFieldDto field : fields) {
            dynamicFields.put(
                    field.fieldCode(),
                    normalizeDynamicFieldValue(field, dynamicRecord.get(field.columnName())));
        }
        return dynamicFields;
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

    private String dynamicTableName(ArchiveCategoryDto category, ArchiveLevel archiveLevel) {
        return dynamicTableName(category, archiveLevel, ArchiveFieldScope.METADATA);
    }

    private String dynamicTableName(
            ArchiveCategoryDto category, ArchiveLevel archiveLevel, ArchiveFieldScope fieldScope) {
        return ArchiveDynamicTableNames.tableName(category, archiveLevel, fieldScope);
    }

    private ArchiveLevel normalizeArchiveLevel(ArchiveLevel archiveLevel) {
        return ArchiveDynamicTableNames.normalizeArchiveLevel(archiveLevel);
    }

    private void ensureArchiveLevelAllowed(ArchiveCategoryDto category, ArchiveLevel archiveLevel) {
        if (!ArchiveDynamicTableNames.supportsArchiveLevel(category, archiveLevel)) {
            throw badRequest("该分类未启用案卷管理");
        }
    }

    private boolean isDynamicTableBuilt(ArchiveCategoryDto category, ArchiveLevel archiveLevel) {
        return isDynamicTableBuilt(category, archiveLevel, ArchiveFieldScope.METADATA);
    }

    private boolean isDynamicTableBuilt(
            ArchiveCategoryDto category, ArchiveLevel archiveLevel, ArchiveFieldScope fieldScope) {
        String tableName = dynamicTableName(category, archiveLevel, fieldScope);
        return StringUtils.isNotBlank(tableName) && archiveMapper.tableExists(tableName) > 0;
    }

    public void ensureItemEditable(Long id) {
        ensureItemEditable(loadItem(id));
    }

    void ensureItemEditable(ArchiveItemDto record) {
        if (record.lockedFlag()) {
            throw badRequest("档案条目已锁定，不能修改");
        }
    }

    ArchiveCategoryDto getCategoryByCode(String categoryCode) {
        return archiveCategoryService.listCategories(null).stream()
                .filter(category -> category.categoryCode().equals(categoryCode))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "档案分类不存在"));
    }

    private BadRequestException badRequest(String message) {
        return new BadRequestException(message);
    }

    private BadRequestException badRequest(String message, String field, String description) {
        return new BadRequestException(message, field, description);
    }

    private void requirePermission(Long userId, String permissionCode) {
        userId = AuthenticatedUsers.requireResolvedUserId(userId);
        if (!permissionService.hasPermission(userId, permissionCode)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "权限不足");
        }
    }

    void assertItemInDataScope(Long userId, ArchiveCategoryDto category, ArchiveItemDto record) {
        userId = AuthenticatedUsers.requireResolvedUserId(userId);
        ArchiveDataScopeFilter filter =
                dataScopeService.buildItemFilter(userId, category.id(), record.fondsCode());
        if (filter.empty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "数据范围不足");
        }
        if (filter.allData()) {
            return;
        }
        Map<String, @Nullable Object> dynamicRecord = loadDynamicRecord(category, record.id());
        if (!dataScopeService.matchesItemFilter(
                filter,
                record.fondsCode(),
                record.securityLevelId(),
                record.retentionPeriodId(),
                dynamicRecord)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "数据范围不足");
        }
    }

    private @Nullable String string(Map<String, @Nullable Object> row, String key) {
        Object value = value(row, key);
        return value == null ? null : value.toString();
    }

    private boolean bool(Map<String, @Nullable Object> row, String key) {
        Object value = value(row, key);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private Number number(Map<String, @Nullable Object> row, String key) {
        Object value = value(row, key);
        if (value instanceof Number number) {
            return number;
        }
        throw new IllegalStateException("缺少数值字段：" + key);
    }

    private @Nullable Long longOrNull(Map<String, @Nullable Object> row, String key) {
        Object value = value(row, key);
        return value instanceof Number number ? number.longValue() : null;
    }

    private @Nullable LocalDateTime dateTime(Map<String, @Nullable Object> row, String key) {
        Object value = value(row, key);
        return value instanceof LocalDateTime dateTime ? dateTime : null;
    }

    private @Nullable Object value(Map<String, @Nullable Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        return row.get(JdbcUtils.convertPropertyNameToUnderscoreName(key));
    }

    public record ArchiveItemDto(
            Long id,
            @Nullable Long volumeId,
            String fondsCode,
            String fondsName,
            String categoryCode,
            String categoryName,
            @Nullable String archiveNo,
            String electronicStatus,
            @Nullable Long securityLevelId,
            @Nullable Long retentionPeriodId,
            int archiveYear,
            boolean lockedFlag,
            @Nullable String lockReason,
            @Nullable Long lockedBy,
            @Nullable LocalDateTime lockedAt) {}

    public record ArchiveItemDetailDto(
            ArchiveItemDto item,
            ArchiveCategoryDto category,
            List<ArchiveFieldDto> fields,
            Map<String, @Nullable Object> dynamicFields,
            List<ArchiveFieldDto> physicalFields,
            Map<String, @Nullable Object> physicalFieldValues) {}
}
