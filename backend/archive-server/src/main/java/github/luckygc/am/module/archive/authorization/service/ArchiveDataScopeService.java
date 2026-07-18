package github.luckygc.am.module.archive.authorization.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeResolutionTypes.ArchiveDataScopeFilter;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeResolutionTypes.ResolvedArchiveDataScope;
import github.luckygc.am.module.archive.item.ArchiveItemQueryOperator;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataReferenceService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFieldDto;
import github.luckygc.am.module.authentication.AuthenticationUser;
import github.luckygc.am.module.authentication.repository.AuthenticationUserDataRepository;
import github.luckygc.am.module.authorization.AuthorizationRole;
import github.luckygc.am.module.authorization.repository.AuthorizationRoleDataRepository;
import github.luckygc.am.module.organization.service.OrganizationDepartmentService;
import github.luckygc.am.module.organization.service.OrganizationDepartmentService.OrganizationDepartmentResponse;

@Service
public class ArchiveDataScopeService {

    private final ArchiveDataScopeDataRepository dataScopeRepository;
    private final ArchiveDataScopeDimensionDataRepository dimensionRepository;
    private final ArchiveDataScopeSubjectRelationDataRepository subjectRelationRepository;
    private final AuthorizationRoleDataRepository roleRepository;
    private final AuthenticationUserDataRepository authenticationUserRepository;
    private final OrganizationDepartmentService departmentService;
    private final ArchiveMetadataService archiveMetadataService;
    private final ArchiveMetadataReferenceService archiveMetadataReferenceService;
    private final ArchiveCategoryService archiveCategoryService;
    private final ArchiveDataScopeResolver dataScopeResolver;

    public ArchiveDataScopeService(
            ArchiveDataScopeDataRepository dataScopeRepository,
            ArchiveDataScopeDimensionDataRepository dimensionRepository,
            ArchiveDataScopeSubjectRelationDataRepository subjectRelationRepository,
            AuthorizationRoleDataRepository roleRepository,
            AuthenticationUserDataRepository authenticationUserRepository,
            OrganizationDepartmentService departmentService,
            ArchiveMetadataService archiveMetadataService,
            ArchiveMetadataReferenceService archiveMetadataReferenceService,
            ArchiveCategoryService archiveCategoryService,
            ArchiveDataScopeResolver dataScopeResolver) {
        this.dataScopeRepository = dataScopeRepository;
        this.dimensionRepository = dimensionRepository;
        this.subjectRelationRepository = subjectRelationRepository;
        this.roleRepository = roleRepository;
        this.authenticationUserRepository = authenticationUserRepository;
        this.departmentService = departmentService;
        this.archiveMetadataService = archiveMetadataService;
        this.archiveMetadataReferenceService = archiveMetadataReferenceService;
        this.archiveCategoryService = archiveCategoryService;
        this.dataScopeResolver = dataScopeResolver;
    }

    public void validateScopeDefinition(
            ArchiveDataScope scope, List<ArchiveDataScopeDimension> dimensions) {
        validateScopeDefinitionInternal(scope, dimensions);
    }

    private void validateScopeDefinitionInternal(
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
        validateScopeDefinitionInternal(scope, dimensions);
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
        validateScopeDefinitionInternal(scope, dimensions);
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
        List<Long> normalizedScopeIds = normalizeEnabledScopeIds(scopeIds);
        saveSubjectDataScopes(ArchiveDataScopeSubjectType.ROLE, roleId, normalizedScopeIds);
        return listRoleDataScopesInternal(roleId);
    }

    @Transactional(readOnly = true)
    public RoleArchiveDataScopesResponse listRoleDataScopes(Long roleId) {
        return listRoleDataScopesInternal(roleId);
    }

    private RoleArchiveDataScopesResponse listRoleDataScopesInternal(Long roleId) {
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
        AuthenticationUser user =
                authenticationUserRepository
                        .findById(userId)
                        .orElseThrow(() -> new BadRequestException("用户不存在", "userId", "用户不存在"));
        if (!user.isEnabled()) {
            throw new BadRequestException("用户已停用", "userId", "用户已停用");
        }
        List<Long> normalizedScopeIds = normalizeEnabledScopeIds(scopeIds);
        saveSubjectDataScopes(ArchiveDataScopeSubjectType.USER, userId, normalizedScopeIds);
        return listUserDataScopesInternal(userId);
    }

    @Transactional(readOnly = true)
    public UserArchiveDataScopesResponse listUserDataScopes(Long userId) {
        return listUserDataScopesInternal(userId);
    }

