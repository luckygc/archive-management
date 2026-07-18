package github.luckygc.am.module.archive.authorization.service;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.authorization.ArchiveDataScope;
import github.luckygc.am.module.archive.authorization.ArchiveDataScopeDimension;
import github.luckygc.am.module.archive.authorization.ArchiveDataScopeDynamicCondition;
import github.luckygc.am.module.archive.authorization.ArchiveDataScopeSubjectRelation;
import github.luckygc.am.module.archive.authorization.ArchiveDataScopeSubjectType;
import github.luckygc.am.module.archive.authorization.ArchiveDataScopeType;
import github.luckygc.am.module.archive.authorization.repository.ArchiveDataScopeDataRepository;
import github.luckygc.am.module.archive.authorization.repository.ArchiveDataScopeDimensionDataRepository;
import github.luckygc.am.module.archive.authorization.repository.ArchiveDataScopeSubjectRelationDataRepository;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeResolutionTypes.ArchiveDataScopeFilter;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeResolutionTypes.ResolvedArchiveDataScope;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeResolutionTypes.ResolvedScope;
import github.luckygc.am.module.archive.item.ArchiveItemQueryOperator;
import github.luckygc.am.module.archive.mapper.ArchiveDataScopeSqlGroup;
import github.luckygc.am.module.archive.mapper.ArchiveSqlCondition;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFieldDto;
import github.luckygc.am.module.authentication.AuthenticationUser;
import github.luckygc.am.module.authentication.repository.AuthenticationUserDataRepository;
import github.luckygc.am.module.authorization.AuthorizationRole;
import github.luckygc.am.module.authorization.AuthorizationUserRoleRelation;
import github.luckygc.am.module.authorization.repository.AuthorizationRoleDataRepository;
import github.luckygc.am.module.authorization.repository.AuthorizationUserRoleRelationDataRepository;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;
import github.luckygc.am.module.organization.service.OrganizationDepartmentService;
import github.luckygc.am.module.organization.service.OrganizationDepartmentService.OrganizationDepartmentResponse;

@Service
public class ArchiveDataScopeResolver {

    private final ArchiveDataScopeDataRepository dataScopeRepository;
    private final ArchiveDataScopeDimensionDataRepository dimensionRepository;
    private final ArchiveDataScopeSubjectRelationDataRepository subjectRelationRepository;
    private final AuthorizationRoleDataRepository roleRepository;
    private final AuthorizationUserRoleRelationDataRepository userRoleRelationRepository;
    private final AuthenticationUserDataRepository authenticationUserRepository;
    private final OrganizationDepartmentService departmentService;
    private final ArchiveMetadataService archiveMetadataService;
    private final ArchiveCategoryService archiveCategoryService;

    public ArchiveDataScopeResolver(
            ArchiveDataScopeDataRepository dataScopeRepository,
            ArchiveDataScopeDimensionDataRepository dimensionRepository,
            ArchiveDataScopeSubjectRelationDataRepository subjectRelationRepository,
            AuthorizationRoleDataRepository roleRepository,
            AuthorizationUserRoleRelationDataRepository userRoleRelationRepository,
            AuthenticationUserDataRepository authenticationUserRepository,
            OrganizationDepartmentService departmentService,
            ArchiveMetadataService archiveMetadataService,
            ArchiveCategoryService archiveCategoryService) {
        this.dataScopeRepository = dataScopeRepository;
        this.dimensionRepository = dimensionRepository;
        this.subjectRelationRepository = subjectRelationRepository;
        this.roleRepository = roleRepository;
        this.userRoleRelationRepository = userRoleRelationRepository;
        this.authenticationUserRepository = authenticationUserRepository;
        this.departmentService = departmentService;
        this.archiveMetadataService = archiveMetadataService;
        this.archiveCategoryService = archiveCategoryService;
    }

    public ResolvedArchiveDataScope resolveUserDataScope(Long userId) {
        return resolveUserDataScopeInternal(userId);
    }

