package github.luckygc.am.module.archive.authorization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.ArchiveLevel;
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
import github.luckygc.am.module.archive.metadata.ArchiveFieldControl;
import github.luckygc.am.module.archive.metadata.ArchiveFieldScope;
import github.luckygc.am.module.archive.metadata.ArchiveFieldType;
import github.luckygc.am.module.archive.metadata.ArchiveManagementMode;
import github.luckygc.am.module.archive.metadata.ArchiveTableStatus;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataReferenceService;
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

@DisplayName("档案数据范围服务")
class ArchiveDataScopeServiceTests {

    private ArchiveDataScopeDataRepository dataScopeRepository;
    private ArchiveDataScopeDimensionDataRepository dimensionRepository;
    private ArchiveDataScopeSubjectRelationDataRepository subjectRelationRepository;
    private AuthorizationRoleDataRepository roleRepository;
    private AuthorizationUserRoleRelationDataRepository userRoleRelationRepository;
    private AuthenticationUserDataRepository authenticationUserRepository;
    private OrganizationDepartmentService departmentService;
    private ArchiveMetadataService archiveMetadataService;
    private ArchiveMetadataReferenceService archiveMetadataReferenceService;
    private ArchiveCategoryService archiveCategoryService;
    private ArchiveDataScopeService dataScopeService;

    @BeforeEach
    void setUp() {
        dataScopeRepository = mock(ArchiveDataScopeDataRepository.class);
        dimensionRepository = mock(ArchiveDataScopeDimensionDataRepository.class);
        subjectRelationRepository = mock(ArchiveDataScopeSubjectRelationDataRepository.class);
        roleRepository = mock(AuthorizationRoleDataRepository.class);
        userRoleRelationRepository = mock(AuthorizationUserRoleRelationDataRepository.class);
        authenticationUserRepository = mock(AuthenticationUserDataRepository.class);
        departmentService = mock(OrganizationDepartmentService.class);
        archiveMetadataService = mock(ArchiveMetadataService.class);
        archiveMetadataReferenceService = mock(ArchiveMetadataReferenceService.class);
        archiveCategoryService = mock(ArchiveCategoryService.class);
        ArchiveDataScopeResolver dataScopeResolver =
                new ArchiveDataScopeResolver(
                        dataScopeRepository,
                        dimensionRepository,
                        subjectRelationRepository,
                        roleRepository,
                        userRoleRelationRepository,
                        authenticationUserRepository,
                        departmentService,
                        archiveMetadataService,
                        archiveCategoryService);
        dataScopeService =
                new ArchiveDataScopeService(
                        dataScopeRepository,
                        dimensionRepository,
                        subjectRelationRepository,
                        roleRepository,
                        authenticationUserRepository,
                        departmentService,
                        archiveMetadataService,
                        archiveMetadataReferenceService,
                        archiveCategoryService,
                        dataScopeResolver);
    }

