package github.luckygc.am.module.archive.authorization.service;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.authorization.ArchiveDataScope;
import github.luckygc.am.module.archive.authorization.ArchiveDataScopeDimension;
import github.luckygc.am.module.archive.authorization.ArchiveDataScopeDimensionType;
import github.luckygc.am.module.archive.authorization.ArchiveDataScopeDynamicCondition;
import github.luckygc.am.module.archive.authorization.ArchiveDataScopeSubjectRelation;
import github.luckygc.am.module.archive.authorization.ArchiveDataScopeSubjectType;
import github.luckygc.am.module.archive.authorization.ArchiveDataScopeType;
import github.luckygc.am.module.archive.authorization.repository.ArchiveDataScopeDataRepository;
import github.luckygc.am.module.archive.authorization.repository.ArchiveDataScopeDimensionDataRepository;
import github.luckygc.am.module.archive.authorization.repository.ArchiveDataScopeSubjectRelationDataRepository;
import github.luckygc.am.module.archive.item.ArchiveItemQueryOperator;
import github.luckygc.am.module.archive.mapper.ArchiveDataScopeSqlGroup;
import github.luckygc.am.module.archive.mapper.ArchiveSqlCondition;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveFieldDto;
import github.luckygc.am.module.authorization.AuthorizationRole;
import github.luckygc.am.module.authorization.AuthorizationUserRoleRelation;
import github.luckygc.am.module.authorization.repository.AuthorizationRoleDataRepository;
import github.luckygc.am.module.authorization.repository.AuthorizationUserRoleRelationDataRepository;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@Service
public class ArchiveDataScopeService {

    private final ArchiveDataScopeDataRepository dataScopeRepository;
    private final ArchiveDataScopeDimensionDataRepository dimensionRepository;
    private final ArchiveDataScopeSubjectRelationDataRepository subjectRelationRepository;
    private final AuthorizationRoleDataRepository roleRepository;
    private final AuthorizationUserRoleRelationDataRepository userRoleRelationRepository;
    private final ArchiveMetadataService archiveMetadataService;

    public ArchiveDataScopeService(
            ArchiveDataScopeDataRepository dataScopeRepository,
            ArchiveDataScopeDimensionDataRepository dimensionRepository,
            ArchiveDataScopeSubjectRelationDataRepository subjectRelationRepository,
            AuthorizationRoleDataRepository roleRepository,
            AuthorizationUserRoleRelationDataRepository userRoleRelationRepository,
            ArchiveMetadataService archiveMetadataService) {
        this.dataScopeRepository = dataScopeRepository;
        this.dimensionRepository = dimensionRepository;
        this.subjectRelationRepository = subjectRelationRepository;
        this.roleRepository = roleRepository;
        this.userRoleRelationRepository = userRoleRelationRepository;
        this.archiveMetadataService = archiveMetadataService;
    }

    public void validateScopeDefinition(
            ArchiveDataScope scope, List<ArchiveDataScopeDimension> dimensions) {
        boolean hasDimensions = !dimensions.isEmpty();
        boolean hasDynamicConditions = hasDynamicConditions(scope.getDynamicCondition());
        if (scope.getScopeType() == ArchiveDataScopeType.ALL) {
            if (hasDimensions || hasDynamicConditions) {
                throw new BadRequestException("任意范围不能配置条件", "scopeType", "任意范围不能配置条件");
            }
            return;
        }
        if (!hasDimensions && !hasDynamicConditions) {
            throw new BadRequestException("条件范围至少需要一个条件", "scopeType", "条件范围至少需要一个条件");
        }
        validateDimensions(dimensions);
        validateDynamicConditions(scope.getDynamicCondition());
    }

