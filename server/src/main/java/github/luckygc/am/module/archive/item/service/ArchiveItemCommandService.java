package github.luckygc.am.module.archive.item.service;

import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.mybatis.spring.MyBatisSystemException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeResolutionTypes.ArchiveDataScopeFilter;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService;
import github.luckygc.am.module.archive.governance.service.ArchiveGovernanceService;
import github.luckygc.am.module.archive.item.ArchiveItemAudit;
import github.luckygc.am.module.archive.item.repository.ArchiveItemAuditDataRepository;
import github.luckygc.am.module.archive.item.service.ArchiveItemReadService.ArchiveItemDetailDto;
import github.luckygc.am.module.archive.item.service.ArchiveItemReadService.ArchiveItemDto;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.mapper.ArchiveSqlAssignment;
import github.luckygc.am.module.archive.metadata.ArchiveDynamicTableNames;
import github.luckygc.am.module.archive.metadata.ArchiveFieldScope;
import github.luckygc.am.module.archive.metadata.ArchiveLayoutSurface;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataReferenceService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFieldDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFondsDto;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@Service
public class ArchiveItemCommandService {

    private static final String AUDIT_OPERATION_CREATE = "CREATE";
    private static final String AUDIT_OPERATION_UPDATE = "UPDATE";
    private static final String AUDIT_OPERATION_DELETE = "DELETE";
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DEFAULT_PAGE_LIMIT = 100;
    private static final int MAX_PAGE_LIMIT = 1000;
    private final ArchiveMetadataService archiveMetadataService;
    private final ArchiveMetadataReferenceService archiveMetadataReferenceService;
    private final ArchiveCategoryService archiveCategoryService;
    private final ArchiveGovernanceService governanceService;
    private final ArchiveMapper archiveMapper;
    private final ArchiveItemSearchProjectionService searchProjectionService;
    private final ArchiveDataScopeService dataScopeService;
    private final AuthorizationPermissionService permissionService;
    private final ArchiveItemAuditDataRepository auditRepository;
    private final ArchiveItemReadService archiveItemReadService;
    private final ArchiveItemFieldValueConverter fieldValueConverter;

    public ArchiveItemCommandService(
            ArchiveMetadataService archiveMetadataService,
            ArchiveMetadataReferenceService archiveMetadataReferenceService,
            ArchiveCategoryService archiveCategoryService,
            ArchiveGovernanceService governanceService,
            ArchiveMapper archiveMapper,
            ArchiveItemSearchProjectionService searchProjectionService,
            ArchiveDataScopeService dataScopeService,
            AuthorizationPermissionService permissionService,
            ArchiveItemAuditDataRepository auditRepository,
            ArchiveItemReadService archiveItemReadService,
            ArchiveItemFieldValueConverter fieldValueConverter) {
        this.archiveMetadataService = archiveMetadataService;
        this.archiveMetadataReferenceService = archiveMetadataReferenceService;
        this.archiveCategoryService = archiveCategoryService;
        this.governanceService = governanceService;
        this.archiveMapper = archiveMapper;
        this.searchProjectionService = searchProjectionService;
        this.dataScopeService = dataScopeService;
        this.permissionService = permissionService;
        this.auditRepository = auditRepository;
        this.archiveItemReadService = archiveItemReadService;
        this.fieldValueConverter = fieldValueConverter;
    }

