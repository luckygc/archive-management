package github.luckygc.am.module.archive.rule.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.api.ApiFieldViolation;
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.governance.ArchiveGovernanceScheme;
import github.luckygc.am.module.archive.governance.ArchiveGovernanceSchemeVersion;
import github.luckygc.am.module.archive.governance.ArchiveGovernanceSchemeVersionStatus;
import github.luckygc.am.module.archive.governance.ArchiveGovernanceScope;
import github.luckygc.am.module.archive.governance.ArchiveGovernanceScopeType;
import github.luckygc.am.module.archive.governance.repository.ArchiveGovernanceSchemeDataRepository;
import github.luckygc.am.module.archive.governance.repository.ArchiveGovernanceSchemeVersionDataRepository;
import github.luckygc.am.module.archive.governance.repository.ArchiveGovernanceScopeDataRepository;
import github.luckygc.am.module.archive.metadata.ArchiveFieldDataType;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeActionType;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeDefinition;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeDefinitionKind;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeStatus;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeTriggerPoint;
import github.luckygc.am.module.archive.rule.repository.ArchiveRuntimeActionDataRepository;
import github.luckygc.am.module.archive.rule.repository.ArchiveRuntimeDefinitionDataRepository;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeDefinitionService.SaveArchiveRuntimeActionRequest;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeDefinitionService.SaveArchiveRuntimeDefinitionRequest;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeFieldCatalogService.ArchiveRuntimeField;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeFieldCatalogService.ArchiveRuntimeFieldCatalog;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ArchiveRuntimeSnapshotService {

    static final String SCHEMA_VERSION = "1";
    static final int MAX_SNAPSHOT_BYTES = 1_048_576;
    static final int MAX_SNAPSHOT_NODES = 20_000;
    static final int MAX_DEFINITIONS = 1_000;
    private static final String APPLICATION_VERSION = "0.0.1";

    private final ArchiveGovernanceSchemeDataRepository schemeRepository;
    private final ArchiveGovernanceSchemeVersionDataRepository versionRepository;
    private final ArchiveGovernanceScopeDataRepository scopeRepository;
    private final ArchiveRuntimeDefinitionDataRepository definitionRepository;
    private final ArchiveRuntimeActionDataRepository actionRepository;
    private final ArchiveRuntimeDefinitionService definitionService;
    private final ArchiveRuntimeFieldCatalogService fieldCatalogService;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public ArchiveRuntimeSnapshotService(
            ArchiveGovernanceSchemeDataRepository schemeRepository,
            ArchiveGovernanceSchemeVersionDataRepository versionRepository,
            ArchiveGovernanceScopeDataRepository scopeRepository,
            ArchiveRuntimeDefinitionDataRepository definitionRepository,
            ArchiveRuntimeActionDataRepository actionRepository,
            ArchiveRuntimeDefinitionService definitionService,
            ArchiveRuntimeFieldCatalogService fieldCatalogService,
            JsonMapper jsonMapper,
            Clock clock) {
        this.schemeRepository = schemeRepository;
        this.versionRepository = versionRepository;
        this.scopeRepository = scopeRepository;
        this.definitionRepository = definitionRepository;
        this.actionRepository = actionRepository;
        this.definitionService = definitionService;
        this.fieldCatalogService = fieldCatalogService;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ArchiveRuntimeSnapshot exportSnapshot(Long schemeVersionId) {
        ArchiveGovernanceSchemeVersion version = requireVersion(schemeVersionId);
        ArchiveGovernanceScheme scheme = requireScheme(version.getSchemeId());
        SnapshotScheme snapshotScheme =
                new SnapshotScheme(
                        scheme.getSchemeCode(),
                        scheme.getSchemeName(),
                        version.getVersionCode(),
                        version.getVersionDescription(),
                        scopeRepository.findBySchemeVersionId(version.getId()).stream()
                                .map(this::toSnapshotScope)
                                .sorted(SNAPSHOT_SCOPE_ORDER)
                                .toList());
        List<SnapshotDefinition> definitions =
                definitionRepository.findBySchemeVersionId(version.getId()).stream()
                        .map(this::toSnapshotDefinition)
                        .sorted(SNAPSHOT_DEFINITION_ORDER)
                        .toList();
        String sha256 =
                digestMaterial(SCHEMA_VERSION, APPLICATION_VERSION, snapshotScheme, definitions);
        LocalDateTime exportedAt = LocalDateTime.now(clock);
        return new ArchiveRuntimeSnapshot(
                SCHEMA_VERSION,
                APPLICATION_VERSION,
                exportedAt,
                fileName(snapshotScheme, exportedAt),
                snapshotScheme,
                definitions,
                sha256);
    }

    @Transactional(readOnly = true)
    public ArchiveRuntimeSnapshotPreflightResult preflight(
            ArchiveRuntimeSnapshotPreflightRequest request) {
        return preflightCore(request, null);
    }

    @Transactional
    public ArchiveRuntimeSnapshotImportResult importAsDraft(
            ArchiveRuntimeSnapshotImportRequest request, Long userId) {
        if (request == null || request.preflight() == null) {
            throw snapshotError("导入请求不能为空", "preflight", "请先提交完整快照预检参数");
        }
        String targetVersionCode = requiredText(request.targetVersionCode(), "targetVersionCode");
        ArchiveRuntimeSnapshotPreflightResult preflight = preflightCore(request.preflight(), null);
        ArchiveGovernanceScheme scheme =
                requireSchemeByCode(resolveTargetSchemeCode(request.preflight()));
        if (versionRepository.findBySchemeIdAndVersionCode(scheme.getId(), targetVersionCode)
                != null) {
            throw snapshotError("目标治理版本编码已存在", "targetVersionCode", "请选择新的草稿版本编码");
        }
        ArchiveGovernanceSchemeVersion version = new ArchiveGovernanceSchemeVersion();
        version.setSchemeId(scheme.getId());
        version.setVersionCode(targetVersionCode);
        version.setVersionDescription(
                StringUtils.defaultIfBlank(
                        request.targetVersionDescription(),
                        request.preflight().snapshot().scheme().versionDescription()));
        version.setStatus(ArchiveGovernanceSchemeVersionStatus.DRAFT);
        ArchiveGovernanceSchemeVersion saved = versionRepository.insert(version);
        insertImportedScopes(
                saved.getId(),
                request.preflight().snapshot().scheme().scopes(),
                request.preflight());
        insertDefinitions(saved.getId(), request.preflight(), userId);
        return new ArchiveRuntimeSnapshotImportResult(
                saved.getId(),
                scheme.getSchemeCode(),
                saved.getVersionCode(),
                preflight.definitionCount(),
                preflight.fieldMappings(),
                preflight.sha256());
    }

    @Transactional
    public ArchiveRuntimeSnapshotRestoreResult restoreDraft(
            Long targetSchemeVersionId, ArchiveRuntimeSnapshotRestoreRequest request, Long userId) {
        if (request == null || request.preflight() == null) {
            throw snapshotError("恢复请求不能为空", "preflight", "请先提交完整快照预检参数");
        }
        ArchiveGovernanceSchemeVersion target = requireVersion(targetSchemeVersionId);
        if (target.getStatus() != ArchiveGovernanceSchemeVersionStatus.DRAFT) {
            throw snapshotError("只能恢复草稿治理版本", "targetSchemeVersionId", "请先创建草稿版本再恢复和发布");
        }
        List<ArchiveRuntimeDefinition> existing =
                definitionRepository.findBySchemeVersionId(targetSchemeVersionId);
        if (existing.stream()
                .anyMatch(definition -> definition.getStatus() != ArchiveRuntimeStatus.DRAFT)) {
            throw snapshotError("目标草稿包含已发布运行时定义", "targetSchemeVersionId", "请使用不含已发布定义的新草稿版本执行恢复");
        }
        ArchiveRuntimeSnapshotPreflightResult preflight =
                preflightCore(request.preflight(), targetSchemeVersionId);
        int beforeCount = existing.size();
        for (ArchiveRuntimeDefinition definition : existing) {
            definitionService.deleteDefinition(definition.getId(), userId);
        }
        insertDefinitions(targetSchemeVersionId, request.preflight(), userId);
        return new ArchiveRuntimeSnapshotRestoreResult(
                targetSchemeVersionId,
                beforeCount,
                preflight.definitionCount(),
                preflight.fieldMappings(),
                preflight.sha256());
    }

    private ArchiveRuntimeSnapshotPreflightResult preflightCore(
            ArchiveRuntimeSnapshotPreflightRequest request,
            @Nullable Long explicitTargetVersionId) {
        if (request == null || request.snapshot() == null) {
            throw snapshotError("运行时配置快照不能为空", "snapshot", "请上传完整快照");
        }
        ArchiveRuntimeSnapshot snapshot = request.snapshot();
        validateEnvelope(snapshot);
        String targetSchemeCode = resolveTargetSchemeCode(request);
        ArchiveGovernanceScheme targetScheme = requireSchemeByCode(targetSchemeCode);
        Long targetVersionId =
                explicitTargetVersionId == null
                        ? referenceVersionId(targetScheme)
                        : explicitTargetVersionId;
        if (explicitTargetVersionId != null) {
            ArchiveGovernanceSchemeVersion targetVersion = requireVersion(explicitTargetVersionId);
            if (!Objects.equals(targetVersion.getSchemeId(), targetScheme.getId())) {
                throw snapshotError("恢复目标与目标治理方案不一致", "targetSchemeCode", "目标治理方案编码必须与恢复版本所属方案一致");
            }
        }
        Map<String, String> categoryMappings = normalizedMappings(request.categoryMappings());
        Map<String, String> fieldMappings = normalizedMappings(request.fieldMappings());
        validateUniqueCodes(snapshot.definitions());
        List<SnapshotResolvedFieldMapping> resolvedMappings = new ArrayList<>();
        List<ApiFieldViolation> violations = new ArrayList<>();
        validateScopes(targetVersionId, snapshot.scheme().scopes(), categoryMappings, violations);
        for (SnapshotDefinition definition : snapshot.definitions()) {
            validateDefinition(
                    targetVersionId,
                    definition,
                    categoryMappings,
                    fieldMappings,
                    resolvedMappings,
                    violations);
        }
        if (!violations.isEmpty()) {
            throw new BadRequestException(
                    "运行时配置快照与目标环境不兼容",
                    violations,
                    "INVALID_ARGUMENT",
                    "ARCHIVE_RUNTIME_SNAPSHOT_INCOMPATIBLE");
        }
        return new ArchiveRuntimeSnapshotPreflightResult(
                true,
                targetSchemeCode,
                snapshot.definitions().size(),
                snapshot.scheme().scopes().size(),
                resolvedMappings.stream().distinct().sorted(RESOLVED_FIELD_ORDER).toList(),
                snapshot.sha256());
    }

    private void validateEnvelope(ArchiveRuntimeSnapshot snapshot) {
        if (!SCHEMA_VERSION.equals(snapshot.schemaVersion())) {
            throw snapshotError(
                    "不支持的运行时配置快照版本", "snapshot.schemaVersion", "当前仅支持版本 " + SCHEMA_VERSION);
        }
        if (snapshot.scheme() == null || snapshot.definitions() == null) {
            throw snapshotError("运行时配置快照结构不完整", "snapshot", "缺少方案或定义内容");
        }
        validateRequiredSnapshotFields(snapshot);
        if (snapshot.definitions().size() > MAX_DEFINITIONS) {
            throw snapshotError("运行时配置快照定义数量超限", "snapshot.definitions", "最多允许 1000 条定义");
        }
        byte[] serialized = serialized(snapshot);
        if (serialized.length > MAX_SNAPSHOT_BYTES) {
            throw snapshotError("运行时配置快照大小超限", "snapshot", "快照不得超过 1 MiB");
        }
        JsonNode tree = readTree(serialized);
        if (countNodes(tree, MAX_SNAPSHOT_NODES + 1) > MAX_SNAPSHOT_NODES) {
            throw snapshotError("运行时配置快照节点数量超限", "snapshot", "快照 JSON 节点不得超过 20000");
        }
        String expected =
                digestMaterial(
                        snapshot.schemaVersion(),
                        snapshot.sourceApplicationVersion(),
                        snapshot.scheme(),
                        snapshot.definitions());
        if (StringUtils.isBlank(snapshot.sha256())
                || !MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.US_ASCII),
                        snapshot.sha256().getBytes(StandardCharsets.US_ASCII))) {
            throw new BadRequestException(
                    "运行时配置快照摘要不匹配",
                    List.of(new ApiFieldViolation("snapshot.sha256", "快照可能已损坏或被篡改")),
                    "INVALID_ARGUMENT",
                    "ARCHIVE_RUNTIME_SNAPSHOT_DIGEST_MISMATCH");
        }
    }

    private void validateRequiredSnapshotFields(ArchiveRuntimeSnapshot snapshot) {
        if (StringUtils.isBlank(snapshot.sourceApplicationVersion())) {
            throw snapshotError("运行时配置快照结构不完整", "snapshot.sourceApplicationVersion", "来源应用版本不能为空");
        }
        SnapshotScheme scheme = snapshot.scheme();
        if (StringUtils.isAnyBlank(
                scheme.schemeCode(), scheme.schemeName(), scheme.versionCode())) {
            throw snapshotError("运行时配置快照结构不完整", "snapshot.scheme", "方案编码、方案名称和版本编码不能为空");
        }
        for (int index = 0; index < scheme.scopes().size(); index++) {
            SnapshotScope scope = scheme.scopes().get(index);
            if (scope == null || scope.scopeType() == null) {
                throw snapshotError(
                        "运行时配置快照结构不完整", "snapshot.scheme.scopes[" + index + "]", "作用域类型不能为空");
            }
        }
        for (int index = 0; index < snapshot.definitions().size(); index++) {
            SnapshotDefinition definition = snapshot.definitions().get(index);
            String path = "snapshot.definitions[" + index + "]";
            if (definition == null
                    || definition.definitionKind() == null
                    || StringUtils.isAnyBlank(
                            definition.definitionCode(), definition.definitionName())
                    || definition.triggerPoint() == null
                    || definition.sourceStatus() == null) {
                throw snapshotError("运行时配置快照结构不完整", path, "定义类型、编码、名称、触发点和来源状态不能为空");
            }
            for (int actionIndex = 0; actionIndex < definition.actions().size(); actionIndex++) {
                if (definition.actions().get(actionIndex) == null
                        || definition.actions().get(actionIndex).actionType() == null) {
                    throw snapshotError(
                            "运行时配置快照结构不完整", path + ".actions[" + actionIndex + "]", "动作类型不能为空");
                }
            }
            for (int fieldIndex = 0;
                    fieldIndex < definition.fieldReferences().size();
                    fieldIndex++) {
                SnapshotFieldReference field = definition.fieldReferences().get(fieldIndex);
                if (field == null
                        || StringUtils.isBlank(field.fieldCode())
                        || field.dataType() == null) {
                    throw snapshotError(
                            "运行时配置快照结构不完整",
                            path + ".fieldReferences[" + fieldIndex + "]",
                            "字段编码和数据类型不能为空");
                }
            }
        }
    }

    private void validateScopes(
            Long targetVersionId,
            List<SnapshotScope> scopes,
            Map<String, String> categoryMappings,
            List<ApiFieldViolation> violations) {
        Set<String> uniqueScopes = new LinkedHashSet<>();
        for (int index = 0; index < scopes.size(); index++) {
            SnapshotScope scope = scopes.get(index);
            String targetCategory = mapped(scope.categoryCode(), categoryMappings);
            boolean validShape =
                    switch (scope.scopeType()) {
                        case GLOBAL -> scope.fondsCode() == null && targetCategory == null;
                        case FONDS ->
                                StringUtils.isNotBlank(scope.fondsCode()) && targetCategory == null;
                        case CATEGORY -> StringUtils.isNotBlank(targetCategory);
                    };
            String path = "snapshot.scheme.scopes[" + index + "]";
            if (!validShape) {
                violations.add(new ApiFieldViolation(path, "治理作用域结构不合法"));
                continue;
            }
            String key =
                    scope.scopeType()
                            + "|"
                            + Objects.toString(scope.fondsCode(), "")
                            + "|"
                            + Objects.toString(targetCategory, "");
            if (!uniqueScopes.add(key)) {
                violations.add(new ApiFieldViolation(path, "治理作用域映射后重复"));
            }
            if (targetCategory != null) {
                try {
                    fieldCatalogService.catalog(
                            targetVersionId,
                            targetCategory,
                            ArchiveRuntimeTriggerPoint.ITEM_BEFORE_CREATE);
                } catch (RuntimeException exception) {
                    violations.add(
                            new ApiFieldViolation(
                                    path + ".categoryCode",
                                    exception.getMessage() == null
                                            ? "目标分类不可用"
                                            : exception.getMessage()));
                }
            }
        }
    }

    private void validateUniqueCodes(List<SnapshotDefinition> definitions) {
        Set<String> codes = new LinkedHashSet<>();
        for (int index = 0; index < definitions.size(); index++) {
            String code = definitions.get(index).definitionCode();
            if (!codes.add(code)) {
                throw snapshotError(
                        "运行时配置快照包含重复定义编码",
                        "snapshot.definitions[" + index + "].definitionCode",
                        "定义编码必须唯一");
            }
        }
    }

    private void validateDefinition(
            Long targetVersionId,
            SnapshotDefinition definition,
            Map<String, String> categoryMappings,
            Map<String, String> fieldMappings,
            List<SnapshotResolvedFieldMapping> resolvedMappings,
            List<ApiFieldViolation> violations) {
        Set<String> referencedCodes = new LinkedHashSet<>();
        collectFieldCodes(definition.conditionJson(), referencedCodes);
        definition
                .actions()
                .forEach(action -> collectFieldCodes(action.actionParams(), referencedCodes));
        Set<String> declaredCodes =
                definition.fieldReferences().stream()
                        .map(SnapshotFieldReference::fieldCode)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!referencedCodes.equals(declaredCodes)) {
            violations.add(
                    new ApiFieldViolation(
                            "definitions." + definition.definitionCode() + ".fieldReferences",
                            "字段引用清单与条件或动作中的实际引用不一致"));
        }
        String targetCategory = mapped(definition.scopeCategoryCode(), categoryMappings);
        ArchiveRuntimeFieldCatalog catalog;
        try {
            catalog =
                    fieldCatalogService.catalog(
                            targetVersionId, targetCategory, definition.triggerPoint());
        } catch (RuntimeException exception) {
            violations.add(
                    new ApiFieldViolation(
                            "definitions." + definition.definitionCode() + ".scopeCategoryCode",
                            exception.getMessage() == null ? "目标分类不可用" : exception.getMessage()));
            return;
        }
        Map<String, ArchiveRuntimeField> targetFields = catalog.fieldsByCode();
        for (SnapshotFieldReference sourceField : definition.fieldReferences()) {
            String targetFieldCode =
                    fieldMappings.getOrDefault(sourceField.fieldCode(), sourceField.fieldCode());
            ArchiveRuntimeField targetField = targetFields.get(targetFieldCode);
            if (targetField == null || !targetField.readable()) {
                violations.add(
                        new ApiFieldViolation(
                                "definitions."
                                        + definition.definitionCode()
                                        + ".fields."
                                        + sourceField.fieldCode(),
                                "目标字段不存在、已停用或不可读：" + targetFieldCode));
                continue;
            }
            if (targetField.dataType() != sourceField.dataType()) {
                violations.add(
                        new ApiFieldViolation(
                                "definitions."
                                        + definition.definitionCode()
                                        + ".fields."
                                        + sourceField.fieldCode(),
                                "字段类型不兼容：源为 "
                                        + sourceField.dataType()
                                        + "，目标为 "
                                        + targetField.dataType()));
                continue;
            }
            resolvedMappings.add(
                    new SnapshotResolvedFieldMapping(
                            definition.definitionCode(),
                            definition.scopeCategoryCode(),
                            targetCategory,
                            sourceField.fieldCode(),
                            targetFieldCode,
                            targetField.dataType()));
        }
        SaveArchiveRuntimeDefinitionRequest saveRequest =
                toSaveRequest(targetVersionId, definition, categoryMappings, fieldMappings);
        try {
            definitionService.validatePortableDefinition(saveRequest);
        } catch (RuntimeException exception) {
            violations.add(
                    new ApiFieldViolation(
                            "definitions." + definition.definitionCode(),
                            exception.getMessage() == null ? "定义不兼容" : exception.getMessage()));
        }
    }

    private void insertImportedScopes(
            Long targetVersionId,
            List<SnapshotScope> scopes,
            ArchiveRuntimeSnapshotPreflightRequest request) {
        Map<String, String> categoryMappings = normalizedMappings(request.categoryMappings());
        List<ArchiveGovernanceScope> imported =
                scopes.stream()
                        .map(
                                source -> {
                                    ArchiveGovernanceScope target = new ArchiveGovernanceScope();
                                    target.setSchemeVersionId(targetVersionId);
                                    target.setScopeType(source.scopeType());
                                    target.setFondsCode(source.fondsCode());
                                    target.setCategoryCode(
                                            mapped(source.categoryCode(), categoryMappings));
                                    target.setDefaultFlag(false);
                                    return target;
                                })
                        .toList();
        if (!imported.isEmpty()) scopeRepository.insertAll(imported);
    }

    private void insertDefinitions(
            Long targetVersionId, ArchiveRuntimeSnapshotPreflightRequest request, Long userId) {
        Map<String, String> categoryMappings = normalizedMappings(request.categoryMappings());
        Map<String, String> fieldMappings = normalizedMappings(request.fieldMappings());
        request.snapshot().definitions().stream()
                .sorted(SNAPSHOT_DEFINITION_ORDER)
                .forEach(
                        definition ->
                                definitionService.createDefinition(
                                        toSaveRequest(
                                                targetVersionId,
                                                definition,
                                                categoryMappings,
                                                fieldMappings),
                                        userId));
    }

    private SaveArchiveRuntimeDefinitionRequest toSaveRequest(
            Long targetVersionId,
            SnapshotDefinition definition,
            Map<String, String> categoryMappings,
            Map<String, String> fieldMappings) {
        List<SaveArchiveRuntimeActionRequest> actions =
                definition.actions().stream()
                        .map(
                                action ->
                                        new SaveArchiveRuntimeActionRequest(
                                                action.actionType(),
                                                action.actionOrder(),
                                                rewriteMap(action.actionParams(), fieldMappings)))
                        .toList();
        return new SaveArchiveRuntimeDefinitionRequest(
                targetVersionId,
                definition.definitionKind(),
                definition.definitionCode(),
                definition.definitionName(),
                definition.triggerPoint(),
                definition.scopeFondsCode(),
                mapped(definition.scopeCategoryCode(), categoryMappings),
                definition.scopeArchiveLevel(),
                definition.priority(),
                rewriteMap(definition.conditionJson(), fieldMappings),
                definition.constraintAction(),
                definition.constraintMessage(),
                definition.enabled(),
                actions);
    }

    private SnapshotDefinition toSnapshotDefinition(ArchiveRuntimeDefinition definition) {
        List<SnapshotAction> actions =
                actionRepository.findByDefinitionId(definition.getId()).stream()
                        .map(
                                action ->
                                        new SnapshotAction(
                                                action.getActionType(),
                                                action.getActionOrder(),
                                                normalizeMap(action.getActionParams())))
                        .sorted(SNAPSHOT_ACTION_ORDER)
                        .toList();
        Set<String> referencedCodes = new LinkedHashSet<>();
        collectFieldCodes(definition.getConditionJson(), referencedCodes);
        actions.forEach(action -> collectFieldCodes(action.actionParams(), referencedCodes));
        ArchiveRuntimeFieldCatalog catalog =
                fieldCatalogService.catalog(
                        definition.getSchemeVersionId(),
                        definition.getScopeCategoryCode(),
                        definition.getTriggerPoint());
        List<SnapshotFieldReference> fieldReferences =
                referencedCodes.stream()
                        .sorted()
                        .map(
                                code -> {
                                    ArchiveRuntimeField field = catalog.fieldsByCode().get(code);
                                    if (field == null) {
                                        throw snapshotError(
                                                "运行时定义引用了失效字段",
                                                "definitions."
                                                        + definition.getDefinitionCode()
                                                        + ".fields."
                                                        + code,
                                                "请修复定义后再导出");
                                    }
                                    return new SnapshotFieldReference(
                                            code,
                                            field.dataType(),
                                            field.readable(),
                                            field.writable());
                                })
                        .toList();
        return new SnapshotDefinition(
                definition.getDefinitionKind(),
                definition.getDefinitionCode(),
                definition.getDefinitionName(),
                definition.getTriggerPoint(),
                definition.getScopeFondsCode(),
                definition.getScopeCategoryCode(),
                definition.getScopeArchiveLevel(),
                definition.getPriority(),
                normalizeMap(definition.getConditionJson()),
                definition.getConstraintAction(),
                definition.getConstraintMessage(),
                definition.getStatus(),
                definition.isEnabled(),
                fieldReferences,
                actions);
    }

    private SnapshotScope toSnapshotScope(ArchiveGovernanceScope scope) {
        return new SnapshotScope(
                scope.getScopeType(),
                scope.getFondsCode(),
                scope.getCategoryCode(),
                scope.isDefaultFlag());
    }

    private void collectFieldCodes(@Nullable Object value, Set<String> result) {
        if (value instanceof Map<?, ?> map) {
            Object field = map.get("field");
            if (field instanceof String fieldCode) result.add(fieldCode);
            map.values().forEach(child -> collectFieldCodes(child, result));
        } else if (value instanceof Iterable<?> iterable) {
            iterable.forEach(child -> collectFieldCodes(child, result));
        }
    }

    private Map<String, Object> rewriteMap(
            Map<String, Object> source, Map<String, String> fieldMappings) {
        Object rewritten = rewriteValue(source, fieldMappings, null);
        if (!(rewritten instanceof Map<?, ?> map)) {
            throw snapshotError("快照 JSON 对象结构不合法", "snapshot", "条件和动作参数必须为对象");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return Collections.unmodifiableMap(result);
    }

    private Object rewriteValue(
            @Nullable Object value, Map<String, String> fieldMappings, @Nullable String parentKey) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new TreeMap<>();
            map.forEach(
                    (key, child) -> {
                        String name = String.valueOf(key);
                        result.put(name, rewriteValue(child, fieldMappings, name));
                    });
            return Collections.unmodifiableMap(result);
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> result = new ArrayList<>();
            iterable.forEach(child -> result.add(rewriteValue(child, fieldMappings, null)));
            return List.copyOf(result);
        }
        if ("field".equals(parentKey) && value instanceof String fieldCode) {
            return fieldMappings.getOrDefault(fieldCode, fieldCode);
        }
        return value;
    }

    private Map<String, Object> normalizeMap(Map<String, Object> source) {
        return rewriteMap(source, Map.of());
    }

    private Map<String, String> normalizedMappings(@Nullable Map<String, String> mappings) {
        if (mappings == null || mappings.isEmpty()) return Map.of();
        Map<String, String> result = new TreeMap<>();
        mappings.forEach(
                (source, target) -> {
                    String normalizedSource = StringUtils.trimToNull(source);
                    String normalizedTarget = StringUtils.trimToNull(target);
                    if (normalizedSource == null || normalizedTarget == null) {
                        throw snapshotError("快照映射不能为空", "mappings", "源编码和目标编码均不能为空");
                    }
                    result.put(normalizedSource, normalizedTarget);
                });
        return Map.copyOf(result);
    }

    private @Nullable String mapped(@Nullable String source, Map<String, String> mappings) {
        return source == null ? null : mappings.getOrDefault(source, source);
    }

    private String resolveTargetSchemeCode(ArchiveRuntimeSnapshotPreflightRequest request) {
        return StringUtils.defaultIfBlank(
                request.targetSchemeCode(), request.snapshot().scheme().schemeCode());
    }

    private Long referenceVersionId(ArchiveGovernanceScheme scheme) {
        return versionRepository.findBySchemeId(scheme.getId()).stream()
                .findFirst()
                .map(ArchiveGovernanceSchemeVersion::getId)
                .orElseThrow(
                        () ->
                                snapshotError(
                                        "目标治理方案没有可用于字段预检的版本",
                                        "targetSchemeCode",
                                        "请先为目标治理方案创建一个草稿版本"));
    }

    private ArchiveGovernanceSchemeVersion requireVersion(Long id) {
        return versionRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "治理版本不存在"));
    }

    private ArchiveGovernanceScheme requireScheme(Long id) {
        return schemeRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "治理方案不存在"));
    }

    private ArchiveGovernanceScheme requireSchemeByCode(String schemeCode) {
        ArchiveGovernanceScheme scheme = schemeRepository.findBySchemeCode(schemeCode);
        if (scheme == null) {
            throw snapshotError("目标治理方案不存在", "targetSchemeCode", "未找到治理方案编码：" + schemeCode);
        }
        return scheme;
    }

    private String requiredText(@Nullable String value, String field) {
        String result = StringUtils.trimToNull(value);
        if (result == null) throw snapshotError("目标版本编码不能为空", field, "请输入目标草稿版本编码");
        return result;
    }

    private String fileName(SnapshotScheme scheme, LocalDateTime exportedAt) {
        String timestamp = exportedAt.toString().replace(":", "");
        return "archive-runtime-"
                + scheme.schemeCode()
                + "-"
                + scheme.versionCode()
                + "-"
                + timestamp
                + ".json";
    }

    private String digestMaterial(
            String schemaVersion,
            String sourceApplicationVersion,
            SnapshotScheme scheme,
            List<SnapshotDefinition> definitions) {
        SnapshotScheme canonicalScheme =
                new SnapshotScheme(
                        scheme.schemeCode(),
                        scheme.schemeName(),
                        scheme.versionCode(),
                        scheme.versionDescription(),
                        scheme.scopes().stream().sorted(SNAPSHOT_SCOPE_ORDER).toList());
        List<SnapshotDefinition> canonicalDefinitions =
                definitions.stream()
                        .map(this::canonicalDefinition)
                        .sorted(SNAPSHOT_DEFINITION_ORDER)
                        .toList();
        SnapshotDigestMaterial material =
                new SnapshotDigestMaterial(
                        schemaVersion,
                        sourceApplicationVersion,
                        canonicalScheme,
                        canonicalDefinitions);
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(serialized(material)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 不支持 SHA-256", exception);
        }
    }

    private SnapshotDefinition canonicalDefinition(SnapshotDefinition definition) {
        return new SnapshotDefinition(
                definition.definitionKind(),
                definition.definitionCode(),
                definition.definitionName(),
                definition.triggerPoint(),
                definition.scopeFondsCode(),
                definition.scopeCategoryCode(),
                definition.scopeArchiveLevel(),
                definition.priority(),
                normalizeMap(definition.conditionJson()),
                definition.constraintAction(),
                definition.constraintMessage(),
                definition.sourceStatus(),
                definition.enabled(),
                definition.fieldReferences().stream()
                        .sorted(Comparator.comparing(SnapshotFieldReference::fieldCode))
                        .toList(),
                definition.actions().stream()
                        .map(this::canonicalAction)
                        .sorted(SNAPSHOT_ACTION_ORDER)
                        .toList());
    }

    private SnapshotAction canonicalAction(SnapshotAction action) {
        return new SnapshotAction(
                action.actionType(), action.actionOrder(), normalizeMap(action.actionParams()));
    }

    private byte[] serialized(Object value) {
        try {
            return jsonMapper.writeValueAsBytes(value);
        } catch (Exception exception) {
            throw snapshotError("运行时配置快照无法序列化", "snapshot", "快照包含不支持的 JSON 值");
        }
    }

    private JsonNode readTree(byte[] serialized) {
        try {
            return jsonMapper.readTree(serialized);
        } catch (Exception exception) {
            throw snapshotError("运行时配置快照不是有效 JSON", "snapshot", "无法解析快照");
        }
    }

    private int countNodes(JsonNode node, int limit) {
        int count = 1;
        var children = node.iterator();
        while (children.hasNext() && count < limit) {
            count += countNodes(children.next(), limit - count);
        }
        return count;
    }

    private BadRequestException snapshotError(String message, String field, String description) {
        return new BadRequestException(
                message,
                List.of(new ApiFieldViolation(field, description)),
                "INVALID_ARGUMENT",
                "ARCHIVE_RUNTIME_SNAPSHOT_INVALID");
    }

    private static final Comparator<SnapshotScope> SNAPSHOT_SCOPE_ORDER =
            Comparator.comparing((SnapshotScope scope) -> scope.scopeType().name())
                    .thenComparing(scope -> Objects.toString(scope.fondsCode(), ""))
                    .thenComparing(scope -> Objects.toString(scope.categoryCode(), ""))
                    .thenComparing(SnapshotScope::defaultFlag);
    private static final Comparator<SnapshotDefinition> SNAPSHOT_DEFINITION_ORDER =
            Comparator.comparingInt(SnapshotDefinition::priority)
                    .thenComparing(SnapshotDefinition::definitionCode);
    private static final Comparator<SnapshotAction> SNAPSHOT_ACTION_ORDER =
            Comparator.comparingInt(SnapshotAction::actionOrder)
                    .thenComparing(action -> action.actionType().name());
    private static final Comparator<SnapshotResolvedFieldMapping> RESOLVED_FIELD_ORDER =
            Comparator.comparing(SnapshotResolvedFieldMapping::definitionCode)
                    .thenComparing(SnapshotResolvedFieldMapping::sourceFieldCode);

    private record SnapshotDigestMaterial(
            String schemaVersion,
            String sourceApplicationVersion,
            SnapshotScheme scheme,
            List<SnapshotDefinition> definitions) {}

    public record ArchiveRuntimeSnapshot(
            String schemaVersion,
            String sourceApplicationVersion,
            LocalDateTime exportedAt,
            String fileName,
            SnapshotScheme scheme,
            List<SnapshotDefinition> definitions,
            String sha256) {
        public ArchiveRuntimeSnapshot {
            definitions = definitions == null ? null : List.copyOf(definitions);
        }
    }

    public record SnapshotScheme(
            String schemeCode,
            String schemeName,
            String versionCode,
            @Nullable String versionDescription,
            List<SnapshotScope> scopes) {
        public SnapshotScheme {
            scopes = scopes == null ? List.of() : List.copyOf(scopes);
        }
    }

    public record SnapshotScope(
            ArchiveGovernanceScopeType scopeType,
            @Nullable String fondsCode,
            @Nullable String categoryCode,
            boolean defaultFlag) {}

    public record SnapshotDefinition(
            ArchiveRuntimeDefinitionKind definitionKind,
            String definitionCode,
            String definitionName,
            ArchiveRuntimeTriggerPoint triggerPoint,
            @Nullable String scopeFondsCode,
            @Nullable String scopeCategoryCode,
            @Nullable ArchiveLevel scopeArchiveLevel,
            int priority,
            Map<String, Object> conditionJson,
            @Nullable ArchiveRuntimeActionType constraintAction,
            @Nullable String constraintMessage,
            ArchiveRuntimeStatus sourceStatus,
            boolean enabled,
            List<SnapshotFieldReference> fieldReferences,
            List<SnapshotAction> actions) {
        public SnapshotDefinition {
            conditionJson = conditionJson == null ? Map.of() : conditionJson;
            fieldReferences = fieldReferences == null ? List.of() : List.copyOf(fieldReferences);
            actions = actions == null ? List.of() : List.copyOf(actions);
        }
    }

    public record SnapshotFieldReference(
            String fieldCode, ArchiveFieldDataType dataType, boolean readable, boolean writable) {}

    public record SnapshotAction(
            ArchiveRuntimeActionType actionType,
            int actionOrder,
            Map<String, Object> actionParams) {
        public SnapshotAction {
            actionParams = actionParams == null ? Map.of() : actionParams;
        }
    }

    public record ArchiveRuntimeSnapshotPreflightRequest(
            ArchiveRuntimeSnapshot snapshot,
            @Nullable String targetSchemeCode,
            @Nullable Map<String, String> categoryMappings,
            @Nullable Map<String, String> fieldMappings) {}

    public record ArchiveRuntimeSnapshotPreflightResult(
            boolean compatible,
            String targetSchemeCode,
            int definitionCount,
            int scopeCount,
            List<SnapshotResolvedFieldMapping> fieldMappings,
            String sha256) {}

    public record SnapshotResolvedFieldMapping(
            String definitionCode,
            @Nullable String sourceCategoryCode,
            @Nullable String targetCategoryCode,
            String sourceFieldCode,
            String targetFieldCode,
            ArchiveFieldDataType dataType) {}

    public record ArchiveRuntimeSnapshotImportRequest(
            ArchiveRuntimeSnapshotPreflightRequest preflight,
            String targetVersionCode,
            @Nullable String targetVersionDescription) {}

    public record ArchiveRuntimeSnapshotImportResult(
            Long schemeVersionId,
            String schemeCode,
            String versionCode,
            int definitionCount,
            List<SnapshotResolvedFieldMapping> fieldMappings,
            String sha256) {}

    public record ArchiveRuntimeSnapshotRestoreRequest(
            ArchiveRuntimeSnapshotPreflightRequest preflight) {}

    public record ArchiveRuntimeSnapshotRestoreResult(
            Long schemeVersionId,
            int beforeDefinitionCount,
            int afterDefinitionCount,
            List<SnapshotResolvedFieldMapping> fieldMappings,
            String sha256) {}
}