    @Transactional(readOnly = true)
    public List<ArchiveDataScopeResponse> listScopes(boolean enabled) {
        return dataScopeRepository.list(enabled).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ArchiveDataScopeResponse getScope(Long scopeId) {
        return toResponse(loadScope(scopeId));
    }

    @Transactional
    public ArchiveDataScopeResponse createScope(CreateArchiveDataScopeRequest request) {
        ArchiveDataScope scope = new ArchiveDataScope();
        applyScopeFields(
                scope,
                request.scopeCode(),
                request.scopeName(),
                request.scopeType(),
                request.dynamicCondition(),
                request.enabled(),
                request.description());
        List<ArchiveDataScopeDimension> dimensions = toDimensions(null, request.dimensions());
        validateScopeDefinition(scope, dimensions);
        scope = dataScopeRepository.insert(scope);
        insertDimensions(scope.getId(), dimensions);
        return toResponse(scope);
    }

    @Transactional
    public ArchiveDataScopeResponse updateScope(
            Long scopeId, UpdateArchiveDataScopeRequest request) {
        ArchiveDataScope scope = loadScope(scopeId);
        applyScopeFields(
                scope,
                request.scopeCode(),
                request.scopeName(),
                request.scopeType(),
                request.dynamicCondition(),
                request.enabled(),
                request.description());
        List<ArchiveDataScopeDimension> dimensions = toDimensions(scopeId, request.dimensions());
        validateScopeDefinition(scope, dimensions);
        scope = dataScopeRepository.update(scope);
        dimensionRepository.deleteByScopeId(scopeId);
        insertDimensions(scopeId, dimensions);
        return toResponse(scope);
    }

    @Transactional
    public RoleArchiveDataScopesResponse saveRoleDataScopes(Long roleId, List<Long> scopeIds) {
        AuthorizationRole role =
                roleRepository
                        .findById(roleId)
                        .orElseThrow(() -> new BadRequestException("角色不存在", "roleId", "角色不存在"));
        if (!role.isEnabled()) {
            throw new BadRequestException("角色已停用", "roleId", "角色已停用");
        }
        List<Long> normalizedScopeIds =
                scopeIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
        for (Long scopeId : normalizedScopeIds) {
            ArchiveDataScope scope = loadScope(scopeId);
            if (!scope.isEnabled()) {
                throw new BadRequestException("数据范围已停用", "scopeIds", "数据范围已停用: " + scopeId);
            }
        }
        saveSubjectDataScopes(ArchiveDataScopeSubjectType.ROLE, roleId, normalizedScopeIds);
        return listRoleDataScopes(roleId);
    }

    @Transactional(readOnly = true)
    public RoleArchiveDataScopesResponse listRoleDataScopes(Long roleId) {
        AuthorizationRole role =
                roleRepository
                        .findById(roleId)
                        .orElseThrow(() -> new BadRequestException("角色不存在", "roleId", "角色不存在"));
        if (!role.isEnabled()) {
            return new RoleArchiveDataScopesResponse(roleId, List.of());
        }
        List<Long> scopeIds = listSubjectScopeIds(ArchiveDataScopeSubjectType.ROLE, roleId);
        return new RoleArchiveDataScopesResponse(roleId, scopeIds);
    }

    @Transactional
    public UserArchiveDataScopesResponse saveUserDataScopes(Long userId, List<Long> scopeIds) {
        List<Long> normalizedScopeIds =
                scopeIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
        for (Long scopeId : normalizedScopeIds) {
            ArchiveDataScope scope = loadScope(scopeId);
            if (!scope.isEnabled()) {
                throw new BadRequestException("数据范围已停用", "scopeIds", "数据范围已停用: " + scopeId);
            }
        }
        saveSubjectDataScopes(ArchiveDataScopeSubjectType.USER, userId, normalizedScopeIds);
        return listUserDataScopes(userId);
    }

    @Transactional(readOnly = true)
    public UserArchiveDataScopesResponse listUserDataScopes(Long userId) {
        return new UserArchiveDataScopesResponse(
                userId, listSubjectScopeIds(ArchiveDataScopeSubjectType.USER, userId));
    }

    @Transactional(readOnly = true)
    public ResolvedArchiveDataScope resolveUserDataScope(Long userId) {
        List<ResolvedScope> scopes = new ArrayList<>();
        if (appendSubjectScopes(ArchiveDataScopeSubjectType.USER, userId, scopes)) {
            return ResolvedArchiveDataScope.all();
        }
        for (AuthorizationUserRoleRelation userRole :
                userRoleRelationRepository.findByUserId(userId)) {
            AuthorizationRole role = roleRepository.findById(userRole.getRoleId()).orElse(null);
            if (role == null || !role.isEnabled()) {
                continue;
            }
            if (AuthorizationPermissionService.SUPER_ADMIN_ROLE_NAME.equals(role.getRoleName())) {
                return ResolvedArchiveDataScope.all();
            }
            if (appendSubjectScopes(ArchiveDataScopeSubjectType.ROLE, role.getId(), scopes)) {
                return ResolvedArchiveDataScope.all();
            }
        }
        return scopes.isEmpty()
                ? ResolvedArchiveDataScope.none()
                : ResolvedArchiveDataScope.conditional(scopes);
    }

    @Transactional(readOnly = true)
    public ArchiveDataScopeFilter buildItemFilter(
            Long userId, Long categoryId, @Nullable String requestedFondsCode) {
        ResolvedArchiveDataScope resolved = resolveUserDataScope(userId);
        if (resolved.allData()) {
            return ArchiveDataScopeFilter.all();
        }
        if (resolved.empty()) {
            return ArchiveDataScopeFilter.none();
        }
        List<ArchiveDataScopeSqlGroup> groups = new ArrayList<>();
        String fondsCode = StringUtils.trimToNull(requestedFondsCode);
        Map<Long, ArchiveCategoryDto> categoriesById =
                archiveMetadataService.listCategories(true).stream()
                        .collect(Collectors.toMap(ArchiveCategoryDto::id, category -> category));
        Map<String, ArchiveFieldDto> fieldsByCode =
                archiveMetadataService.listFields(categoryId).stream()
                        .collect(Collectors.toMap(ArchiveFieldDto::fieldCode, field -> field));
        for (ResolvedScope scope : resolved.scopes()) {
            List<String> scopeFondsCodes = new ArrayList<>();
            List<Long> scopeSecurityLevelIds = new ArrayList<>();
            boolean scopeHasCategoryDimension = false;
            boolean scopeCategoryMatched = false;
            for (ArchiveDataScopeDimension dimension : scope.dimensions()) {
                switch (dimension.getDimensionType()) {
                    case FONDS -> {
                        if (StringUtils.isNotBlank(dimension.getTargetCode())) {
                            scopeFondsCodes.add(dimension.getTargetCode());
                        }
                    }
                    case CATEGORY -> {
                        scopeHasCategoryDimension = true;
                        if (categoryMatches(categoryId, dimension, categoriesById)) {
                            scopeCategoryMatched = true;
                        }
                    }
                    case SECURITY_LEVEL -> {
                        if (dimension.getTargetId() != null) {
                            scopeSecurityLevelIds.add(dimension.getTargetId());
                        }
                    }
                }
            }
            if (scopeHasCategoryDimension && !scopeCategoryMatched) {
                continue;
            }
            List<String> normalizedFondsCodes =
                    scopeFondsCodes.stream()
                            .filter(StringUtils::isNotBlank)
                            .distinct()
                            .sorted()
                            .toList();
            List<Long> normalizedSecurityLevelIds =
                    scopeSecurityLevelIds.stream()
                            .filter(Objects::nonNull)
                            .distinct()
                            .sorted()
                            .toList();
            if (fondsCode != null
                    && !normalizedFondsCodes.isEmpty()
                    && !normalizedFondsCodes.contains(fondsCode)) {
                continue;
            }
            @Nullable ArchiveDataScopeDynamicCondition dynamicCondition =
                    scope.scope().getDynamicCondition();
            List<ArchiveSqlCondition> dynamicConditions =
                    compileDynamicConditions(categoryId, fieldsByCode, dynamicCondition);
            if (hasDynamicConditions(dynamicCondition) && dynamicConditions.isEmpty()) {
                continue;
            }
            groups.add(
                    new ArchiveDataScopeSqlGroup(
                            normalizedFondsCodes, normalizedSecurityLevelIds, dynamicConditions));
        }
        return groups.isEmpty()
                ? ArchiveDataScopeFilter.none()
                : ArchiveDataScopeFilter.groups(groups);
    }

    public boolean matchesItemFilter(
            ArchiveDataScopeFilter filter,
            @Nullable String fondsCode,
            @Nullable Long securityLevelId,
            Map<String, @Nullable Object> dynamicRow) {
        if (filter.allData()) {
            return true;
        }
        if (filter.empty()) {
            return false;
        }
        for (ArchiveDataScopeSqlGroup group : filter.groups()) {
            if (!matchesFonds(group.fondsCodes(), fondsCode)) {
                continue;
            }
            if (!matchesSecurityLevel(group.securityLevelIds(), securityLevelId)) {
                continue;
            }
            if (group.conditions().stream()
                    .allMatch(condition -> matchesCondition(condition, dynamicRow))) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesFonds(List<String> allowedFondsCodes, @Nullable String fondsCode) {
        return allowedFondsCodes.isEmpty()
                || (StringUtils.isNotBlank(fondsCode) && allowedFondsCodes.contains(fondsCode));
    }

    private boolean matchesSecurityLevel(
            List<Long> allowedSecurityLevelIds, @Nullable Long securityLevelId) {
        return allowedSecurityLevelIds.isEmpty()
                || (securityLevelId != null && allowedSecurityLevelIds.contains(securityLevelId));
    }

    private boolean matchesCondition(
            ArchiveSqlCondition condition, Map<String, @Nullable Object> dynamicRow) {
        @Nullable Object actual = dynamicRow.get(condition.columnName());
        return switch (condition.operator()) {
            case EQ -> valuesEqual(actual, condition.value());
            case IN ->
                    condition.values().stream().anyMatch(expected -> valuesEqual(actual, expected));
            case IS_NULL -> actual == null;
            case IS_NOT_NULL -> actual != null;
            default -> false;
        };
    }

    private boolean valuesEqual(@Nullable Object actual, @Nullable Object expected) {
        if (actual == null || expected == null) {
            return actual == expected;
        }
        return actual.equals(expected) || actual.toString().equals(expected.toString());
    }

    private boolean categoryMatches(
            Long categoryId,
            ArchiveDataScopeDimension dimension,
            Map<Long, ArchiveCategoryDto> categoriesById) {
        Long targetId = dimension.getTargetId();
        if (targetId == null) {
            return false;
        }
        if (categoryId.equals(targetId)) {
            return true;
        }
        if (!dimension.isIncludeDescendants()) {
            return false;
        }
        Long currentId = categoryId;
        while (currentId != null) {
            ArchiveCategoryDto category = categoriesById.get(currentId);
            if (category == null) {
                return false;
            }
            currentId = category.parentId();
            if (targetId.equals(currentId)) {
                return true;
            }
        }
        return false;
    }

    private List<ArchiveSqlCondition> compileDynamicConditions(
            Long categoryId,
            Map<String, ArchiveFieldDto> fieldsByCode,
            @Nullable ArchiveDataScopeDynamicCondition dynamicCondition) {
        if (!hasDynamicConditions(dynamicCondition)) {
            return List.of();
        }
        List<ArchiveSqlCondition> conditions = new ArrayList<>();
        for (ArchiveDataScopeDynamicCondition.DynamicFieldCondition condition :
                dynamicCondition.dynamicFields()) {
            if (!categoryId.equals(condition.categoryId())) {
                continue;
            }
            ArchiveFieldDto field = fieldsByCode.get(condition.fieldCode());
            if (field == null || !field.enabled() || !field.dataScopeFilterable()) {
                throw new BadRequestException("动态字段不允许用于数据范围", "dynamicCondition", "动态字段不允许用于数据范围");
            }
            ArchiveItemQueryOperator operator = requireDynamicOperator(condition.operator());
            List<String> values = condition.values() == null ? List.of() : condition.values();
            conditions.add(toSqlCondition(field, operator, values));
        }
        return conditions;
    }

    private ArchiveSqlCondition toSqlCondition(
            ArchiveFieldDto field, ArchiveItemQueryOperator operator, List<String> values) {
        return switch (operator) {
            case EQ ->
                    new ArchiveSqlCondition(
                            field.columnName(),
                            ArchiveItemQueryOperator.EQ,
                            convertDynamicConditionValue(field, values.getFirst()));
            case IN ->
                    new ArchiveSqlCondition(
                            field.columnName(),
                            ArchiveItemQueryOperator.IN,
                            values.stream()
                                    .map(value -> convertDynamicConditionValue(field, value))
                                    .toList());
            case IS_NULL ->
                    new ArchiveSqlCondition(
                            field.columnName(), ArchiveItemQueryOperator.IS_NULL, null);
            case IS_NOT_NULL ->
                    new ArchiveSqlCondition(
                            field.columnName(), ArchiveItemQueryOperator.IS_NOT_NULL, null);
            default ->
                    throw new BadRequestException(
                            "动态字段条件操作符不支持", "dynamicCondition", "动态字段条件操作符不支持");
        };
    }

    private @Nullable Object convertDynamicConditionValue(
            ArchiveFieldDto field, @Nullable String value) {
        String text = StringUtils.trimToNull(value);
        if (text == null) {
            return null;
        }
        try {
            return switch (field.fieldType()) {
                case TEXT -> text;
                case INTEGER -> Integer.parseInt(text);
                case DECIMAL -> new BigDecimal(text);
                case DATE -> Date.valueOf(LocalDate.parse(text));
                case DATETIME -> Timestamp.valueOf(LocalDateTime.parse(text));
            };
        } catch (DateTimeParseException | IllegalArgumentException exception) {
            throw new BadRequestException("动态字段条件值格式不合法", "dynamicCondition", "动态字段条件值格式不合法");
        }
    }

    private void applyScopeFields(
            ArchiveDataScope scope,
            String rawScopeCode,
            String rawScopeName,
            ArchiveDataScopeType scopeType,
            @Nullable ArchiveDataScopeDynamicCondition dynamicCondition,
            boolean enabled,
            @Nullable String description) {
        String scopeCode = StringUtils.trimToNull(rawScopeCode);
        String scopeName = StringUtils.trimToNull(rawScopeName);
        if (scopeCode == null) {
            throw new BadRequestException("数据范围编码不能为空", "scopeCode", "数据范围编码不能为空");
        }
        if (scopeName == null) {
            throw new BadRequestException("数据范围名称不能为空", "scopeName", "数据范围名称不能为空");
        }
        scope.setScopeCode(scopeCode);
        scope.setScopeName(scopeName);
        scope.setScopeType(scopeType);
        scope.setDynamicCondition(dynamicCondition);
        scope.setEnabled(enabled);
        scope.setDescription(StringUtils.trimToNull(description));
    }

    private void validateDimensions(List<ArchiveDataScopeDimension> dimensions) {
        for (ArchiveDataScopeDimension dimension : dimensions) {
            if (dimension.getDimensionType() == null) {
                throw new BadRequestException("范围维度类型不能为空", "dimensions", "范围维度类型不能为空");
            }
            switch (dimension.getDimensionType()) {
                case FONDS -> {
                    if (StringUtils.isBlank(dimension.getTargetCode())) {
                        throw new BadRequestException("全宗范围必须指定全宗编码", "dimensions", "全宗范围必须指定全宗编码");
                    }
                    archiveMetadataService.getFondsByCode(dimension.getTargetCode());
                }
                case CATEGORY -> {
                    if (dimension.getTargetId() == null) {
                        throw new BadRequestException(
                                "分类范围必须指定分类 ID", "dimensions", "分类范围必须指定分类 ID");
                    }
                    archiveMetadataService.getCategory(dimension.getTargetId());
                }
                case SECURITY_LEVEL -> {
                    if (dimension.getTargetId() == null) {
                        throw new BadRequestException(
                                "密级范围必须指定密级 ID", "dimensions", "密级范围必须指定密级 ID");
                    }
                }
            }
        }
    }

    private void validateDynamicConditions(
            @Nullable ArchiveDataScopeDynamicCondition dynamicCondition) {
        if (!hasDynamicConditions(dynamicCondition)) {
            return;
        }
        for (ArchiveDataScopeDynamicCondition.DynamicFieldCondition condition :
                dynamicCondition.dynamicFields()) {
            if (condition.categoryId() == null) {
                throw new BadRequestException("动态字段条件必须指定分类", "dynamicCondition", "动态字段条件必须指定分类");
            }
            if (StringUtils.isBlank(condition.fieldCode())) {
                throw new BadRequestException(
                        "动态字段条件必须指定字段编码", "dynamicCondition", "动态字段条件必须指定字段编码");
            }
            archiveMetadataService.getCategory(condition.categoryId());
            ArchiveFieldDto field =
                    archiveMetadataService.listFields(condition.categoryId()).stream()
                            .filter(item -> item.fieldCode().equals(condition.fieldCode()))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new BadRequestException(
                                                    "动态字段不存在",
                                                    "dynamicCondition",
                                                    "动态字段不存在: " + condition.fieldCode()));
            if (!field.enabled() || !field.dataScopeFilterable()) {
                throw new BadRequestException("动态字段不允许用于数据范围", "dynamicCondition", "动态字段不允许用于数据范围");
            }
            validateDynamicOperatorAndValues(field, condition);
        }
    }

    private void validateDynamicOperatorAndValues(
            ArchiveFieldDto field,
            ArchiveDataScopeDynamicCondition.DynamicFieldCondition condition) {
        ArchiveItemQueryOperator operator = requireDynamicOperator(condition.operator());
        List<String> values = condition.values() == null ? List.of() : condition.values();
        switch (operator) {
            case EQ -> requireValue(values, 1, "EQ 操作符必须提供一个值");
            case IN -> {
                if (values.isEmpty()) {
                    throw new BadRequestException(
                            "IN 操作符必须提供至少一个值", "dynamicCondition", "IN 操作符必须提供至少一个值");
                }
            }
            case IS_NULL, IS_NOT_NULL -> {
                if (!values.isEmpty()) {
                    throw new BadRequestException(
                            "空值判断操作符不能提供值", "dynamicCondition", "空值判断操作符不能提供值");
                }
            }
            default ->
                    throw new BadRequestException(
                            "动态字段条件操作符不支持", "dynamicCondition", "动态字段条件操作符不支持");
        }
    }

    private ArchiveItemQueryOperator requireDynamicOperator(
            @Nullable ArchiveItemQueryOperator operator) {
        if (operator == null) {
            throw new BadRequestException("动态字段条件操作符不能为空", "dynamicCondition", "动态字段条件操作符不能为空");
        }
        return operator;
    }

    private void requireValue(List<String> values, int size, String message) {
        if (values.size() != size || values.stream().anyMatch(StringUtils::isBlank)) {
            throw new BadRequestException(message, "dynamicCondition", message);
        }
    }

    private ArchiveDataScope loadScope(Long scopeId) {
        return dataScopeRepository
                .findById(scopeId)
                .orElseThrow(() -> new BadRequestException("数据范围不存在", "scopeId", "数据范围不存在"));
    }

    private ArchiveDataScopeResponse toResponse(ArchiveDataScope scope) {
        return new ArchiveDataScopeResponse(
                scope.getId(),
                scope.getScopeCode(),
                scope.getScopeName(),
                scope.getScopeType(),
                dimensionRepository.findByScopeId(scope.getId()).stream()
                        .map(this::toDimensionResponse)
                        .toList(),
                scope.getDynamicCondition(),
                scope.isEnabled(),
                scope.getDescription());
    }

    private ArchiveDataScopeDimensionResponse toDimensionResponse(
            ArchiveDataScopeDimension dimension) {
        return new ArchiveDataScopeDimensionResponse(
                dimension.getDimensionType(),
                dimension.getTargetId(),
                dimension.getTargetCode(),
                dimension.isIncludeDescendants(),
                dimension.getSortOrder());
    }

    private List<ArchiveDataScopeDimension> toDimensions(
            @Nullable Long scopeId, List<ArchiveDataScopeDimensionRequest> requests) {
        if (requests == null) {
            return List.of();
        }
        List<ArchiveDataScopeDimension> dimensions = new ArrayList<>();
        int sortOrder = 0;
        for (ArchiveDataScopeDimensionRequest request : requests) {
            ArchiveDataScopeDimension dimension = new ArchiveDataScopeDimension();
            dimension.setScopeId(scopeId);
            dimension.setDimensionType(request.dimensionType());
            dimension.setTargetId(request.targetId());
            dimension.setTargetCode(StringUtils.trimToNull(request.targetCode()));
            dimension.setIncludeDescendants(request.includeDescendants());
            dimension.setSortOrder(sortOrder++);
            dimensions.add(dimension);
        }
        return dimensions;
    }

    private void insertDimensions(Long scopeId, List<ArchiveDataScopeDimension> dimensions) {
        for (ArchiveDataScopeDimension dimension : dimensions) {
            dimension.setScopeId(scopeId);
            dimensionRepository.insert(dimension);
        }
    }

    private void saveSubjectDataScopes(
            ArchiveDataScopeSubjectType subjectType, Long subjectId, List<Long> scopeIds) {
        subjectRelationRepository.deleteBySubjectTypeAndSubjectId(subjectType, subjectId);
        for (Long scopeId : scopeIds) {
            ArchiveDataScopeSubjectRelation relation = new ArchiveDataScopeSubjectRelation();
            relation.setSubjectType(subjectType);
            relation.setSubjectId(subjectId);
            relation.setScopeId(scopeId);
            subjectRelationRepository.insert(relation);
        }
    }

    private List<Long> listSubjectScopeIds(
            ArchiveDataScopeSubjectType subjectType, Long subjectId) {
        return subjectRelationRepository
                .findBySubjectTypeAndSubjectId(subjectType, subjectId)
                .stream()
                .map(ArchiveDataScopeSubjectRelation::getScopeId)
                .distinct()
                .sorted()
                .toList();
    }

    private boolean appendSubjectScopes(
            ArchiveDataScopeSubjectType subjectType, Long subjectId, List<ResolvedScope> scopes) {
        for (ArchiveDataScopeSubjectRelation relation :
                subjectRelationRepository.findBySubjectTypeAndSubjectId(subjectType, subjectId)) {
            ArchiveDataScope scope =
                    dataScopeRepository.findById(relation.getScopeId()).orElse(null);
            if (scope == null || !scope.isEnabled()) {
                continue;
            }
            if (scope.getScopeType() == ArchiveDataScopeType.ALL) {
                return true;
            }
            List<ArchiveDataScopeDimension> dimensions =
                    dimensionRepository.findByScopeId(scope.getId());
            if (dimensions.isEmpty() && !hasDynamicConditions(scope.getDynamicCondition())) {
                continue;
            }
            scopes.add(new ResolvedScope(scope, dimensions));
        }
        return false;
    }

    private boolean hasDynamicConditions(
            @Nullable ArchiveDataScopeDynamicCondition dynamicCondition) {
        return dynamicCondition != null
                && dynamicCondition.dynamicFields() != null
                && !dynamicCondition.dynamicFields().isEmpty();
    }

    public record ResolvedArchiveDataScope(
            boolean allData, boolean empty, List<ResolvedScope> scopes) {

        public static ResolvedArchiveDataScope all() {
            return new ResolvedArchiveDataScope(true, false, List.of());
        }

        public static ResolvedArchiveDataScope none() {
            return new ResolvedArchiveDataScope(false, true, List.of());
        }

        public static ResolvedArchiveDataScope conditional(List<ResolvedScope> scopes) {
            return new ResolvedArchiveDataScope(false, false, List.copyOf(scopes));
        }
    }

    public record ResolvedScope(
            ArchiveDataScope scope, List<ArchiveDataScopeDimension> dimensions) {}

    public record ArchiveDataScopeFilter(
            boolean allData, boolean empty, List<ArchiveDataScopeSqlGroup> groups) {

        public static ArchiveDataScopeFilter all() {
            return new ArchiveDataScopeFilter(true, false, List.of());
        }

        public static ArchiveDataScopeFilter none() {
            return new ArchiveDataScopeFilter(false, true, List.of());
        }

        public static ArchiveDataScopeFilter fondsCodes(List<String> fondsCodes) {
            return groups(
                    List.of(
                            new ArchiveDataScopeSqlGroup(
                                    List.copyOf(fondsCodes), List.of(), List.of())));
        }

        public static ArchiveDataScopeFilter groups(List<ArchiveDataScopeSqlGroup> groups) {
            return new ArchiveDataScopeFilter(false, false, List.copyOf(groups));
        }
    }

    public record CreateArchiveDataScopeRequest(
            String scopeCode,
            String scopeName,
            ArchiveDataScopeType scopeType,
            List<ArchiveDataScopeDimensionRequest> dimensions,
            @Nullable ArchiveDataScopeDynamicCondition dynamicCondition,
            boolean enabled,
            @Nullable String description) {}

    public record UpdateArchiveDataScopeRequest(
            String scopeCode,
            String scopeName,
            ArchiveDataScopeType scopeType,
            List<ArchiveDataScopeDimensionRequest> dimensions,
            @Nullable ArchiveDataScopeDynamicCondition dynamicCondition,
            boolean enabled,
            @Nullable String description) {}

    public record ArchiveDataScopeDimensionRequest(
            ArchiveDataScopeDimensionType dimensionType,
            @Nullable Long targetId,
            @Nullable String targetCode,
            boolean includeDescendants) {}

    public record ArchiveDataScopeResponse(
            Long id,
            String scopeCode,
            String scopeName,
            ArchiveDataScopeType scopeType,
            List<ArchiveDataScopeDimensionResponse> dimensions,
            @Nullable ArchiveDataScopeDynamicCondition dynamicCondition,
            boolean enabled,
            @Nullable String description) {}

    public record ArchiveDataScopeDimensionResponse(
            ArchiveDataScopeDimensionType dimensionType,
            @Nullable Long targetId,
            @Nullable String targetCode,
            boolean includeDescendants,
            int sortOrder) {}

    public record RoleArchiveDataScopesResponse(Long roleId, List<Long> scopeIds) {}

    public record UserArchiveDataScopesResponse(Long userId, List<Long> scopeIds) {}
}