    @Transactional
    public ArchiveItemDto createItem(@Nullable CreateArchiveItemRequest request, Long userId) {
        requirePermission(userId, "archive:item:create");
        if (request == null) {
            throw badRequest("请求体不能为空");
        }
        if (request.categoryId() == null) {
            throw badRequest("档案分类不能为空", "categoryId", "档案分类不能为空");
        }
        ArchiveCategoryDto category = archiveCategoryService.getCategory(request.categoryId());
        ArchiveLevel archiveLevel = ArchiveLevel.ITEM;
        ensureArchiveLevelAllowed(category, archiveLevel);
        String tableName = dynamicTableName(category, archiveLevel);
        if (!isDynamicTableBuilt(category, archiveLevel)) {
            throw badRequest("档案分类尚未建表");
        }
        if (StringUtils.isBlank(request.fondsCode())) {
            throw badRequest("全宗不能为空", "fondsCode", "全宗不能为空");
        }
        ArchiveFondsDto fonds =
                archiveMetadataReferenceService.getEnabledFondsByCode(request.fondsCode());
        Long volumeId =
                validateParentForWrite(
                        archiveLevel,
                        request.volumeId(),
                        category.categoryCode(),
                        fonds.fondsCode());
        List<ArchiveFieldDto> fields =
                archiveMetadataService.listEnabledFields(request.categoryId(), archiveLevel);
        List<ArchiveFieldDto> physicalFields =
                archiveMetadataService.listEnabledFields(
                        request.categoryId(), archiveLevel, ArchiveFieldScope.PHYSICAL);
        Map<String, @Nullable Object> dynamicFields =
                request.dynamicFields() == null ? Map.of() : request.dynamicFields();
        Map<String, @Nullable Object> requestPhysicalFields = request.physicalFields();
        int archiveYear =
                request.archiveYear() == null ? Year.now().getValue() : request.archiveYear();
        validateArchiveYear(archiveYear);
        String archiveNo = StringUtils.trimToNull(request.archiveNo());
        ensureItemArchiveNoUnique(category.categoryCode(), archiveNo, null);
        Map<String, @Nullable Object> convertedDynamicFields =
                fieldValueConverter.convertFields(fields, dynamicFields, "dynamicFields");
        Map<String, @Nullable Object> convertedPhysicalFields =
                requestPhysicalFields == null
                        ? Map.of()
                        : fieldValueConverter.convertFields(
                                physicalFields, requestPhysicalFields, "physicalFields");
        assertProposedItemInDataScope(
                userId,
                category,
                fonds.fondsCode(),
                request.securityLevelId(),
                request.retentionPeriodId(),
                fields,
                convertedDynamicFields);
        Long governanceSchemeVersionId =
                governanceService
                        .requireDefaultVersionForNewArchive(
                                fonds.fondsCode(), category.categoryCode())
                        .getId();

        Long recordId;
        try {
            recordId =
                    archiveMapper.insertArchiveItem(
                            archiveLevel.value(),
                            volumeId,
                            fonds.fondsCode(),
                            fonds.fondsName(),
                            category.categoryCode(),
                            category.categoryName(),
                            archiveNo,
                            StringUtils.defaultIfBlank(request.electronicStatus(), "DRAFT"),
                            request.securityLevelId(),
                            request.retentionPeriodId(),
                            archiveYear,
                            governanceSchemeVersionId);
        } catch (DuplicateKeyException exception) {
            throw duplicateArchiveNo();
        }
        try {
            insertDynamicRecord(tableName, recordId, fields, convertedDynamicFields);
        } catch (DuplicateKeyException | MyBatisSystemException exception) {
            throw badRequest("档案条目违反唯一约束");
        }
        if (requestPhysicalFields != null) {
            upsertPhysicalFieldsIfPresent(
                    category, archiveLevel, recordId, physicalFields, convertedPhysicalFields);
        }
        searchProjectionService.upsert(recordId, category, fields, convertedDynamicFields);
        ArchiveItemDto record = archiveItemReadService.getItem(recordId);
        insertItemAudit(AUDIT_OPERATION_CREATE, record, null, userId);
        return record;
    }

