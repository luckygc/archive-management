package github.luckygc.am.module.archive.item.service;

import java.time.LocalDateTime;
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
import github.luckygc.am.module.archive.rule.ArchiveRuntimeTriggerPoint;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeExecutionService;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeExecutionService.ArchiveRuntimeExecutionRequest;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeExecutionService.ArchiveRuntimeExecutionResult;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeTraceService;
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
    private final ArchiveRuntimeExecutionService runtimeExecutionService;
    private final ArchiveRuntimeTraceService runtimeTraceService;

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
            ArchiveItemFieldValueConverter fieldValueConverter,
            ArchiveRuntimeExecutionService runtimeExecutionService,
            ArchiveRuntimeTraceService runtimeTraceService) {
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
        this.runtimeExecutionService = runtimeExecutionService;
        this.runtimeTraceService = runtimeTraceService;
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
        String archiveNo = StringUtils.trimToNull(request.archiveNo());
        Map<String, @Nullable Object> convertedDynamicFields =
                fieldValueConverter.convertFields(fields, dynamicFields, "dynamicFields");
        Map<String, @Nullable Object> convertedPhysicalFields =
                requestPhysicalFields == null
                        ? Map.of()
                        : fieldValueConverter.convertFields(
                                physicalFields, requestPhysicalFields, "physicalFields");
        Long governanceSchemeVersionId =
                governanceService
                        .requireDefaultVersionForNewArchive(
                                fonds.fondsCode(), category.categoryCode())
                        .getId();
        ItemPolicyExecution policyExecution =
                enforceItemPolicy(
                        governanceSchemeVersionId,
                        ArchiveRuntimeTriggerPoint.ITEM_BEFORE_CREATE,
                        null,
                        volumeId,
                        fonds.fondsCode(),
                        fonds.fondsName(),
                        category,
                        archiveNo,
                        archiveYear,
                        StringUtils.defaultIfBlank(request.electronicStatus(), "DRAFT"),
                        request.securityLevelId(),
                        request.retentionPeriodId(),
                        fields,
                        convertedDynamicFields,
                        physicalFields,
                        convertedPhysicalFields,
                        userId);
        ArchiveRuntimeExecutionResult runtimeResult = policyExecution.result();
        ItemCandidate candidate =
                finalItemCandidate(runtimeResult, fields, physicalFields, "DRAFT");
        archiveNo = candidate.archiveNo();
        archiveYear = candidate.archiveYear();
        convertedDynamicFields = candidate.dynamicFields();
        convertedPhysicalFields = candidate.physicalFields();
        validateArchiveYear(archiveYear);
        ensureItemArchiveNoUnique(category.categoryCode(), archiveNo, null);
        assertProposedItemInDataScope(
                userId,
                category,
                fonds.fondsCode(),
                candidate.securityLevelId(),
                candidate.retentionPeriodId(),
                fields,
                convertedDynamicFields);

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
                            candidate.electronicStatus(),
                            candidate.securityLevelId(),
                            candidate.retentionPeriodId(),
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
        if (requestPhysicalFields != null || hasAssignment(runtimeResult, "physical.")) {
            upsertPhysicalFieldsIfPresent(
                    category, archiveLevel, recordId, physicalFields, convertedPhysicalFields);
        }
        searchProjectionService.upsert(recordId, category, fields, convertedDynamicFields);
        ArchiveItemDto record = archiveItemReadService.getItem(recordId);
        insertItemAudit(AUDIT_OPERATION_CREATE, record, null, userId);
        runtimeTraceService.saveSuccessfulExecution(
                policyExecution.request(), runtimeResult, recordId);
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
        String archiveNo = StringUtils.trimToNull(request.archiveNo());
        List<ArchiveFieldDto> allFields =
                archiveMetadataService.listEnabledFields(category.id(), ArchiveLevel.ITEM);
        List<ArchiveFieldDto> allPhysicalFields =
                archiveMetadataService.listEnabledFields(
                        category.id(), ArchiveLevel.ITEM, ArchiveFieldScope.PHYSICAL);
        Map<String, @Nullable Object> currentDynamicFields =
                loadFieldsByCode(tableName, id, allFields);
        Map<String, @Nullable Object> requestDynamicFields =
                request.dynamicFields() == null ? Map.of() : request.dynamicFields();
        Map<String, @Nullable Object> convertedRequestDynamicFields =
                request.dynamicFields() == null
                        ? Map.of()
                        : fieldValueConverter.convertFields(
                                before.fields(), requestDynamicFields, "dynamicFields");
        Map<String, @Nullable Object> convertedDynamicFields =
                mergeFields(currentDynamicFields, convertedRequestDynamicFields);
        Map<String, @Nullable Object> requestPhysicalFields = request.physicalFields();
        Map<String, @Nullable Object> currentPhysicalFields =
                loadFieldsByCode(
                        dynamicTableName(category, ArchiveLevel.ITEM, ArchiveFieldScope.PHYSICAL),
                        id,
                        allPhysicalFields);
        Map<String, @Nullable Object> convertedRequestPhysicalFields =
                requestPhysicalFields == null
                        ? Map.of()
                        : fieldValueConverter.convertFields(
                                before.physicalFields(), requestPhysicalFields, "physicalFields");
        Map<String, @Nullable Object> convertedPhysicalFields =
                mergeFields(currentPhysicalFields, convertedRequestPhysicalFields);
        Long governanceSchemeVersionId = requireGovernanceVersionId(before.item());
        ItemPolicyExecution policyExecution =
                enforceItemPolicy(
                        governanceSchemeVersionId,
                        ArchiveRuntimeTriggerPoint.ITEM_BEFORE_UPDATE,
                        id,
                        volumeId,
                        fonds.fondsCode(),
                        fonds.fondsName(),
                        category,
                        archiveNo,
                        archiveYear,
                        StringUtils.defaultIfBlank(
                                request.electronicStatus(), before.item().electronicStatus()),
                        request.securityLevelId() == null
                                ? before.item().securityLevelId()
                                : request.securityLevelId(),
                        request.retentionPeriodId() == null
                                ? before.item().retentionPeriodId()
                                : request.retentionPeriodId(),
                        allFields,
                        convertedDynamicFields,
                        allPhysicalFields,
                        convertedPhysicalFields,
                        userId);
        ArchiveRuntimeExecutionResult runtimeResult = policyExecution.result();
        ItemCandidate candidate =
                finalItemCandidate(
                        runtimeResult,
                        allFields,
                        allPhysicalFields,
                        before.item().electronicStatus());
        archiveNo = candidate.archiveNo();
        archiveYear = candidate.archiveYear();
        convertedDynamicFields = candidate.dynamicFields();
        convertedPhysicalFields = candidate.physicalFields();
        validateArchiveYear(archiveYear);
        ensureItemArchiveNoUnique(before.item().categoryCode(), archiveNo, id);
        assertProposedItemInDataScope(
                userId,
                category,
                fonds.fondsCode(),
                candidate.securityLevelId(),
                candidate.retentionPeriodId(),
                allFields,
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
                            candidate.electronicStatus(),
                            candidate.securityLevelId(),
                            candidate.retentionPeriodId(),
                            archiveYear);
        } catch (DuplicateKeyException exception) {
            throw duplicateArchiveNo();
        }
        if (updated == 0) {
            throw badRequest("档案条目已锁定，不能修改");
        }
        try {
            archiveMapper.updateDynamicRecord(
                    tableName, id, dynamicAssignments(allFields, convertedDynamicFields));
        } catch (DuplicateKeyException | MyBatisSystemException exception) {
            throw badRequest("档案条目违反唯一约束");
        }
        if (requestPhysicalFields != null || hasAssignment(runtimeResult, "physical.")) {
            upsertPhysicalFieldsIfPresent(
                    category, ArchiveLevel.ITEM, id, allPhysicalFields, convertedPhysicalFields);
        }
        searchProjectionService.refreshFromDynamicRecord(id, category, ArchiveLevel.ITEM);
        ArchiveItemDetailDto after =
                archiveItemReadService.getItemDetail(id, userId, ArchiveLayoutSurface.EDIT);
        insertItemAudit(AUDIT_OPERATION_UPDATE, after.item(), null, userId);
        runtimeTraceService.saveSuccessfulExecution(policyExecution.request(), runtimeResult, id);
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
        List<ArchiveFieldDto> fields =
                archiveMetadataService.listEnabledFields(category.id(), ArchiveLevel.ITEM);
        List<ArchiveFieldDto> physicalFields =
                archiveMetadataService.listEnabledFields(
                        category.id(), ArchiveLevel.ITEM, ArchiveFieldScope.PHYSICAL);
        ItemPolicyExecution policyExecution =
                enforceItemPolicy(
                        requireGovernanceVersionId(record),
                        ArchiveRuntimeTriggerPoint.ITEM_BEFORE_DELETE,
                        id,
                        record.volumeId(),
                        record.fondsCode(),
                        record.fondsName(),
                        category,
                        record.archiveNo(),
                        record.archiveYear(),
                        record.electronicStatus(),
                        record.securityLevelId(),
                        record.retentionPeriodId(),
                        fields,
                        loadFieldsByCode(tableName, id, fields),
                        physicalFields,
                        loadFieldsByCode(
                                dynamicTableName(
                                        category, ArchiveLevel.ITEM, ArchiveFieldScope.PHYSICAL),
                                id,
                                physicalFields),
                        userId);
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
        runtimeTraceService.saveSuccessfulExecution(
                policyExecution.request(), policyExecution.result(), id);
    }

    private ItemPolicyExecution enforceItemPolicy(
            Long governanceSchemeVersionId,
            ArchiveRuntimeTriggerPoint triggerPoint,
            @Nullable Long itemId,
            @Nullable Long volumeId,
            String fondsCode,
            String fondsName,
            ArchiveCategoryDto category,
            @Nullable String archiveNo,
            int archiveYear,
            String electronicStatus,
            @Nullable Long securityLevelId,
            @Nullable Long retentionPeriodId,
            List<ArchiveFieldDto> fields,
            Map<String, @Nullable Object> dynamicFields,
            List<ArchiveFieldDto> physicalFields,
            Map<String, @Nullable Object> physicalFieldValues,
            Long userId) {
        Map<String, @Nullable Object> facts = new LinkedHashMap<>();
        facts.put("item.id", itemId);
        facts.put("item.fondsCode", fondsCode);
        facts.put("item.fondsName", fondsName);
        facts.put("item.categoryCode", category.categoryCode());
        facts.put("item.categoryName", category.categoryName());
        facts.put("item.archiveNo", archiveNo);
        facts.put("item.archiveYear", archiveYear);
        facts.put("item.electronicStatus", electronicStatus);
        facts.put("item.securityLevelId", securityLevelId);
        facts.put("item.retentionPeriodId", retentionPeriodId);
        addFieldFacts(facts, "metadata.", fields, dynamicFields);
        addFieldFacts(facts, "physical.", physicalFields, physicalFieldValues);
        facts.put("context.userId", userId);
        facts.put("context.now", LocalDateTime.now());
        facts.put("context.operation", triggerPoint.name());
        ArchiveRuntimeExecutionRequest request =
                new ArchiveRuntimeExecutionRequest(
                        governanceSchemeVersionId,
                        triggerPoint,
                        fondsCode,
                        category.categoryCode(),
                        ArchiveLevel.ITEM,
                        "ARCHIVE_ITEM",
                        itemId,
                        facts,
                        userId);
        return new ItemPolicyExecution(request, runtimeExecutionService.enforce(request));
    }

    private void addFieldFacts(
            Map<String, @Nullable Object> facts,
            String prefix,
            List<ArchiveFieldDto> fields,
            Map<String, @Nullable Object> values) {
        fields.forEach(
                field -> facts.put(prefix + field.fieldCode(), values.get(field.fieldCode())));
    }

    private ItemCandidate finalItemCandidate(
            ArchiveRuntimeExecutionResult result,
            List<ArchiveFieldDto> fields,
            List<ArchiveFieldDto> physicalFields,
            String defaultElectronicStatus) {
        Map<String, @Nullable Object> facts = result.candidateFacts();
        Map<String, @Nullable Object> dynamicFields =
                fieldValueConverter.convertFields(
                        fields, fieldsFromFacts(facts, "metadata.", fields), "dynamicFields");
        Map<String, @Nullable Object> physicalFieldValues =
                fieldValueConverter.convertFields(
                        physicalFields,
                        fieldsFromFacts(facts, "physical.", physicalFields),
                        "physicalFields");
        return new ItemCandidate(
                stringFact(facts, "item.archiveNo"),
                intFact(facts, "item.archiveYear"),
                StringUtils.defaultIfBlank(
                        stringFact(facts, "item.electronicStatus"), defaultElectronicStatus),
                longFact(facts, "item.securityLevelId"),
                longFact(facts, "item.retentionPeriodId"),
                dynamicFields,
                physicalFieldValues);
    }

    private Map<String, @Nullable Object> fieldsFromFacts(
            Map<String, @Nullable Object> facts, String prefix, List<ArchiveFieldDto> fields) {
        Map<String, @Nullable Object> values = new LinkedHashMap<>();
        fields.forEach(
                field -> values.put(field.fieldCode(), facts.get(prefix + field.fieldCode())));
        return values;
    }

    private Map<String, @Nullable Object> loadFieldsByCode(
            String tableName, Long id, List<ArchiveFieldDto> fields) {
        if (fields.isEmpty()
                || StringUtils.isBlank(tableName)
                || archiveMapper.tableExists(tableName) == 0) {
            return Map.of();
        }
        Map<String, @Nullable Object> row = archiveMapper.loadDynamicRecord(tableName, id);
        if (row == null) return Map.of();
        Map<String, @Nullable Object> values = new LinkedHashMap<>();
        fields.forEach(field -> values.put(field.fieldCode(), row.get(field.columnName())));
        return values;
    }

    private Map<String, @Nullable Object> mergeFields(
            Map<String, @Nullable Object> current, Map<String, @Nullable Object> requested) {
        Map<String, @Nullable Object> merged = new LinkedHashMap<>(current);
        merged.putAll(requested);
        return merged;
    }

    private boolean hasAssignment(ArchiveRuntimeExecutionResult result, String prefix) {
        return result.assignments().keySet().stream().anyMatch(field -> field.startsWith(prefix));
    }

    private Long requireGovernanceVersionId(ArchiveItemDto item) {
        if (item.governanceSchemeVersionId() == null) {
            throw badRequest("档案条目未绑定治理版本，不能执行运行时检查");
        }
        return item.governanceSchemeVersionId();
    }

    private @Nullable String stringFact(Map<String, @Nullable Object> facts, String field) {
        Object value = facts.get(field);
        return value == null ? null : StringUtils.trimToNull(value.toString());
    }

    private int intFact(Map<String, @Nullable Object> facts, String field) {
        Object value = facts.get(field);
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw badRequest("运行时规则字段值类型不兼容：" + field);
        }
    }

    private @Nullable Long longFact(Map<String, @Nullable Object> facts, String field) {
        Object value = facts.get(field);
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException exception) {
            throw badRequest("运行时规则字段值类型不兼容：" + field);
        }
    }

    private record ItemCandidate(
            @Nullable String archiveNo,
            int archiveYear,
            String electronicStatus,
            @Nullable Long securityLevelId,
            @Nullable Long retentionPeriodId,
            Map<String, @Nullable Object> dynamicFields,
            Map<String, @Nullable Object> physicalFields) {}

    private record ItemPolicyExecution(
            ArchiveRuntimeExecutionRequest request, ArchiveRuntimeExecutionResult result) {}

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
