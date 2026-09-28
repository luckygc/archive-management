package github.luckygc.am.module.archive.item.service;

import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

import github.luckygc.am.common.api.JsonMergePatch;
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeResolutionTypes.ArchiveDataScopeFilter;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService;
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

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@Service
public class ArchiveItemService {

    private static final String AUDIT_OPERATION_CREATE = "CREATE";
    private static final String AUDIT_OPERATION_UPDATE = "UPDATE";
    private static final String AUDIT_OPERATION_REASSIGN_FONDS = "REASSIGN_FONDS";
    private static final String AUDIT_OPERATION_DELETE = "DELETE";
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DEFAULT_PAGE_LIMIT = 100;
    private static final int MAX_PAGE_LIMIT = 1000;
    private static final Set<String> PATCH_TOP_FIELDS =
            Set.of("item", "dynamicFields", "physicalFieldValues");
    private static final Set<String> PATCH_TOP_READ_ONLY_FIELDS =
            Set.of("category", "fields", "physicalFields");
    private static final Set<String> PATCH_ITEM_FIELDS =
            Set.of(
                    "volumeId",
                    "fondsCode",
                    "archiveNo",
                    "archiveYear",
                    "securityLevelId",
                    "retentionPeriodId");
    private static final Set<String> PATCH_ITEM_READ_ONLY_FIELDS =
            Set.of(
                    "id",
                    "fondsName",
                    "categoryCode",
                    "categoryName",
                    "lockedFlag",
                    "lockReason",
                    "lockedBy",
                    "lockedAt",
                    "repositoryId",
                    "archivedAt");
    private static final TypeReference<Map<String, @Nullable Object>> PATCH_FIELD_VALUES_TYPE =
            new TypeReference<>() {};
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
    private final JsonMapper jsonMapper;

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
        this.jsonMapper = jsonMapper;
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
                archiveMetadataReferenceService.getWritableFondsByCode(request.fondsCode());
        archiveCategoryService.requireCategoryAvailableForFonds(fonds.fondsCode(), category.id());
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
        ItemPolicyExecution policyExecution =
                enforceItemPolicy(
                        ArchiveRuntimeTriggerPoint.ITEM_BEFORE_CREATE,
                        null,
                        volumeId,
                        fonds.fondsCode(),
                        fonds.fondsName(),
                        category,
                        archiveNo,
                        archiveYear,
                        request.securityLevelId(),
                        request.retentionPeriodId(),
                        fields,
                        convertedDynamicFields,
                        physicalFields,
                        convertedPhysicalFields,
                        userId);
        ArchiveRuntimeExecutionResult runtimeResult = policyExecution.result();
        ItemCandidate candidate = finalItemCandidate(runtimeResult, fields, physicalFields);
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
        return updateItemCore(id, request, userId, null, false, Set.of(), null);
    }

    @Transactional
    public ArchiveItemDetailDto patchItem(Long id, JsonNode patch, Long userId) {
        requirePermission(userId, "archive:item:update");
        ArchiveItemDetailDto before =
                archiveItemReadService.getItemDetail(id, userId, ArchiveLayoutSurface.EDIT);
        archiveItemReadService.assertItemInDataScope(userId, before.category(), before.item());
        archiveItemReadService.ensureItemEditable(before.item());
        requirePatchObject(patch, PATCH_TOP_FIELDS, PATCH_TOP_READ_ONLY_FIELDS, "");
        JsonNode itemPatch = patch.get("item");
        if (itemPatch != null) {
            requirePatchObject(itemPatch, PATCH_ITEM_FIELDS, PATCH_ITEM_READ_ONLY_FIELDS, "item");
        }
        for (String field : List.of("dynamicFields", "physicalFieldValues")) {
            JsonNode fieldPatch = patch.get(field);
            if (fieldPatch != null && !fieldPatch.isObject()) {
                throw new BadRequestException(field + " 补丁必须是对象", field, "字段值必须是对象");
            }
        }
        ObjectNode representation = JsonNodeFactory.instance.objectNode();
        ObjectNode item = (ObjectNode) jsonMapper.valueToTree(before.item());
        for (String field : PATCH_ITEM_READ_ONLY_FIELDS) {
            item.remove(field);
        }
        representation.set("item", item);
        representation.set(
                "dynamicFields",
                jsonMapper.valueToTree(nonNullFieldValues(before.dynamicFields())));
        representation.set(
                "physicalFieldValues",
                jsonMapper.valueToTree(nonNullFieldValues(before.physicalFieldValues())));
        JsonNode merged = JsonMergePatch.apply(representation, patch);
        if (merged.equals(representation)) {
            return before;
        }
        JsonNode mergedItem = merged.get("item");
        String fondsCode = requiredPatchText(mergedItem, "fondsCode");
        JsonNode yearNode = mergedItem.get("archiveYear");
        if (yearNode == null || !yearNode.isInt()) {
            throw new BadRequestException("archiveYear 不能删除或改变类型", "item.archiveYear", "年度不合法");
        }
        Set<String> removedItemFields = new HashSet<>();
        if (itemPatch != null) {
            for (String field : itemPatch.propertyNames()) {
                if (itemPatch.get(field).isNull()) {
                    removedItemFields.add(field);
                }
            }
        }
        UpdateArchiveItemRequest request =
                new UpdateArchiveItemRequest(
                        optionalPatchLong(mergedItem, "volumeId"),
                        fondsCode,
                        optionalPatchText(mergedItem, "archiveNo"),
                        yearNode.intValue(),
                        optionalPatchLong(mergedItem, "securityLevelId"),
                        optionalPatchLong(mergedItem, "retentionPeriodId"),
                        patch.has("physicalFieldValues")
                                ? patchFieldValues(merged.get("physicalFieldValues"))
                                : null,
                        patch.has("dynamicFields")
                                ? patchFieldValues(merged.get("dynamicFields"))
                                : null);
        return updateItemCore(id, request, userId, null, false, removedItemFields, before);
    }

    private void requirePatchObject(
            JsonNode patch, Set<String> fields, Set<String> readOnlyFields, String path) {
        if (patch == null || !patch.isObject()) {
            throw new BadRequestException(path + " 补丁必须是对象");
        }
        for (String field : patch.propertyNames()) {
            if (readOnlyFields.contains(field)
                    || (!fields.contains(field) && !patch.get(field).isNull())) {
                throw new BadRequestException(
                        "不支持修改字段 " + field, path.isEmpty() ? field : path + "." + field, "字段不可修改");
            }
        }
    }

    private String requiredPatchText(JsonNode item, String field) {
        JsonNode value = item.get(field);
        if (value == null || !value.isTextual()) {
            throw new BadRequestException(field + " 不能删除或改变类型", "item." + field, "字段不合法");
        }
        return value.asText();
    }

    private @Nullable String optionalPatchText(JsonNode item, String field) {
        JsonNode value = item.get(field);
        if (value == null) {
            return null;
        }
        if (!value.isTextual()) {
            throw new BadRequestException(field + " 类型不合法", "item." + field, "必须是字符串");
        }
        return value.asText();
    }

    private @Nullable Long optionalPatchLong(JsonNode item, String field) {
        JsonNode value = item.get(field);
        if (value == null) {
            return null;
        }
        if (!value.isIntegralNumber()) {
            throw new BadRequestException(field + " 类型不合法", "item." + field, "必须是整数");
        }
        return value.longValue();
    }

    private Map<String, @Nullable Object> patchFieldValues(JsonNode value) {
        try {
            return jsonMapper.readValue(value.toString(), PATCH_FIELD_VALUES_TYPE);
        } catch (JacksonException exception) {
            throw new BadRequestException("动态字段补丁格式不合法");
        }
    }

    private Map<String, Object> nonNullFieldValues(Map<String, @Nullable Object> values) {
        Map<String, Object> present = new LinkedHashMap<>();
        values.forEach(
                (field, value) -> {
                    if (value != null) {
                        present.put(field, value);
                    }
                });
        return present;
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
        return updateItemCore(id, updateRequest, userId, auditReason, true, Set.of(), null);
    }

    private ArchiveItemDetailDto updateItemCore(
            Long id,
            UpdateArchiveItemRequest request,
            Long userId,
            @Nullable String auditReason,
            boolean allowArchivedReassignment,
            Set<String> removedItemFields,
            @Nullable ArchiveItemDetailDto preparedBefore) {
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
        if (fondsChanged && before.item().archivedAt() != null && !allowArchivedReassignment) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "已归档档案必须通过调整全宗动作办理");
        }
        archiveCategoryService.requireCategoryAvailableForFonds(fonds.fondsCode(), category.id());
        Long volumeId =
                validateParentForWrite(
                        ArchiveLevel.ITEM,
                        request.volumeId() == null && !removedItemFields.contains("volumeId")
                                ? before.item().volumeId()
                                : request.volumeId(),
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
        ItemPolicyExecution policyExecution =
                enforceItemPolicy(
                        ArchiveRuntimeTriggerPoint.ITEM_BEFORE_UPDATE,
                        id,
                        volumeId,
                        fonds.fondsCode(),
                        fonds.fondsName(),
                        category,
                        archiveNo,
                        archiveYear,
                        request.securityLevelId() == null
                                        && !removedItemFields.contains("securityLevelId")
                                ? before.item().securityLevelId()
                                : request.securityLevelId(),
                        request.retentionPeriodId() == null
                                        && !removedItemFields.contains("retentionPeriodId")
                                ? before.item().retentionPeriodId()
                                : request.retentionPeriodId(),
                        allFields,
                        convertedDynamicFields,
                        allPhysicalFields,
                        convertedPhysicalFields,
                        userId);
        ArchiveRuntimeExecutionResult runtimeResult = policyExecution.result();
        ItemCandidate candidate = finalItemCandidate(runtimeResult, allFields, allPhysicalFields);
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
                        tableName, id, dynamicAssignments(allFields, convertedDynamicFields));
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
                allowArchivedReassignment ? AUDIT_OPERATION_REASSIGN_FONDS : AUDIT_OPERATION_UPDATE,
                after.item(),
                auditReason,
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
                        ArchiveRuntimeTriggerPoint.ITEM_BEFORE_DELETE,
                        id,
                        record.volumeId(),
                        record.fondsCode(),
                        record.fondsName(),
                        category,
                        record.archiveNo(),
                        record.archiveYear(),
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
        searchProjectionSynchronizer.synchronize(id);
        runtimeTraceService.saveSuccessfulExecution(
                policyExecution.request(), policyExecution.result(), id);
    }

    private ItemPolicyExecution enforceItemPolicy(
            ArchiveRuntimeTriggerPoint triggerPoint,
            @Nullable Long itemId,
            @Nullable Long volumeId,
            String fondsCode,
            String fondsName,
            ArchiveCategoryDto category,
            @Nullable String archiveNo,
            int archiveYear,
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
        facts.put("item.securityLevelId", securityLevelId);
        facts.put("item.retentionPeriodId", retentionPeriodId);
        addFieldFacts(facts, "metadata.", fields, dynamicFields);
        addFieldFacts(facts, "physical.", physicalFields, physicalFieldValues);
        facts.put("context.userId", userId);
        facts.put("context.now", LocalDateTime.now());
        facts.put("context.operation", triggerPoint.name());
        ArchiveRuntimeExecutionRequest request =
                new ArchiveRuntimeExecutionRequest(
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
            List<ArchiveFieldDto> physicalFields) {
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