    @Transactional
    public ArchiveItemDetailDto updateItem(
            Long id, @Nullable UpdateArchiveItemRequest request, Long userId) {
        requirePermission(userId, "archive:item:update");
        if (request == null) {
            throw badRequest("请求体不能为空");
        }
        ArchiveItemDetailDto before =
                archiveItemReadService.getItemDetail(id, userId, ArchiveLayoutSurface.EDIT);
        archiveItemReadService.assertItemInDataScope(userId, before.category(), before.item());
        archiveItemReadService.ensureItemEditable(before.item());
        ArchiveCategoryDto category = before.category();
        String tableName = dynamicTableName(category, ArchiveLevel.ITEM);
        if (!isDynamicTableBuilt(category, ArchiveLevel.ITEM)) {
            throw badRequest("档案分类尚未建表");
        }
        if (StringUtils.isBlank(request.fondsCode())) {
            throw badRequest("全宗不能为空", "fondsCode", "全宗不能为空");
        }
        ArchiveFondsDto fonds =
                archiveMetadataReferenceService.getEnabledFondsByCode(request.fondsCode());
        Long volumeId =
                validateParentForWrite(
                        ArchiveLevel.ITEM,
                        request.volumeId() == null ? before.item().volumeId() : request.volumeId(),
                        before.item().categoryCode(),
                        fonds.fondsCode());
        int archiveYear =
                request.archiveYear() == null ? before.item().archiveYear() : request.archiveYear();
        validateArchiveYear(archiveYear);
        String archiveNo = StringUtils.trimToNull(request.archiveNo());
        ensureItemArchiveNoUnique(before.item().categoryCode(), archiveNo, id);
        Map<String, @Nullable Object> requestDynamicFields =
                request.dynamicFields() == null ? before.dynamicFields() : request.dynamicFields();
        Map<String, @Nullable Object> convertedDynamicFields =
                fieldValueConverter.convertFields(
                        before.fields(), requestDynamicFields, "dynamicFields");
        Map<String, @Nullable Object> requestPhysicalFields = request.physicalFields();
        Map<String, @Nullable Object> convertedPhysicalFields =
                requestPhysicalFields == null
                        ? Map.of()
                        : fieldValueConverter.convertFields(
                                before.physicalFields(), requestPhysicalFields, "physicalFields");
        assertProposedItemInDataScope(
                userId,
                category,
                fonds.fondsCode(),
                request.securityLevelId() == null
                        ? before.item().securityLevelId()
                        : request.securityLevelId(),
                request.retentionPeriodId() == null
                        ? before.item().retentionPeriodId()
                        : request.retentionPeriodId(),
                before.fields(),
                convertedDynamicFields);
        int updated;
        try {
            updated =
                    archiveMapper.updateArchiveItem(
                            id,
                            volumeId,
                            fonds.fondsCode(),
                            fonds.fondsName(),
                            archiveNo,
                            StringUtils.defaultIfBlank(
                                    request.electronicStatus(), before.item().electronicStatus()),
                            request.securityLevelId() == null
                                    ? before.item().securityLevelId()
                                    : request.securityLevelId(),
                            request.retentionPeriodId() == null
                                    ? before.item().retentionPeriodId()
                                    : request.retentionPeriodId(),
                            archiveYear);
        } catch (DuplicateKeyException exception) {
            throw duplicateArchiveNo();
        }
        if (updated == 0) {
            throw badRequest("档案条目已锁定，不能修改");
        }
        try {
            archiveMapper.updateDynamicRecord(
                    tableName, id, dynamicAssignments(before.fields(), convertedDynamicFields));
        } catch (DuplicateKeyException | MyBatisSystemException exception) {
            throw badRequest("档案条目违反唯一约束");
        }
        if (requestPhysicalFields != null) {
            upsertPhysicalFieldsIfPresent(
                    category,
                    ArchiveLevel.ITEM,
                    id,
                    before.physicalFields(),
                    convertedPhysicalFields);
        }
        searchProjectionService.refreshFromDynamicRecord(id, category, ArchiveLevel.ITEM);
        ArchiveItemDetailDto after =
                archiveItemReadService.getItemDetail(id, userId, ArchiveLayoutSurface.EDIT);
        insertItemAudit(AUDIT_OPERATION_UPDATE, after.item(), null, userId);
        return after;
    }

    @Transactional
    public void deleteItem(Long id, Long userId, @Nullable DeleteItemRequest request) {
        requirePermission(userId, "archive:item:delete");
        ArchiveItemDto record = archiveItemReadService.getItem(id);
        ArchiveCategoryDto category =
                archiveItemReadService.getCategoryByCode(record.categoryCode());
        archiveItemReadService.assertItemInDataScope(userId, category, record);
        archiveItemReadService.ensureItemEditable(record);
        String tableName = dynamicTableName(category, ArchiveLevel.ITEM);
        insertItemAudit(
                AUDIT_OPERATION_DELETE,
                record,
                request == null ? null : StringUtils.trimToNull(request.reason()),
                userId);
        if (isDynamicTableBuilt(category, ArchiveLevel.ITEM)) {
            archiveMapper.markDynamicRecordDeleted(tableName, id, userId);
        }
        String physicalTableName =
                dynamicTableName(category, ArchiveLevel.ITEM, ArchiveFieldScope.PHYSICAL);
        if (isDynamicTableBuilt(category, ArchiveLevel.ITEM, ArchiveFieldScope.PHYSICAL)) {
            archiveMapper.markDynamicRecordDeleted(physicalTableName, id, userId);
        }
        int updated = archiveMapper.markArchiveItemDeleted(id, userId);
        if (updated == 0) {
            throw badRequest("档案条目已锁定，不能删除");
        }
        searchProjectionService.delete(id);
    }

