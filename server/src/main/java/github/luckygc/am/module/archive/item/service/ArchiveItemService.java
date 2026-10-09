package github.luckygc.am.module.archive.item.service;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import github.luckygc.am.module.archive.item.ArchiveItemAudit;
import github.luckygc.am.module.archive.item.repository.ArchiveItemAuditDataRepository;
import github.luckygc.am.module.archive.item.service.ArchiveItemCandidateAssembler.ItemCandidate;
import github.luckygc.am.module.archive.item.service.ArchiveItemCandidateAssembler.ItemPolicyCommand;
import github.luckygc.am.module.archive.item.service.ArchiveItemDynamicValues.InsertValues;
import github.luckygc.am.module.archive.item.service.ArchiveItemPatchParser.PreparedPatch;
import github.luckygc.am.module.archive.item.service.ArchiveItemReadService.ArchiveItemDetailDto;
import github.luckygc.am.module.archive.item.service.ArchiveItemReadService.ArchiveItemDto;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ArchiveItemService {

    private static final String AUDIT_OPERATION_CREATE = "CREATE";
    private static final String AUDIT_OPERATION_UPDATE = "UPDATE";
    private static final String AUDIT_OPERATION_REASSIGN_FONDS = "REASSIGN_FONDS";
    private static final String AUDIT_OPERATION_DELETE = "DELETE";
    private final ArchiveMetadataService archiveMetadataService;
    private final ArchiveMetadataReferenceService archiveMetadataReferenceService;
    private final ArchiveCategoryService archiveCategoryService;
    private final ArchiveMapper archiveMapper;
    private final ArchiveItemSearchProjectionSynchronizer searchProjectionSynchronizer;
    private final ArchiveDataScopeService dataScopeService;
    private final AuthorizationPermissionService permissionService;
    private final ArchiveItemAuditDataRepository auditRepository;
    private final ArchiveItemReadService archiveItemReadService;
    private final ArchiveItemFieldValueConverter fieldValueConverter;
    private final ArchiveRuntimeExecutionService runtimeExecutionService;
    private final ArchiveRuntimeTraceService runtimeTraceService;
    private final ArchiveItemPatchParser patchParser;
    private final ArchiveItemCandidateAssembler candidateAssembler;

    public ArchiveItemService(
            ArchiveMetadataService archiveMetadataService,
            ArchiveMetadataReferenceService archiveMetadataReferenceService,
            ArchiveCategoryService archiveCategoryService,
            ArchiveMapper archiveMapper,
            ArchiveItemSearchProjectionSynchronizer searchProjectionSynchronizer,
            ArchiveDataScopeService dataScopeService,
            AuthorizationPermissionService permissionService,
            ArchiveItemAuditDataRepository auditRepository,
            ArchiveItemReadService archiveItemReadService,
            ArchiveItemFieldValueConverter fieldValueConverter,
            ArchiveRuntimeExecutionService runtimeExecutionService,
            ArchiveRuntimeTraceService runtimeTraceService,
            JsonMapper jsonMapper) {
        this.archiveMetadataService = archiveMetadataService;
        this.archiveMetadataReferenceService = archiveMetadataReferenceService;
        this.archiveCategoryService = archiveCategoryService;
        this.archiveMapper = archiveMapper;
        this.searchProjectionSynchronizer = searchProjectionSynchronizer;
        this.dataScopeService = dataScopeService;
        this.permissionService = permissionService;
        this.auditRepository = auditRepository;
        this.archiveItemReadService = archiveItemReadService;
        this.fieldValueConverter = fieldValueConverter;
        this.runtimeExecutionService = runtimeExecutionService;
        this.runtimeTraceService = runtimeTraceService;
        this.patchParser = new ArchiveItemPatchParser(jsonMapper);
        this.candidateAssembler = new ArchiveItemCandidateAssembler(fieldValueConverter);
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
        String tableName = dynamicTableName(category, archiveLevel);
        if (!isDynamicTableBuilt(category, archiveLevel)) {
            throw badRequest("档案分类尚未建表");
        }
        if (StringUtils.isBlank(request.fondsCode())) {
            throw badRequest("全宗不能为空", "fondsCode", "全宗不能为空");
        }
        ArchiveFondsDto fonds =
                archiveMetadataReferenceService.getWritableFondsByCode(request.fondsCode());
        archiveCategoryService.requireCategoryAvailableForFonds(fonds.fondsCode(), category.id());
        Long volumeId = request.volumeId();
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
        ItemPolicyExecution policyExecution =
                enforceItemPolicy(
                        new ItemPolicyCommand(
                                ArchiveRuntimeTriggerPoint.ITEM_BEFORE_CREATE,
                                null,
                                fonds.fondsCode(),
                                fonds.fondsName(),
                                category,
                                new ItemCandidate(
                                        archiveNo,
                                        archiveYear,
                                        request.securityLevelId(),
                                        request.retentionPeriodId(),
                                        convertedDynamicFields,
                                        convertedPhysicalFields),
                                fields,
                                physicalFields,
                                userId));
        ArchiveRuntimeExecutionResult runtimeResult = policyExecution.result();
        ItemCandidate candidate =
                candidateAssembler.finalCandidate(runtimeResult, fields, physicalFields);
        archiveNo = candidate.archiveNo();
        archiveYear = candidate.archiveYear();
        convertedDynamicFields = candidate.dynamicFields();
        convertedPhysicalFields = candidate.physicalFields();
        validateArchiveYear(archiveYear);
        ensureItemArchiveNoUnique(category.categoryCode(), archiveNo, null);
        assertProposedItemInDataScope(userId, category, fonds.fondsCode(), fields, candidate);

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
                            candidate.securityLevelId(),
                            candidate.retentionPeriodId(),
                            archiveYear);
        } catch (DuplicateKeyException exception) {
            throw duplicateArchiveNo();
        }
        if (request.repositoryId() != null) {
            Long repositoryId = archiveMapper.getEnabledArchiveRepositoryId(request.repositoryId());
            if (repositoryId == null) {
                throw badRequest("目标业务库不存在或已禁用");
            }
            archiveMapper.updateArchiveItemRepository(recordId, repositoryId);
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
        searchProjectionSynchronizer.synchronize(recordId);
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
        return updateItemCore(
                id, request, userId, new ItemUpdateContext(null, false, Set.of(), null));
    }

    @Transactional
    public ArchiveItemDetailDto patchItem(Long id, JsonNode patch, Long userId) {
        requirePermission(userId, "archive:item:update");
        ArchiveItemDetailDto before =
                archiveItemReadService.getItemDetail(id, userId, ArchiveLayoutSurface.EDIT);
        archiveItemReadService.assertItemInDataScope(userId, before.category(), before.item());
        archiveItemReadService.ensureItemEditable(before.item());
        PreparedPatch prepared = patchParser.parse(before, patch);
        if (prepared == null) {
            return before;
        }
        return updateItemCore(
                id,
                prepared.request(),
                userId,
                new ItemUpdateContext(null, false, prepared.removedItemFields(), before));
    }

    @Transactional
    public ArchiveItemDetailDto reassignFonds(
            Long id, @Nullable ReassignArchiveItemFondsRequest request, Long userId) {
        requirePermission(userId, "archive:item:update");
        if (request == null) {
            throw badRequest("请求体不能为空");
        }
        String targetFondsCode = StringUtils.trimToNull(request.targetFondsCode());
        if (targetFondsCode == null) {
            throw badRequest("目标全宗不能为空", "targetFondsCode", "目标全宗不能为空");
        }
        String reason = StringUtils.trimToNull(request.reason());
        if (reason == null) {
            throw badRequest("调整原因不能为空", "reason", "调整原因不能为空");
        }
        ArchiveItemDetailDto before =
                archiveItemReadService.getItemDetail(id, userId, ArchiveLayoutSurface.EDIT);
        archiveItemReadService.assertItemInDataScope(userId, before.category(), before.item());
        if (before.item().volumeId() != null) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "卷内档案不能单独调整全宗");
        }
        if (targetFondsCode.equals(before.item().fondsCode())) {
            throw badRequest("目标全宗不能与原全宗相同", "targetFondsCode", "目标全宗不能与原全宗相同");
        }
        UpdateArchiveItemRequest updateRequest =
                new UpdateArchiveItemRequest(
                        null,
                        targetFondsCode,
                        before.item().archiveNo(),
                        before.item().archiveYear(),
                        before.item().securityLevelId(),
                        before.item().retentionPeriodId(),
                        null,
                        null);
        String auditReason =
                "原全宗 %s -> 目标全宗 %s；%s"
                        .formatted(before.item().fondsCode(), targetFondsCode, reason);
        return updateItemCore(
                id,
                updateRequest,
                userId,
                new ItemUpdateContext(auditReason, true, Set.of(), null));
    }

    private ArchiveItemDetailDto updateItemCore(
            Long id, UpdateArchiveItemRequest request, Long userId, ItemUpdateContext context) {
        @Nullable ArchiveItemDetailDto preparedBefore = context.preparedBefore();
        Set<String> removedItemFields = context.removedItemFields();
        ArchiveItemDetailDto before =
                preparedBefore == null
                        ? archiveItemReadService.getItemDetail(
                                id, userId, ArchiveLayoutSurface.EDIT)
                        : preparedBefore;
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
                archiveMetadataReferenceService.getWritableFondsByCode(request.fondsCode());
        boolean fondsChanged = !fonds.fondsCode().equals(before.item().fondsCode());
        if (fondsChanged
                && before.item().archivedAt() != null
                && !context.allowArchivedReassignment()) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "已归档档案必须通过调整全宗动作办理");
        }
        archiveCategoryService.requireCategoryAvailableForFonds(fonds.fondsCode(), category.id());
        Long volumeId =
                request.volumeId() == null && !removedItemFields.contains("volumeId")
                        ? before.item().volumeId()
                        : request.volumeId();
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
                ArchiveItemDynamicValues.merge(currentDynamicFields, convertedRequestDynamicFields);
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
                ArchiveItemDynamicValues.merge(
                        currentPhysicalFields, convertedRequestPhysicalFields);
        ItemPolicyExecution policyExecution =
                enforceItemPolicy(
                        new ItemPolicyCommand(
                                ArchiveRuntimeTriggerPoint.ITEM_BEFORE_UPDATE,
                                id,
                                fonds.fondsCode(),
                                fonds.fondsName(),
                                category,
                                new ItemCandidate(
                                        archiveNo,
                                        archiveYear,
                                        request.securityLevelId() == null
                                                        && !removedItemFields.contains(
                                                                "securityLevelId")
                                                ? before.item().securityLevelId()
                                                : request.securityLevelId(),
                                        request.retentionPeriodId() == null
                                                        && !removedItemFields.contains(
                                                                "retentionPeriodId")
                                                ? before.item().retentionPeriodId()
                                                : request.retentionPeriodId(),
                                        convertedDynamicFields,
                                        convertedPhysicalFields),
                                allFields,
                                allPhysicalFields,
                                userId));
        ArchiveRuntimeExecutionResult runtimeResult = policyExecution.result();
        ItemCandidate candidate =
                candidateAssembler.finalCandidate(runtimeResult, allFields, allPhysicalFields);
        archiveNo = candidate.archiveNo();
        archiveYear = candidate.archiveYear();
        convertedDynamicFields = candidate.dynamicFields();
        convertedPhysicalFields = candidate.physicalFields();
        validateArchiveYear(archiveYear);
        ensureItemArchiveNoUnique(before.item().categoryCode(), archiveNo, id);
        assertProposedItemInDataScope(userId, category, fonds.fondsCode(), allFields, candidate);
        int updated;
        try {
            updated =
                    archiveMapper.updateArchiveItem(
                            id,
                            volumeId,
                            fonds.fondsCode(),
                            fonds.fondsName(),
                            archiveNo,
                            candidate.securityLevelId(),
                            candidate.retentionPeriodId(),
                            archiveYear);
        } catch (DuplicateKeyException exception) {
            throw duplicateArchiveNo();
        }
        if (updated == 0) {
            throw badRequest("档案条目已锁定，不能修改");
        }
        if (!allFields.isEmpty()) {
            try {
                archiveMapper.updateDynamicRecord(
                        tableName,
                        id,
                        ArchiveItemDynamicValues.assignments(allFields, convertedDynamicFields));
            } catch (DuplicateKeyException | MyBatisSystemException exception) {
                throw badRequest("档案条目违反唯一约束");
            }
        }
        if (requestPhysicalFields != null || hasAssignment(runtimeResult, "physical.")) {
            upsertPhysicalFieldsIfPresent(
                    category, ArchiveLevel.ITEM, id, allPhysicalFields, convertedPhysicalFields);
        }
        searchProjectionSynchronizer.synchronize(id);
        ArchiveItemDetailDto after =
                archiveItemReadService.getItemDetail(id, userId, ArchiveLayoutSurface.EDIT);
        insertItemAudit(
                context.allowArchivedReassignment()
                        ? AUDIT_OPERATION_REASSIGN_FONDS
                        : AUDIT_OPERATION_UPDATE,
                after.item(),
                context.auditReason(),
                userId);
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
                        new ItemPolicyCommand(
                                ArchiveRuntimeTriggerPoint.ITEM_BEFORE_DELETE,
                                id,
                                record.fondsCode(),
                                record.fondsName(),
                                category,
                                new ItemCandidate(
                                        record.archiveNo(),
                                        record.archiveYear(),
                                        record.securityLevelId(),
                                        record.retentionPeriodId(),
                                        loadFieldsByCode(tableName, id, fields),
                                        loadFieldsByCode(
                                                dynamicTableName(
                                                        category,
                                                        ArchiveLevel.ITEM,
                                                        ArchiveFieldScope.PHYSICAL),
                                                id,
                                                physicalFields)),
                                fields,
                                physicalFields,
                                userId));
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
        searchProjectionSynchronizer.synchronize(id);
        runtimeTraceService.saveSuccessfulExecution(
                policyExecution.request(), policyExecution.result(), id);
    }

    private ItemPolicyExecution enforceItemPolicy(ItemPolicyCommand command) {
        ArchiveRuntimeExecutionRequest request =
                candidateAssembler.executionRequest(command, LocalDateTime.now());
        return new ItemPolicyExecution(request, runtimeExecutionService.enforce(request));
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
        return ArchiveItemDynamicValues.byFieldCode(fields, row);
    }

    private boolean hasAssignment(ArchiveRuntimeExecutionResult result, String prefix) {
        return result.assignments().keySet().stream().anyMatch(field -> field.startsWith(prefix));
    }

    private record ItemPolicyExecution(
            ArchiveRuntimeExecutionRequest request, ArchiveRuntimeExecutionResult result) {}

    private record ItemUpdateContext(
            @Nullable String auditReason,
            boolean allowArchivedReassignment,
            Set<String> removedItemFields,
            @Nullable ArchiveItemDetailDto preparedBefore) {}

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
                    tableName,
                    recordId,
                    ArchiveItemDynamicValues.assignments(fields, convertedFields));
        }
    }

    private String dynamicTableName(ArchiveCategoryDto category, ArchiveLevel archiveLevel) {
        return dynamicTableName(category, archiveLevel, ArchiveFieldScope.METADATA);
    }

    private String dynamicTableName(
            ArchiveCategoryDto category, ArchiveLevel archiveLevel, ArchiveFieldScope fieldScope) {
        return ArchiveDynamicTableNames.tableName(category, archiveLevel, fieldScope);
    }

    private boolean isDynamicTableBuilt(ArchiveCategoryDto category, ArchiveLevel archiveLevel) {
        return isDynamicTableBuilt(category, archiveLevel, ArchiveFieldScope.METADATA);
    }

    private boolean isDynamicTableBuilt(
            ArchiveCategoryDto category, ArchiveLevel archiveLevel, ArchiveFieldScope fieldScope) {
        String tableName = dynamicTableName(category, archiveLevel, fieldScope);
        return StringUtils.isNotBlank(tableName) && archiveMapper.tableExists(tableName) > 0;
    }

    private void insertDynamicRecord(
            String tableName,
            Long recordId,
            List<ArchiveFieldDto> fields,
            Map<String, @Nullable Object> convertedDynamicFields) {
        InsertValues values =
                ArchiveItemDynamicValues.insertValues(recordId, fields, convertedDynamicFields);
        archiveMapper.insertDynamicRecord(tableName, values.columns(), values.values());
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
            List<ArchiveFieldDto> fields,
            ItemCandidate candidate) {
        userId = AuthenticatedUsers.requireResolvedUserId(userId);
        ArchiveDataScopeFilter filter =
                dataScopeService.buildItemFilter(userId, category.id(), fondsCode);
        if (filter.empty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "数据范围不足");
        }
        if (filter.allData()) {
            return;
        }
        Map<String, @Nullable Object> dynamicRow =
                ArchiveItemDynamicValues.byColumnName(fields, candidate.dynamicFields());
        if (!dataScopeService.matchesItemFilter(
                filter,
                fondsCode,
                candidate.securityLevelId(),
                candidate.retentionPeriodId(),
                dynamicRow)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "数据范围不足");
        }
    }

    public record CreateArchiveItemRequest(
            @Nullable Long categoryId,
            @Nullable Long volumeId,
            @Nullable String fondsCode,
            @Nullable String archiveNo,
            @Nullable Integer archiveYear,
            @Nullable Long securityLevelId,
            @Nullable Long retentionPeriodId,
            @Nullable Map<String, @Nullable Object> physicalFields,
            @Nullable Map<String, @Nullable Object> dynamicFields,
            @Nullable Long repositoryId) {

        public CreateArchiveItemRequest(
                @Nullable Long categoryId,
                @Nullable Long volumeId,
                @Nullable String fondsCode,
                @Nullable String archiveNo,
                @Nullable Integer archiveYear,
                @Nullable Long securityLevelId,
                @Nullable Long retentionPeriodId,
                @Nullable Map<String, @Nullable Object> physicalFields,
                @Nullable Map<String, @Nullable Object> dynamicFields) {
            this(
                    categoryId,
                    volumeId,
                    fondsCode,
                    archiveNo,
                    archiveYear,
                    securityLevelId,
                    retentionPeriodId,
                    physicalFields,
                    dynamicFields,
                    null);
        }
    }

    public record UpdateArchiveItemRequest(
            @Nullable Long volumeId,
            @Nullable String fondsCode,
            @Nullable String archiveNo,
            @Nullable Integer archiveYear,
            @Nullable Long securityLevelId,
            @Nullable Long retentionPeriodId,
            @Nullable Map<String, @Nullable Object> physicalFields,
            @Nullable Map<String, @Nullable Object> dynamicFields) {}

    public record ReassignArchiveItemFondsRequest(
            @Nullable String targetFondsCode, @Nullable String reason) {}

    public record DeleteItemRequest(@Nullable String reason) {}
}