    private UserArchiveDataScopesResponse listUserDataScopesInternal(Long userId) {
        return new UserArchiveDataScopesResponse(
                userId, listSubjectScopeIds(ArchiveDataScopeSubjectType.USER, userId));
    }

    @Transactional
    public DepartmentArchiveDataScopesResponse saveDepartmentDataScopes(
            Long departmentId, List<Long> scopeIds) {
        OrganizationDepartmentResponse department =
                departmentService.requireEnabledDepartment(departmentId);
        List<Long> normalizedScopeIds = normalizeEnabledScopeIds(scopeIds);
        saveSubjectDataScopes(
                ArchiveDataScopeSubjectType.DEPARTMENT, department.id(), normalizedScopeIds);
        return new DepartmentArchiveDataScopesResponse(
                department.id(),
                listSubjectScopeIds(ArchiveDataScopeSubjectType.DEPARTMENT, department.id()));
    }

    @Transactional(readOnly = true)
    public DepartmentArchiveDataScopesResponse listDepartmentDataScopes(Long departmentId) {
        OrganizationDepartmentResponse department = departmentService.getDepartment(departmentId);
        if (!department.enabled()) {
            return new DepartmentArchiveDataScopesResponse(department.id(), List.of());
        }
        return new DepartmentArchiveDataScopesResponse(
                department.id(),
                listSubjectScopeIds(ArchiveDataScopeSubjectType.DEPARTMENT, department.id()));
    }

    @Transactional(readOnly = true)
    public ResolvedArchiveDataScope resolveUserDataScope(Long userId) {
        return dataScopeResolver.resolveUserDataScope(userId);
    }

    @Transactional(readOnly = true)
    public ArchiveDataScopeFilter buildItemFilter(
            Long userId, Long categoryId, @Nullable String requestedFondsCode) {
        return dataScopeResolver.buildItemFilter(userId, categoryId, requestedFondsCode);
    }

    @Transactional(readOnly = true)
    public Map<Long, ArchiveDataScopeFilter> compileItemFilters(
            ResolvedArchiveDataScope resolved,
            List<ArchiveCategoryDto> categories,
            @Nullable String requestedFondsCode) {
        return dataScopeResolver.compileItemFilters(resolved, categories, requestedFondsCode);
    }

    public boolean matchesItemFilter(
            ArchiveDataScopeFilter filter,
            @Nullable String fondsCode,
            @Nullable Long securityLevelId,
            @Nullable Long retentionPeriodId,
            java.util.Map<String, @Nullable Object> dynamicRow) {
        return dataScopeResolver.matchesItemFilter(
                filter, fondsCode, securityLevelId, retentionPeriodId, dynamicRow);
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
                    archiveMetadataReferenceService.getFondsByCode(dimension.getTargetCode());
                }
                case CATEGORY -> {
                    if (dimension.getTargetId() == null) {
                        throw new BadRequestException(
                                "分类范围必须指定分类 ID", "dimensions", "分类范围必须指定分类 ID");
                    }
                    archiveCategoryService.getCategory(dimension.getTargetId());
                }
                case SECURITY_LEVEL -> {
                    if (dimension.getTargetId() == null) {
                        throw new BadRequestException(
                                "密级范围必须指定密级 ID", "dimensions", "密级范围必须指定密级 ID");
                    }
                }
                case RETENTION_PERIOD -> {
                    if (dimension.getTargetId() == null) {
                        throw new BadRequestException(
                                "保管期限范围必须指定保管期限 ID", "dimensions", "保管期限范围必须指定保管期限 ID");
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
            archiveCategoryService.getCategory(condition.categoryId());
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

    private List<Long> normalizeEnabledScopeIds(List<Long> scopeIds) {
        if (scopeIds == null) {
            return List.of();
        }
        List<Long> normalizedScopeIds =
                scopeIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
        for (Long scopeId : normalizedScopeIds) {
            ArchiveDataScope scope = loadScope(scopeId);
            if (!scope.isEnabled()) {
                throw new BadRequestException("数据范围已停用", "scopeIds", "数据范围已停用: " + scopeId);
            }
        }
        return normalizedScopeIds;
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

    private boolean hasDynamicConditions(
            @Nullable ArchiveDataScopeDynamicCondition dynamicCondition) {
        return dynamicCondition != null
                && dynamicCondition.dynamicFields() != null
                && !dynamicCondition.dynamicFields().isEmpty();
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

    public record DepartmentArchiveDataScopesResponse(Long departmentId, List<Long> scopeIds) {}
}