    private void upsertPhysicalFieldsIfPresent(
            ArchiveCategoryDto category,
            ArchiveLevel archiveLevel,
            Long recordId,
            List<ArchiveFieldDto> fields,
            Map<String, @Nullable Object> convertedFields) {
        if (fields.isEmpty() || convertedFields.isEmpty()) {
            return;
        }
        if (!isDynamicTableBuilt(category, archiveLevel, ArchiveFieldScope.PHYSICAL)) {
            throw badRequest("档案分类实物信息尚未建表");
        }
        String tableName = dynamicTableName(category, archiveLevel, ArchiveFieldScope.PHYSICAL);
        Map<String, @Nullable Object> current =
                archiveMapper.loadDynamicRecord(tableName, recordId);
        if (current == null) {
            insertDynamicRecord(tableName, recordId, fields, convertedFields);
        } else {
            archiveMapper.updateDynamicRecord(
                    tableName, recordId, dynamicAssignments(fields, convertedFields));
        }
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

    private Long validateParentForWrite(
            ArchiveLevel archiveLevel,
            @Nullable Long volumeId,
            String categoryCode,
            String fondsCode) {
        if (archiveLevel == ArchiveLevel.VOLUME) {
            if (volumeId != null) {
                throw badRequest("案卷不能设置父记录");
            }
            return null;
        }
        return volumeId;
    }

    private void insertDynamicRecord(
            String tableName,
            Long recordId,
            List<ArchiveFieldDto> fields,
            Map<String, @Nullable Object> convertedDynamicFields) {
        StringBuilder columns = new StringBuilder("id");
        List<Object> values = new ArrayList<>();
        values.add(recordId);
        for (ArchiveFieldDto field : fields) {
            columns.append(", ").append(field.columnName());
            values.add(convertedDynamicFields.get(field.fieldCode()));
        }
        archiveMapper.insertDynamicRecord(tableName, columns.toString(), values);
    }

    private List<ArchiveSqlAssignment> dynamicAssignments(
            List<ArchiveFieldDto> fields, Map<String, @Nullable Object> convertedDynamicFields) {
        List<ArchiveSqlAssignment> assignments = new ArrayList<>();
        for (ArchiveFieldDto field : fields) {
            assignments.add(
                    new ArchiveSqlAssignment(
                            field.columnName(), convertedDynamicFields.get(field.fieldCode())));
        }
        return assignments;
    }

    private void validateArchiveYear(int archiveYear) {
        int nextYear = Year.now().getValue() + 1;
        if (archiveYear < 1 || archiveYear > nextYear) {
            throw badRequest(
                    "年度必须在 1 到 " + nextYear + " 之间",
                    "archiveYear",
                    "年度必须在 1 到 " + nextYear + " 之间");
        }
    }

    private void ensureItemArchiveNoUnique(
            String categoryCode, @Nullable String archiveNo, @Nullable Long excludedId) {
        if (StringUtils.isBlank(archiveNo)) {
            return;
        }
        if (archiveMapper.countArchiveItemsByArchiveNo(categoryCode, archiveNo, excludedId) > 0) {
            throw duplicateArchiveNo();
        }
    }

    private BadRequestException duplicateArchiveNo() {
        return badRequest("档号已存在", "archiveNo", "档号已存在");
    }

    private void insertItemAudit(
            String operationType, ArchiveItemDto record, String operationReason, Long operatedBy) {
        ArchiveItemAudit audit = new ArchiveItemAudit();
        audit.setSourceTableName("am_archive_item");
        audit.setSourceRecordId(record.id());
        audit.setArchiveItemId(record.id());
        audit.setFondsCode(record.fondsCode());
        audit.setCategoryCode(record.categoryCode());
        audit.setOperationType(operationType);
        audit.setOperationReason(operationReason);
        audit.setOperatedBy(operatedBy);
        auditRepository.insert(audit);
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

    private void assertProposedItemInDataScope(
            Long userId,
            ArchiveCategoryDto category,
            String fondsCode,
            @Nullable Long securityLevelId,
            @Nullable Long retentionPeriodId,
            List<ArchiveFieldDto> fields,
            Map<String, @Nullable Object> dynamicFieldsByCode) {
        userId = AuthenticatedUsers.requireResolvedUserId(userId);
        ArchiveDataScopeFilter filter =
                dataScopeService.buildItemFilter(userId, category.id(), fondsCode);
        if (filter.empty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "数据范围不足");
        }
        if (filter.allData()) {
            return;
        }
        Map<String, @Nullable Object> dynamicRow = new LinkedHashMap<>();
        for (ArchiveFieldDto field : fields) {
            dynamicRow.put(field.columnName(), dynamicFieldsByCode.get(field.fieldCode()));
        }
        if (!dataScopeService.matchesItemFilter(
                filter, fondsCode, securityLevelId, retentionPeriodId, dynamicRow)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "数据范围不足");
        }
    }

    public record CreateArchiveItemRequest(
            @Nullable Long categoryId,
            @Nullable Long volumeId,
            @Nullable String fondsCode,
            @Nullable String archiveNo,
            @Nullable Integer archiveYear,
            @Nullable String electronicStatus,
            @Nullable Long securityLevelId,
            @Nullable Long retentionPeriodId,
            @Nullable Map<String, @Nullable Object> physicalFields,
            @Nullable Map<String, @Nullable Object> dynamicFields) {}

    public record UpdateArchiveItemRequest(
            @Nullable Long volumeId,
            @Nullable String fondsCode,
            @Nullable String archiveNo,
            @Nullable Integer archiveYear,
            @Nullable String electronicStatus,
            @Nullable Long securityLevelId,
            @Nullable Long retentionPeriodId,
            @Nullable Map<String, @Nullable Object> physicalFields,
            @Nullable Map<String, @Nullable Object> dynamicFields) {}

    public record DeleteItemRequest(@Nullable String reason) {}
}