    private ResolvedArchiveDataScope resolveUserDataScopeInternal(Long userId) {
        List<ResolvedScope> scopes = new ArrayList<>();
        if (appendSubjectScopes(ArchiveDataScopeSubjectType.USER, userId, scopes)) {
            return ResolvedArchiveDataScope.all();
        }
        if (appendUserDepartmentScopes(userId, scopes)) {
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

    public ArchiveDataScopeFilter buildItemFilter(
            Long userId, Long categoryId, @Nullable String requestedFondsCode) {
        ResolvedArchiveDataScope resolved = resolveUserDataScopeInternal(userId);
        if (resolved.allData()) {
            return ArchiveDataScopeFilter.all();
        }
        if (resolved.empty()) {
            return ArchiveDataScopeFilter.none();
        }
        List<ArchiveCategoryDto> categories = archiveCategoryService.listCategories(true);
        Map<Long, ArchiveCategoryDto> categoriesById = categoriesById(categories);
        Map<String, ArchiveFieldDto> fieldsByCode =
                dynamicCategoryIds(resolved).contains(categoryId)
                        ? fieldsByCode(categoryId)
                        : Map.of();
        return compileItemFilter(
                resolved, categoryId, requestedFondsCode, categoriesById, fieldsByCode);
    }

    public Map<Long, ArchiveDataScopeFilter> compileItemFilters(
            ResolvedArchiveDataScope resolved,
            List<ArchiveCategoryDto> categories,
            @Nullable String requestedFondsCode) {
        if (resolved.allData()) {
            return constantFilters(categories, ArchiveDataScopeFilter.all());
        }
        if (resolved.empty()) {
            return constantFilters(categories, ArchiveDataScopeFilter.none());
        }
        Map<Long, ArchiveCategoryDto> categoriesById = categoriesById(categories);
        Set<Long> dynamicCategoryIds = dynamicCategoryIds(resolved);
        Map<Long, ArchiveDataScopeFilter> filters = new LinkedHashMap<>();
        for (ArchiveCategoryDto category : categories) {
            Map<String, ArchiveFieldDto> fieldsByCode =
                    dynamicCategoryIds.contains(category.id())
                            ? fieldsByCode(category.id())
                            : Map.of();
            filters.put(
                    category.id(),
                    compileItemFilter(
                            resolved,
                            category.id(),
                            requestedFondsCode,
                            categoriesById,
                            fieldsByCode));
        }
        return Map.copyOf(filters);
    }

    private Map<Long, ArchiveDataScopeFilter> constantFilters(
            List<ArchiveCategoryDto> categories, ArchiveDataScopeFilter filter) {
        Map<Long, ArchiveDataScopeFilter> filters = new LinkedHashMap<>();
        for (ArchiveCategoryDto category : categories) {
            filters.put(category.id(), filter);
        }
        return Map.copyOf(filters);
    }

    private ArchiveDataScopeFilter compileItemFilter(
            ResolvedArchiveDataScope resolved,
            Long categoryId,
            @Nullable String requestedFondsCode,
            Map<Long, ArchiveCategoryDto> categoriesById,
            Map<String, ArchiveFieldDto> fieldsByCode) {
        if (resolved.allData()) {
            return ArchiveDataScopeFilter.all();
        }
        if (resolved.empty()) {
            return ArchiveDataScopeFilter.none();
        }
        List<ArchiveDataScopeSqlGroup> groups = new ArrayList<>();
        String fondsCode = StringUtils.trimToNull(requestedFondsCode);
        for (ResolvedScope scope : resolved.scopes()) {
            ArchiveDataScopeSqlGroup group =
                    compileScopeGroup(categoryId, fondsCode, categoriesById, fieldsByCode, scope);
            if (group != null) {
                groups.add(group);
            }
        }
        return groups.isEmpty()
                ? ArchiveDataScopeFilter.none()
                : ArchiveDataScopeFilter.groups(groups);
    }

    private Map<Long, ArchiveCategoryDto> categoriesById(List<ArchiveCategoryDto> categories) {
        return categories.stream()
                .filter(ArchiveCategoryDto::enabled)
                .collect(
                        Collectors.toMap(
                                ArchiveCategoryDto::id,
                                category -> category,
                                (first, ignored) -> first,
                                LinkedHashMap::new));
    }

    private Set<Long> dynamicCategoryIds(ResolvedArchiveDataScope resolved) {
        return resolved.scopes().stream()
                .map(ResolvedScope::scope)
                .map(ArchiveDataScope::getDynamicCondition)
                .filter(this::hasDynamicConditions)
                .flatMap(condition -> condition.dynamicFields().stream())
                .map(ArchiveDataScopeDynamicCondition.DynamicFieldCondition::categoryId)
                .collect(Collectors.toSet());
    }

    private Map<String, ArchiveFieldDto> fieldsByCode(Long categoryId) {
        return archiveMetadataService.listFields(categoryId).stream()
                .collect(
                        Collectors.toMap(
                                ArchiveFieldDto::fieldCode,
                                field -> field,
                                (first, ignored) -> first,
                                LinkedHashMap::new));
    }

    private @Nullable ArchiveDataScopeSqlGroup compileScopeGroup(
            Long categoryId,
            @Nullable String requestedFondsCode,
            Map<Long, ArchiveCategoryDto> categoriesById,
            Map<String, ArchiveFieldDto> fieldsByCode,
            ResolvedScope scope) {
        List<String> fondsCodes = new ArrayList<>();
        List<Long> securityLevelIds = new ArrayList<>();
        List<Long> retentionPeriodIds = new ArrayList<>();
        boolean hasCategoryDimension = false;
        boolean categoryMatched = false;
        for (ArchiveDataScopeDimension dimension : scope.dimensions()) {
            switch (dimension.getDimensionType()) {
                case FONDS -> {
                    if (StringUtils.isNotBlank(dimension.getTargetCode())) {
                        fondsCodes.add(dimension.getTargetCode());
                    }
                }
                case CATEGORY -> {
                    hasCategoryDimension = true;
                    categoryMatched |= categoryMatches(categoryId, dimension, categoriesById);
                }
                case SECURITY_LEVEL -> {
                    if (dimension.getTargetId() != null) {
                        securityLevelIds.add(dimension.getTargetId());
                    }
                }
                case RETENTION_PERIOD -> {
                    if (dimension.getTargetId() != null) {
                        retentionPeriodIds.add(dimension.getTargetId());
                    }
                }
            }
        }
        if (hasCategoryDimension && !categoryMatched) {
            return null;
        }
        List<String> normalizedFondsCodes =
                fondsCodes.stream().filter(StringUtils::isNotBlank).distinct().sorted().toList();
        if (requestedFondsCode != null
                && !normalizedFondsCodes.isEmpty()
                && !normalizedFondsCodes.contains(requestedFondsCode)) {
            return null;
        }
        @Nullable ArchiveDataScopeDynamicCondition dynamicCondition =
                scope.scope().getDynamicCondition();
        List<ArchiveSqlCondition> dynamicConditions =
                compileDynamicConditions(categoryId, fieldsByCode, dynamicCondition);
        if (hasDynamicConditions(dynamicCondition) && dynamicConditions.isEmpty()) {
            return null;
        }
        return new ArchiveDataScopeSqlGroup(
                normalizedFondsCodes,
                normalizeIds(securityLevelIds),
                normalizeIds(retentionPeriodIds),
                dynamicConditions);
    }

    private List<Long> normalizeIds(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).distinct().sorted().toList();
    }

    public boolean matchesItemFilter(
            ArchiveDataScopeFilter filter,
            @Nullable String fondsCode,
            @Nullable Long securityLevelId,
            @Nullable Long retentionPeriodId,
            Map<String, @Nullable Object> dynamicRow) {
        if (filter.allData()) {
            return true;
        }
        if (filter.empty()) {
            return false;
        }
        return filter.groups().stream()
                .anyMatch(
                        group ->
                                matches(group.fondsCodes(), fondsCode)
                                        && matches(group.securityLevelIds(), securityLevelId)
                                        && matches(group.retentionPeriodIds(), retentionPeriodId)
                                        && group.conditions().stream()
                                                .allMatch(
                                                        condition ->
                                                                matchesCondition(
                                                                        condition, dynamicRow)));
    }

    private boolean matches(List<?> allowedValues, @Nullable Object actual) {
        return allowedValues.isEmpty() || (actual != null && allowedValues.contains(actual));
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

    private ArchiveItemQueryOperator requireDynamicOperator(
            @Nullable ArchiveItemQueryOperator operator) {
        if (operator == null) {
            throw new BadRequestException("动态字段条件操作符不能为空", "dynamicCondition", "动态字段条件操作符不能为空");
        }
        return operator;
    }

    private boolean appendUserDepartmentScopes(Long userId, List<ResolvedScope> scopes) {
        AuthenticationUser user = authenticationUserRepository.findById(userId).orElse(null);
        if (user == null || !user.isEnabled() || user.getDepartmentId() == null) {
            return false;
        }
        OrganizationDepartmentResponse department =
                departmentService.getDepartment(user.getDepartmentId());
        return department.enabled()
                && appendSubjectScopes(
                        ArchiveDataScopeSubjectType.DEPARTMENT, department.id(), scopes);
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
}