    @Test
    @DisplayName("任意范围不能携带固定维度或动态字段条件")
    void validateScopeShouldRejectAllWithConditions() {
        ArchiveDataScope scope = scope(1L, ArchiveDataScopeType.ALL, true);
        scope.setDynamicCondition(
                new ArchiveDataScopeDynamicCondition(
                        List.of(
                                new ArchiveDataScopeDynamicCondition.DynamicFieldCondition(
                                        10L,
                                        "department",
                                        ArchiveItemQueryOperator.IN,
                                        List.of("LEGAL")))));

        assertThatThrownBy(() -> dataScopeService.validateScopeDefinition(scope, List.of()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("任意范围不能配置条件");
    }

    @Test
    @DisplayName("条件范围至少需要一个固定维度或动态字段条件")
    void validateScopeShouldRejectEmptyConditionalScope() {
        ArchiveDataScope scope = scope(1L, ArchiveDataScopeType.CONDITIONAL, true);

        assertThatThrownBy(() -> dataScopeService.validateScopeDefinition(scope, List.of()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("条件范围至少需要一个条件");
    }

    @Test
    @DisplayName("动态字段未允许用于数据范围时拒绝保存")
    void validateScopeShouldRejectDynamicFieldThatIsNotFilterable() {
        ArchiveDataScope scope = scope(1L, ArchiveDataScopeType.CONDITIONAL, true);
        scope.setDynamicCondition(
                new ArchiveDataScopeDynamicCondition(
                        List.of(
                                new ArchiveDataScopeDynamicCondition.DynamicFieldCondition(
                                        10L,
                                        "department",
                                        ArchiveItemQueryOperator.IN,
                                        List.of("LEGAL")))));
        when(archiveMetadataService.listFields(10L))
                .thenReturn(List.of(field(20L, 10L, "department", false)));

        assertThatThrownBy(() -> dataScopeService.validateScopeDefinition(scope, List.of()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("动态字段不允许用于数据范围");
    }

    @Test
    @DisplayName("用户命中任意范围时计算结果为全部数据")
    void resolveUserScopeShouldReturnAllWhenAnyEnabledScopeIsAll() {
        when(userRoleRelationRepository.findByUserId(7L)).thenReturn(List.of(userRole(7L, 1L)));
        when(roleRepository.findById(1L)).thenReturn(Optional.of(enabledRole(1L)));
        when(subjectRelationRepository.findBySubjectTypeAndSubjectId(
                        ArchiveDataScopeSubjectType.USER, 7L))
                .thenReturn(List.of());
        when(subjectRelationRepository.findBySubjectTypeAndSubjectId(
                        ArchiveDataScopeSubjectType.ROLE, 1L))
                .thenReturn(List.of(subjectScope(ArchiveDataScopeSubjectType.ROLE, 1L, 100L)));
        when(dataScopeRepository.findById(100L))
                .thenReturn(Optional.of(scope(100L, ArchiveDataScopeType.ALL, true)));

        ArchiveDataScopeResolutionTypes.ResolvedArchiveDataScope resolved =
                dataScopeService.resolveUserDataScope(7L);

        assertThat(resolved.allData()).isTrue();
        assertThat(resolved.empty()).isFalse();
    }

    @Test
    @DisplayName("超级管理员数据范围固定为全部数据")
    void resolveUserScopeShouldReturnAllForSuperAdmin() {
        AuthorizationRole role = enabledRole(1L);
        role.setRoleName(AuthorizationPermissionService.SUPER_ADMIN_ROLE_NAME);
        when(userRoleRelationRepository.findByUserId(7L)).thenReturn(List.of(userRole(7L, 1L)));
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));

        ArchiveDataScopeResolutionTypes.ResolvedArchiveDataScope resolved =
                dataScopeService.resolveUserDataScope(7L);

        assertThat(resolved.allData()).isTrue();
    }

    @Test
    @DisplayName("用户直接绑定的数据范围参与范围计算")
    void resolveUserScopeShouldIncludeDirectUserScopes() {
        ArchiveDataScope scope = scope(100L, ArchiveDataScopeType.CONDITIONAL, true);
        ArchiveDataScopeDimension dimension = new ArchiveDataScopeDimension();
        dimension.setScopeId(100L);
        dimension.setDimensionType(ArchiveDataScopeDimensionType.FONDS);
        dimension.setTargetCode("F001");
        when(subjectRelationRepository.findBySubjectTypeAndSubjectId(
                        ArchiveDataScopeSubjectType.USER, 7L))
                .thenReturn(List.of(subjectScope(ArchiveDataScopeSubjectType.USER, 7L, 100L)));
        when(dataScopeRepository.findById(100L)).thenReturn(Optional.of(scope));
        when(dimensionRepository.findByScopeId(100L)).thenReturn(List.of(dimension));
        when(userRoleRelationRepository.findByUserId(7L)).thenReturn(List.of());

        ArchiveDataScopeResolutionTypes.ResolvedArchiveDataScope resolved =
                dataScopeService.resolveUserDataScope(7L);

        assertThat(resolved.empty()).isFalse();
        assertThat(resolved.scopes()).hasSize(1);
        assertThat(resolved.scopes().getFirst().dimensions()).containsExactly(dimension);
    }

    @Test
    @DisplayName("用户所属启用部门绑定的数据范围参与范围计算")
    void resolveUserScopeShouldIncludeEnabledDepartmentScopes() {
        AuthenticationUser user = enabledUser(7L);
        user.setDepartmentId(3L);
        ArchiveDataScope scope = scope(100L, ArchiveDataScopeType.CONDITIONAL, true);
        ArchiveDataScopeDimension dimension = new ArchiveDataScopeDimension();
        dimension.setScopeId(100L);
        dimension.setDimensionType(ArchiveDataScopeDimensionType.FONDS);
        dimension.setTargetCode("F001");
        when(subjectRelationRepository.findBySubjectTypeAndSubjectId(
                        ArchiveDataScopeSubjectType.USER, 7L))
                .thenReturn(List.of());
        when(authenticationUserRepository.findById(7L)).thenReturn(Optional.of(user));
        when(departmentService.getDepartment(3L)).thenReturn(department(3L, true));
        when(subjectRelationRepository.findBySubjectTypeAndSubjectId(
                        ArchiveDataScopeSubjectType.DEPARTMENT, 3L))
                .thenReturn(
                        List.of(subjectScope(ArchiveDataScopeSubjectType.DEPARTMENT, 3L, 100L)));
        when(dataScopeRepository.findById(100L)).thenReturn(Optional.of(scope));
        when(dimensionRepository.findByScopeId(100L)).thenReturn(List.of(dimension));
        when(userRoleRelationRepository.findByUserId(7L)).thenReturn(List.of());

        ArchiveDataScopeResolutionTypes.ResolvedArchiveDataScope resolved =
                dataScopeService.resolveUserDataScope(7L);

        assertThat(resolved.empty()).isFalse();
        assertThat(resolved.scopes()).hasSize(1);
        assertThat(resolved.scopes().getFirst().dimensions()).containsExactly(dimension);
    }

    @Test
    @DisplayName("用户所属停用部门绑定的数据范围不参与范围计算")
    void resolveUserScopeShouldIgnoreDisabledDepartmentScopes() {
        AuthenticationUser user = enabledUser(7L);
        user.setDepartmentId(3L);
        when(subjectRelationRepository.findBySubjectTypeAndSubjectId(
                        ArchiveDataScopeSubjectType.USER, 7L))
                .thenReturn(List.of());
        when(authenticationUserRepository.findById(7L)).thenReturn(Optional.of(user));
        when(departmentService.getDepartment(3L)).thenReturn(department(3L, false));
        when(userRoleRelationRepository.findByUserId(7L)).thenReturn(List.of());

        ArchiveDataScopeResolutionTypes.ResolvedArchiveDataScope resolved =
                dataScopeService.resolveUserDataScope(7L);

        assertThat(resolved.empty()).isTrue();
    }

    @Test
    @DisplayName("禁用角色和禁用范围不参与用户范围计算")
    void resolveUserScopeShouldIgnoreDisabledRoleAndScope() {
        when(userRoleRelationRepository.findByUserId(7L))
                .thenReturn(List.of(userRole(7L, 1L), userRole(7L, 2L)));
        when(subjectRelationRepository.findBySubjectTypeAndSubjectId(
                        ArchiveDataScopeSubjectType.USER, 7L))
                .thenReturn(List.of());
        when(roleRepository.findById(1L)).thenReturn(Optional.of(disabledRole(1L)));
        when(roleRepository.findById(2L)).thenReturn(Optional.of(enabledRole(2L)));
        when(subjectRelationRepository.findBySubjectTypeAndSubjectId(
                        ArchiveDataScopeSubjectType.ROLE, 2L))
                .thenReturn(List.of(subjectScope(ArchiveDataScopeSubjectType.ROLE, 2L, 100L)));
        when(dataScopeRepository.findById(100L))
                .thenReturn(Optional.of(scope(100L, ArchiveDataScopeType.CONDITIONAL, false)));

        ArchiveDataScopeResolutionTypes.ResolvedArchiveDataScope resolved =
                dataScopeService.resolveUserDataScope(7L);

        assertThat(resolved.empty()).isTrue();
        assertThat(resolved.allData()).isFalse();
    }

    @Test
    @DisplayName("条件范围返回固定维度项")
    void resolveUserScopeShouldReturnConditionalDimensions() {
        ArchiveDataScope scope = scope(100L, ArchiveDataScopeType.CONDITIONAL, true);
        ArchiveDataScopeDimension dimension = new ArchiveDataScopeDimension();
        dimension.setScopeId(100L);
        dimension.setDimensionType(ArchiveDataScopeDimensionType.CATEGORY);
        dimension.setTargetId(10L);
        dimension.setIncludeDescendants(true);
        when(userRoleRelationRepository.findByUserId(7L)).thenReturn(List.of(userRole(7L, 1L)));
        when(subjectRelationRepository.findBySubjectTypeAndSubjectId(
                        ArchiveDataScopeSubjectType.USER, 7L))
                .thenReturn(List.of());
        when(roleRepository.findById(1L)).thenReturn(Optional.of(enabledRole(1L)));
        when(subjectRelationRepository.findBySubjectTypeAndSubjectId(
                        ArchiveDataScopeSubjectType.ROLE, 1L))
                .thenReturn(List.of(subjectScope(ArchiveDataScopeSubjectType.ROLE, 1L, 100L)));
        when(dataScopeRepository.findById(100L)).thenReturn(Optional.of(scope));
        when(dimensionRepository.findByScopeId(100L)).thenReturn(List.of(dimension));

        ArchiveDataScopeResolutionTypes.ResolvedArchiveDataScope resolved =
                dataScopeService.resolveUserDataScope(7L);

        assertThat(resolved.empty()).isFalse();
        assertThat(resolved.allData()).isFalse();
        assertThat(resolved.scopes()).hasSize(1);
        assertThat(resolved.scopes().getFirst().dimensions()).containsExactly(dimension);
    }

    @Test
    @DisplayName("分类继承范围在查询子分类时生效")
    void buildItemFilterShouldMatchDescendantCategory() {
        ArchiveDataScope scope = scope(100L, ArchiveDataScopeType.CONDITIONAL, true);
        ArchiveDataScopeDimension categoryDimension = new ArchiveDataScopeDimension();
        categoryDimension.setScopeId(100L);
        categoryDimension.setDimensionType(ArchiveDataScopeDimensionType.CATEGORY);
        categoryDimension.setTargetId(10L);
        categoryDimension.setIncludeDescendants(true);
        ArchiveDataScopeDimension fondsDimension = new ArchiveDataScopeDimension();
        fondsDimension.setScopeId(100L);
        fondsDimension.setDimensionType(ArchiveDataScopeDimensionType.FONDS);
        fondsDimension.setTargetCode("F001");
        bindDirectUserScope(scope, List.of(categoryDimension, fondsDimension));
        when(archiveCategoryService.listCategories(true))
                .thenReturn(List.of(category(10L, null), category(11L, 10L)));
        when(archiveMetadataService.listFields(11L)).thenReturn(List.of());

        ArchiveDataScopeResolutionTypes.ArchiveDataScopeFilter filter =
                dataScopeService.buildItemFilter(7L, 11L, null);

        assertThat(filter.empty()).isFalse();
        assertThat(filter.groups())
                .containsExactly(
                        new ArchiveDataScopeSqlGroup(List.of("F001"), List.of(), List.of()));
    }

    @Test
    @DisplayName("仅分类维度编译为空条件组并允许该分类全部档案")
    void buildItemFilterShouldTreatMatchedCategoryOnlyScopeAsUnrestrictedGroup() {
        ArchiveDataScope scope = scope(100L, ArchiveDataScopeType.CONDITIONAL, true);
        ArchiveDataScopeDimension categoryDimension = new ArchiveDataScopeDimension();
        categoryDimension.setScopeId(100L);
        categoryDimension.setDimensionType(ArchiveDataScopeDimensionType.CATEGORY);
        categoryDimension.setTargetId(11L);
        bindDirectUserScope(scope, List.of(categoryDimension));
        when(archiveCategoryService.listCategories(true)).thenReturn(List.of(category(11L, null)));
        when(archiveMetadataService.listFields(11L)).thenReturn(List.of());

        ArchiveDataScopeResolutionTypes.ArchiveDataScopeFilter filter =
                dataScopeService.buildItemFilter(7L, 11L, null);

        assertThat(filter.empty()).isFalse();
        assertThat(filter.groups())
                .containsExactly(
                        new ArchiveDataScopeSqlGroup(List.of(), List.of(), List.of(), List.of()));
        assertThat(dataScopeService.matchesItemFilter(filter, "F999", 99L, 88L, Map.of())).isTrue();
    }

    @Test
    @DisplayName("密级范围编译为主表固定 ID 条件")
    void buildItemFilterShouldCompileSecurityLevelIds() {
        ArchiveDataScope scope = scope(100L, ArchiveDataScopeType.CONDITIONAL, true);
        ArchiveDataScopeDimension securityLevelDimension = new ArchiveDataScopeDimension();
        securityLevelDimension.setScopeId(100L);
        securityLevelDimension.setDimensionType(ArchiveDataScopeDimensionType.SECURITY_LEVEL);
        securityLevelDimension.setTargetId(3L);
        bindDirectUserScope(scope, List.of(securityLevelDimension));
        when(archiveCategoryService.listCategories(true)).thenReturn(List.of(category(11L, null)));
        when(archiveMetadataService.listFields(11L)).thenReturn(List.of());

        ArchiveDataScopeResolutionTypes.ArchiveDataScopeFilter filter =
                dataScopeService.buildItemFilter(7L, 11L, null);

        assertThat(filter.empty()).isFalse();
        assertThat(filter.groups())
                .containsExactly(new ArchiveDataScopeSqlGroup(List.of(), List.of(3L), List.of()));
    }

    @Test
    @DisplayName("动态字段范围条件编译为受控 MyBatis 条件")
    void buildItemFilterShouldCompileDynamicConditions() {
        ArchiveDataScope scope = scope(100L, ArchiveDataScopeType.CONDITIONAL, true);
        scope.setDynamicCondition(
                new ArchiveDataScopeDynamicCondition(
                        List.of(
                                new ArchiveDataScopeDynamicCondition.DynamicFieldCondition(
                                        11L,
                                        "department",
                                        ArchiveItemQueryOperator.IN,
                                        List.of("LEGAL", "HR")))));
        bindDirectUserScope(scope, List.of());
        when(archiveCategoryService.listCategories(true)).thenReturn(List.of(category(11L, null)));
        when(archiveMetadataService.listFields(11L))
                .thenReturn(List.of(field(20L, 11L, "department", true)));

        ArchiveDataScopeResolutionTypes.ArchiveDataScopeFilter filter =
                dataScopeService.buildItemFilter(7L, 11L, null);

        assertThat(filter.empty()).isFalse();
        ArchiveSqlCondition condition = filter.groups().getFirst().conditions().getFirst();
        assertThat(condition.columnName()).isEqualTo("department");
        assertThat(condition.operator()).isEqualTo(ArchiveItemQueryOperator.IN);
        assertThat(condition.values()).isEqualTo(List.of("LEGAL", "HR"));
    }

    @Test
    @DisplayName("单分类范围为全量范围时不加载分类和字段元数据")
    void buildItemFilterShouldSkipMetadataForAllScope() {
        ArchiveDataScope allScope = scope(100L, ArchiveDataScopeType.ALL, true);
        bindDirectUserScope(allScope, List.of());

        assertThat(dataScopeService.buildItemFilter(7L, 11L, null).allData()).isTrue();
        verify(archiveCategoryService, never()).listCategories(any());
        verify(archiveMetadataService, never()).listFields(any());
    }

    @Test
    @DisplayName("单分类范围为空范围时不加载分类和字段元数据")
    void buildItemFilterShouldSkipMetadataForEmptyScope() {
        assertThat(dataScopeService.buildItemFilter(7L, 11L, null).empty()).isTrue();
        verify(archiveCategoryService, never()).listCategories(any());
        verify(archiveMetadataService, never()).listFields(any());
    }

    @Test
    @DisplayName("批量编译全量、空范围和纯固定维度时不加载字段元数据")
    void compileItemFiltersShouldLoadFieldsOnlyForDynamicConditions() {
        List<ArchiveCategoryDto> categories = List.of(category(11L, null), category(12L, null));

        assertThat(
                        dataScopeService.compileItemFilters(
                                ArchiveDataScopeResolutionTypes.ResolvedArchiveDataScope.all(),
                                categories,
                                null))
                .allSatisfy((ignored, filter) -> assertThat(filter.allData()).isTrue());
        assertThat(
                        dataScopeService.compileItemFilters(
                                ArchiveDataScopeResolutionTypes.ResolvedArchiveDataScope.none(),
                                categories,
                                null))
                .allSatisfy((ignored, filter) -> assertThat(filter.empty()).isTrue());

        ArchiveDataScope fixedScope = scope(100L, ArchiveDataScopeType.CONDITIONAL, true);
        ArchiveDataScopeDimension fondsDimension = new ArchiveDataScopeDimension();
        fondsDimension.setScopeId(100L);
        fondsDimension.setDimensionType(ArchiveDataScopeDimensionType.FONDS);
        fondsDimension.setTargetCode("F001");
        var resolved =
                ArchiveDataScopeResolutionTypes.ResolvedArchiveDataScope.conditional(
                        List.of(
                                new ArchiveDataScopeResolutionTypes.ResolvedScope(
                                        fixedScope, List.of(fondsDimension))));
        assertThat(dataScopeService.compileItemFilters(resolved, categories, null))
                .allSatisfy(
                        (ignored, filter) ->
                                assertThat(filter.groups().getFirst().fondsCodes())
                                        .containsExactly("F001"));

        verify(archiveMetadataService, never()).listFields(any());
    }

    @Test
    @DisplayName("批量编译复用已解析范围和分类元数据并为每个分类只加载一次字段")
    void compileItemFiltersShouldReuseResolvedScopeAndCategoryMetadata() {
        ArchiveDataScope scope = scope(100L, ArchiveDataScopeType.CONDITIONAL, true);
        scope.setDynamicCondition(
                new ArchiveDataScopeDynamicCondition(
                        List.of(
                                new ArchiveDataScopeDynamicCondition.DynamicFieldCondition(
                                        11L,
                                        "department",
                                        ArchiveItemQueryOperator.EQ,
                                        List.of("LEGAL")),
                                new ArchiveDataScopeDynamicCondition.DynamicFieldCondition(
                                        12L,
                                        "department",
                                        ArchiveItemQueryOperator.EQ,
                                        List.of("HR")))));
        var resolved =
                ArchiveDataScopeResolutionTypes.ResolvedArchiveDataScope.conditional(
                        List.of(
                                new ArchiveDataScopeResolutionTypes.ResolvedScope(
                                        scope, List.of())));
        List<ArchiveCategoryDto> categories =
                List.of(category(11L, null), category(12L, null), category(13L, null));
        when(archiveMetadataService.listFields(11L))
                .thenReturn(List.of(field(20L, 11L, "department", true)));
        when(archiveMetadataService.listFields(12L))
                .thenReturn(List.of(field(21L, 12L, "department", true)));

        Map<Long, ArchiveDataScopeResolutionTypes.ArchiveDataScopeFilter> filters =
                dataScopeService.compileItemFilters(resolved, categories, null);

        assertThat(filters).containsOnlyKeys(11L, 12L, 13L);
        assertThat(filters.get(11L).groups().getFirst().conditions().getFirst().value())
                .isEqualTo("LEGAL");
        assertThat(filters.get(12L).groups().getFirst().conditions().getFirst().value())
                .isEqualTo("HR");
        assertThat(filters.get(13L).empty()).isTrue();
        verify(archiveMetadataService).listFields(11L);
        verify(archiveMetadataService).listFields(12L);
        verify(archiveMetadataService, never()).listFields(13L);
        verify(archiveCategoryService, never()).listCategories(any());
        verify(subjectRelationRepository, never()).findBySubjectTypeAndSubjectId(any(), any());
    }

    @Test
    @DisplayName("分类继承忽略停用子分类但保留停用分类精确匹配")
    void compileItemFiltersShouldExcludeDisabledCategoryFromDescendantIndex() {
        ArchiveDataScope descendantScope = scope(100L, ArchiveDataScopeType.CONDITIONAL, true);
        ArchiveDataScopeDimension parentDimension = new ArchiveDataScopeDimension();
        parentDimension.setScopeId(100L);
        parentDimension.setDimensionType(ArchiveDataScopeDimensionType.CATEGORY);
        parentDimension.setTargetId(10L);
        parentDimension.setIncludeDescendants(true);
        var descendantResolved =
                ArchiveDataScopeResolutionTypes.ResolvedArchiveDataScope.conditional(
                        List.of(
                                new ArchiveDataScopeResolutionTypes.ResolvedScope(
                                        descendantScope, List.of(parentDimension))));
        List<ArchiveCategoryDto> categories =
                List.of(category(10L, null, true), category(11L, 10L, false));

        Map<Long, ArchiveDataScopeResolutionTypes.ArchiveDataScopeFilter> descendantFilters =
                dataScopeService.compileItemFilters(descendantResolved, categories, null);

        assertThat(descendantFilters.get(10L).empty()).isFalse();
        assertThat(descendantFilters.get(11L).empty()).isTrue();

        ArchiveDataScope exactScope = scope(101L, ArchiveDataScopeType.CONDITIONAL, true);
        ArchiveDataScopeDimension exactDimension = new ArchiveDataScopeDimension();
        exactDimension.setScopeId(101L);
        exactDimension.setDimensionType(ArchiveDataScopeDimensionType.CATEGORY);
        exactDimension.setTargetId(11L);
        var exactResolved =
                ArchiveDataScopeResolutionTypes.ResolvedArchiveDataScope.conditional(
                        List.of(
                                new ArchiveDataScopeResolutionTypes.ResolvedScope(
                                        exactScope, List.of(exactDimension))));

        assertThat(
                        dataScopeService
                                .compileItemFilters(exactResolved, categories, null)
                                .get(11L)
                                .empty())
                .isFalse();
    }

    @Test
    @DisplayName("动态字段条件只作用于声明分类，不随分类继承下发")
    void buildItemFilterShouldNotApplyDynamicConditionToDescendantCategory() {
        ArchiveDataScope scope = scope(100L, ArchiveDataScopeType.CONDITIONAL, true);
        ArchiveDataScopeDimension categoryDimension = new ArchiveDataScopeDimension();
        categoryDimension.setScopeId(100L);
        categoryDimension.setDimensionType(ArchiveDataScopeDimensionType.CATEGORY);
        categoryDimension.setTargetId(10L);
        categoryDimension.setIncludeDescendants(true);
        scope.setDynamicCondition(
                new ArchiveDataScopeDynamicCondition(
                        List.of(
                                new ArchiveDataScopeDynamicCondition.DynamicFieldCondition(
                                        10L,
                                        "department",
                                        ArchiveItemQueryOperator.EQ,
                                        List.of("LEGAL")))));
        bindDirectUserScope(scope, List.of(categoryDimension));
        when(archiveCategoryService.listCategories(true))
                .thenReturn(List.of(category(10L, null), category(11L, 10L)));
        when(archiveMetadataService.listFields(11L)).thenReturn(List.of());

        ArchiveDataScopeResolutionTypes.ArchiveDataScopeFilter filter =
                dataScopeService.buildItemFilter(7L, 11L, null);

        assertThat(filter.empty()).isTrue();
    }

    @Test
    @DisplayName("保存用户数据范围时拒绝不存在的目标用户")
    void saveUserDataScopesShouldRejectMissingUser() {
        when(authenticationUserRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dataScopeService.saveUserDataScopes(7L, List.of()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("用户不存在");
    }

    @Test
    @DisplayName("保存部门数据范围写入部门主体绑定")
    void saveDepartmentDataScopesShouldPersistDepartmentSubject() {
        ArchiveDataScope scope = scope(100L, ArchiveDataScopeType.CONDITIONAL, true);
        when(departmentService.requireEnabledDepartment(3L)).thenReturn(department(3L, true));
        when(dataScopeRepository.findById(100L)).thenReturn(Optional.of(scope));
        when(subjectRelationRepository.findBySubjectTypeAndSubjectId(
                        ArchiveDataScopeSubjectType.DEPARTMENT, 3L))
                .thenReturn(
                        List.of(subjectScope(ArchiveDataScopeSubjectType.DEPARTMENT, 3L, 100L)));

        ArchiveDataScopeService.DepartmentArchiveDataScopesResponse response =
                dataScopeService.saveDepartmentDataScopes(3L, List.of(100L));

        ArgumentCaptor<ArchiveDataScopeSubjectRelation> captor =
                ArgumentCaptor.forClass(ArchiveDataScopeSubjectRelation.class);
        verify(subjectRelationRepository)
                .deleteBySubjectTypeAndSubjectId(ArchiveDataScopeSubjectType.DEPARTMENT, 3L);
        verify(subjectRelationRepository).insert(captor.capture());
        assertThat(captor.getValue().getSubjectType())
                .isEqualTo(ArchiveDataScopeSubjectType.DEPARTMENT);
        assertThat(captor.getValue().getSubjectId()).isEqualTo(3L);
        assertThat(captor.getValue().getScopeId()).isEqualTo(100L);
        assertThat(response.departmentId()).isEqualTo(3L);
        assertThat(response.scopeIds()).containsExactly(100L);
    }

    @Test
    @DisplayName("保存部门数据范围拒绝停用部门")
    void saveDepartmentDataScopesShouldRejectDisabledDepartment() {
        doThrow(new BadRequestException("部门已停用", "departmentId", "部门已停用"))
                .when(departmentService)
                .requireEnabledDepartment(3L);

        assertThatThrownBy(() -> dataScopeService.saveDepartmentDataScopes(3L, List.of(100L)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("部门已停用");

        verify(subjectRelationRepository, never())
                .deleteBySubjectTypeAndSubjectId(ArchiveDataScopeSubjectType.DEPARTMENT, 3L);
        verify(subjectRelationRepository, never()).insert(any());
    }

    private static ArchiveDataScope scope(
            Long id, ArchiveDataScopeType scopeType, boolean enabled) {
        ArchiveDataScope scope = new ArchiveDataScope();
        scope.setId(id);
        scope.setScopeCode(scopeType == ArchiveDataScopeType.ALL ? "*" : "scope-" + id);
        scope.setScopeName("范围" + id);
        scope.setScopeType(scopeType);
        scope.setEnabled(enabled);
        return scope;
    }

    private static AuthorizationRole enabledRole(Long id) {
        AuthorizationRole role = new AuthorizationRole();
        role.setId(id);
        role.setRoleName("角色" + id);
        role.setEnabled(true);
        return role;
    }

    private static AuthorizationRole disabledRole(Long id) {
        AuthorizationRole role = enabledRole(id);
        role.setEnabled(false);
        return role;
    }

    private static AuthenticationUser enabledUser(Long id) {
        AuthenticationUser user = new AuthenticationUser();
        user.setId(id);
        user.setUsername("user" + id);
        user.setDisplayName("用户" + id);
        user.setEnabled(true);
        return user;
    }

    private static OrganizationDepartmentResponse department(Long id, boolean enabled) {
        LocalDateTime now = LocalDateTime.of(2026, 6, 30, 10, 0);
        return new OrganizationDepartmentResponse(
                id, "D" + id, "部门" + id, null, enabled, 0, now, now);
    }

    private static AuthorizationUserRoleRelation userRole(Long userId, Long roleId) {
        AuthorizationUserRoleRelation relation = new AuthorizationUserRoleRelation();
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        return relation;
    }

    private static ArchiveDataScopeSubjectRelation subjectScope(
            ArchiveDataScopeSubjectType subjectType, Long subjectId, Long scopeId) {
        ArchiveDataScopeSubjectRelation relation = new ArchiveDataScopeSubjectRelation();
        relation.setSubjectType(subjectType);
        relation.setSubjectId(subjectId);
        relation.setScopeId(scopeId);
        return relation;
    }

    private void bindDirectUserScope(
            ArchiveDataScope scope, List<ArchiveDataScopeDimension> dimensions) {
        when(subjectRelationRepository.findBySubjectTypeAndSubjectId(
                        ArchiveDataScopeSubjectType.USER, 7L))
                .thenReturn(
                        List.of(subjectScope(ArchiveDataScopeSubjectType.USER, 7L, scope.getId())));
        when(dataScopeRepository.findById(scope.getId())).thenReturn(Optional.of(scope));
        when(dimensionRepository.findByScopeId(scope.getId())).thenReturn(dimensions);
        when(userRoleRelationRepository.findByUserId(7L)).thenReturn(List.of());
    }

    private static ArchiveCategoryDto category(Long id, Long parentId) {
        return category(id, parentId, true);
    }

    private static ArchiveCategoryDto category(Long id, Long parentId, boolean enabled) {
        LocalDateTime now = LocalDateTime.of(2026, 6, 30, 10, 0);
        return new ArchiveCategoryDto(
                id,
                1L,
                parentId,
                "category-" + id,
                "分类" + id,
                ArchiveManagementMode.ITEM_ONLY,
                null,
                "am_archive_item_category_" + id,
                null,
                null,
                ArchiveTableStatus.BUILT,
                now,
                enabled,
                0,
                now,
                now);
    }

    private static ArchiveFieldDto field(
            Long id, Long categoryId, String fieldCode, boolean dataScopeFilterable) {
        return new ArchiveFieldDto(
                id,
                categoryId,
                ArchiveLevel.ITEM,
                ArchiveFieldScope.METADATA,
                fieldCode,
                "字段" + id,
                ArchiveFieldType.TEXT,
                fieldCode,
                100,
                null,
                null,
                ArchiveFieldControl.INPUT,
                true,
                null,
                0,
                true,
                1,
                0,
                true,
                1,
                0,
                true,
                dataScopeFilterable,
                true,
                0,
                null,
                null,
                null);
    }
}
