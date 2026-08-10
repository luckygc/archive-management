package github.luckygc.am.module.archive.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.metadata.repository.ArchiveCategoryDataRepository;
import github.luckygc.am.module.archive.metadata.repository.ArchiveFieldDataRepository;
import github.luckygc.am.module.archive.metadata.repository.ArchiveFieldLayoutDataRepository;
import github.luckygc.am.module.archive.metadata.repository.ArchiveFondsCategoryScopeDataRepository;
import github.luckygc.am.module.archive.metadata.repository.ArchiveFondsDataRepository;
import github.luckygc.am.module.archive.metadata.repository.ArchiveRetentionPeriodDataRepository;
import github.luckygc.am.module.archive.metadata.repository.ArchiveSecurityLevelDataRepository;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveDynamicTableService;
import github.luckygc.am.module.archive.metadata.service.ArchiveFieldDefinitionService;
import github.luckygc.am.module.archive.metadata.service.ArchiveFieldLayoutService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataReferenceService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFondsCategoryScopeRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveUniqueConstraintDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveUniqueConstraintRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveUniqueConstraintService;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeFieldReferenceService;

@DisplayName("档案元数据服务")
class ArchiveMetadataServiceTests {

    private ArchiveMapper archiveMapper;
    private ArchiveFondsDataRepository fondsRepository;
    private ArchiveFondsCategoryScopeDataRepository fondsCategoryScopeRepository;
    private ArchiveCategoryDataRepository categoryRepository;
    private ArchiveFieldDataRepository fieldRepository;
    private ArchiveMetadataService service;
    private ArchiveCategoryService categoryService;
    private ArchiveMetadataReferenceService referenceService;

    @BeforeEach
    void setUp() {
        archiveMapper = mock(ArchiveMapper.class);
        fondsRepository = mock(ArchiveFondsDataRepository.class);
        fondsCategoryScopeRepository = mock(ArchiveFondsCategoryScopeDataRepository.class);
        categoryRepository = mock(ArchiveCategoryDataRepository.class);
        fieldRepository = mock(ArchiveFieldDataRepository.class);
        ArchiveFieldLayoutDataRepository fieldLayoutRepository =
                mock(ArchiveFieldLayoutDataRepository.class);
        ArchiveSecurityLevelDataRepository securityLevelRepository =
                mock(ArchiveSecurityLevelDataRepository.class);
        ArchiveRetentionPeriodDataRepository retentionPeriodRepository =
                mock(ArchiveRetentionPeriodDataRepository.class);
        ArchiveFieldDefinitionService fieldDefinitionService = new ArchiveFieldDefinitionService();
        ArchiveDynamicTableService dynamicTableService =
                new ArchiveDynamicTableService(archiveMapper, fieldDefinitionService);
        ArchiveFieldLayoutService fieldLayoutService =
                new ArchiveFieldLayoutService(fieldLayoutRepository, fieldDefinitionService);
        ArchiveUniqueConstraintService uniqueConstraintService =
                new ArchiveUniqueConstraintService(
                        archiveMapper, fieldDefinitionService, dynamicTableService);
        categoryService =
                new ArchiveCategoryService(
                        archiveMapper,
                        fondsRepository,
                        fondsCategoryScopeRepository,
                        categoryRepository);
        referenceService =
                new ArchiveMetadataReferenceService(
                        fondsRepository, securityLevelRepository, retentionPeriodRepository);
        service =
                new ArchiveMetadataService(
                        archiveMapper,
                        fieldRepository,
                        fieldDefinitionService,
                        dynamicTableService,
                        fieldLayoutService,
                        uniqueConstraintService,
                        categoryService,
                        mock(ArchiveRuntimeFieldReferenceService.class));
    }

    @Test
    @DisplayName("创建全局分类时保存分类")
    void createCategoryShouldPersistGlobalCategory() {
        when(categoryRepository.insert(org.mockito.ArgumentMatchers.any(ArchiveCategory.class)))
                .thenAnswer(invocation -> withCategoryId(invocation.getArgument(0), 12L));

        ArchiveMetadataTypes.ArchiveCategoryDto response =
                categoryService.createCategory(
                        new ArchiveMetadataTypes.ArchiveCategoryRequest(
                                "contract", "合同档案", null, ArchiveManagementMode.ITEM_ONLY, true, 0),
                        9L);

        assertThat(response.categoryCode()).isEqualTo("contract");
    }

    @Test
    @DisplayName("拒绝创建重复分类编码")
    void createCategoryShouldRejectGloballyDuplicateCode() {
        ArchiveCategory existing = category(12L, ArchiveManagementMode.ITEM_ONLY);
        when(categoryRepository.findByCategoryCode("contract")).thenReturn(existing);

        assertThatThrownBy(
                        () ->
                                categoryService.createCategory(
                                        new ArchiveMetadataTypes.ArchiveCategoryRequest(
                                                " contract ",
                                                "合同档案",
                                                null,
                                                ArchiveManagementMode.ITEM_ONLY,
                                                true,
                                                0),
                                        9L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        exception ->
                                assertThat(((ResponseStatusException) exception).getStatusCode())
                                        .isEqualTo(org.springframework.http.HttpStatus.CONFLICT))
                .hasMessageContaining("分类编码已存在");

        verify(categoryRepository, never()).insert(any(ArchiveCategory.class));
    }

    @Test
    @DisplayName("修改分类时允许保留自身编码")
    void updateCategoryShouldAllowKeepingOwnCode() {
        ArchiveCategory current = category(12L, ArchiveManagementMode.ITEM_ONLY);
        when(categoryRepository.findById(12L)).thenReturn(Optional.of(current));
        when(categoryRepository.update(any(ArchiveCategory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ArchiveMetadataTypes.ArchiveCategoryDto response =
                categoryService.updateCategory(
                        12L,
                        new ArchiveMetadataTypes.ArchiveCategoryRequest(
                                " contract ",
                                "合同档案",
                                null,
                                ArchiveManagementMode.ITEM_ONLY,
                                true,
                                0),
                        9L);

        assertThat(response.id()).isEqualTo(12L);
        assertThat(response.categoryCode()).isEqualTo("contract");
    }

    @Test
    @DisplayName("拒绝修改分类编码")
    void updateCategoryShouldRejectCategoryCodeChange() {
        ArchiveCategory current = category(12L, ArchiveManagementMode.ITEM_ONLY);
        when(categoryRepository.findById(12L)).thenReturn(Optional.of(current));

        assertThatThrownBy(
                        () ->
                                categoryService.updateCategory(
                                        12L,
                                        new ArchiveMetadataTypes.ArchiveCategoryRequest(
                                                "project",
                                                "项目档案",
                                                null,
                                                ArchiveManagementMode.ITEM_ONLY,
                                                true,
                                                0),
                                        9L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        exception ->
                                assertThat(((ResponseStatusException) exception).getStatusCode())
                                        .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST))
                .hasMessageContaining("分类编码创建后不可修改");

        verify(categoryRepository, never()).update(any(ArchiveCategory.class));
    }

    @Test
    @DisplayName("修改不存在分类时优先返回资源不存在")
    void updateCategoryShouldLoadTargetBeforeValidatingCode() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                categoryService.updateCategory(
                                        99L,
                                        new ArchiveMetadataTypes.ArchiveCategoryRequest(
                                                "occupied",
                                                "不存在分类",
                                                null,
                                                ArchiveManagementMode.ITEM_ONLY,
                                                true,
                                                0),
                                        9L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        exception ->
                                assertThat(((ResponseStatusException) exception).getStatusCode())
                                        .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("创建分类时拒绝超长编码和名称")
    void createCategoryShouldRejectOversizedCodeAndName() {
        assertThatThrownBy(
                        () ->
                                categoryService.createCategory(
                                        new ArchiveMetadataTypes.ArchiveCategoryRequest(
                                                "c".repeat(101),
                                                "合同档案",
                                                null,
                                                ArchiveManagementMode.ITEM_ONLY,
                                                true,
                                                0),
                                        9L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("分类编码长度不能超过 100");

        assertThatThrownBy(
                        () ->
                                categoryService.createCategory(
                                        new ArchiveMetadataTypes.ArchiveCategoryRequest(
                                                "contract",
                                                "名".repeat(256),
                                                null,
                                                ArchiveManagementMode.ITEM_ONLY,
                                                true,
                                                0),
                                        9L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("分类名称长度不能超过 255");

        verify(categoryRepository, never()).insert(any(ArchiveCategory.class));
    }

    @Test
    @DisplayName("创建分类时拒绝仅包含 Unicode 空白的编码")
    void createCategoryShouldRejectUnicodeBlankCode() {
        when(categoryRepository.insert(any(ArchiveCategory.class)))
                .thenAnswer(invocation -> withCategoryId(invocation.getArgument(0), 12L));

        assertThatThrownBy(
                        () ->
                                categoryService.createCategory(
                                        new ArchiveMetadataTypes.ArchiveCategoryRequest(
                                                "\u2003",
                                                "合同档案",
                                                null,
                                                ArchiveManagementMode.ITEM_ONLY,
                                                true,
                                                0),
                                        9L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("分类编码不能为空");

        verify(categoryRepository, never()).insert(any(ArchiveCategory.class));
    }

    @Test
    @DisplayName("创建分类时拒绝仅包含 Unicode 空白的名称")
    void createCategoryShouldRejectUnicodeBlankName() {
        when(categoryRepository.insert(any(ArchiveCategory.class)))
                .thenAnswer(invocation -> withCategoryId(invocation.getArgument(0), 12L));

        assertThatThrownBy(
                        () ->
                                categoryService.createCategory(
                                        new ArchiveMetadataTypes.ArchiveCategoryRequest(
                                                "contract",
                                                "\u2003",
                                                null,
                                                ArchiveManagementMode.ITEM_ONLY,
                                                true,
                                                0),
                                        9L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("分类名称不能为空");

        verify(categoryRepository, never()).insert(any(ArchiveCategory.class));
    }

    @Test
    @DisplayName("分类编码长度按 Unicode code point 校验")
    void createCategoryShouldValidateCodeLengthByUnicodeCodePoint() {
        when(categoryRepository.insert(any(ArchiveCategory.class)))
                .thenAnswer(invocation -> withCategoryId(invocation.getArgument(0), 12L));
        String maximumCode = "😀".repeat(100);

        assertThatCode(
                        () ->
                                categoryService.createCategory(
                                        new ArchiveMetadataTypes.ArchiveCategoryRequest(
                                                maximumCode,
                                                "合同档案",
                                                null,
                                                ArchiveManagementMode.ITEM_ONLY,
                                                true,
                                                0),
                                        9L))
                .doesNotThrowAnyException();
        assertThatThrownBy(
                        () ->
                                categoryService.createCategory(
                                        new ArchiveMetadataTypes.ArchiveCategoryRequest(
                                                "😀".repeat(101),
                                                "合同档案",
                                                null,
                                                ArchiveManagementMode.ITEM_ONLY,
                                                true,
                                                0),
                                        9L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("分类编码长度不能超过 100");
    }

    @Test
    @DisplayName("分类名称长度按 Unicode code point 校验")
    void createCategoryShouldValidateNameLengthByUnicodeCodePoint() {
        when(categoryRepository.insert(any(ArchiveCategory.class)))
                .thenAnswer(invocation -> withCategoryId(invocation.getArgument(0), 12L));
        String maximumName = "😀".repeat(255);

        assertThatCode(
                        () ->
                                categoryService.createCategory(
                                        new ArchiveMetadataTypes.ArchiveCategoryRequest(
                                                "contract",
                                                maximumName,
                                                null,
                                                ArchiveManagementMode.ITEM_ONLY,
                                                true,
                                                0),
                                        9L))
                .doesNotThrowAnyException();
        assertThatThrownBy(
                        () ->
                                categoryService.createCategory(
                                        new ArchiveMetadataTypes.ArchiveCategoryRequest(
                                                "contract-other",
                                                "😀".repeat(256),
                                                null,
                                                ArchiveManagementMode.ITEM_ONLY,
                                                true,
                                                0),
                                        9L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("分类名称长度不能超过 255");
    }

    @Test
    @DisplayName("指定分类编码唯一约束冲突返回资源已存在")
    void createCategoryShouldMapNamedUniqueConstraint() {
        when(categoryRepository.findByCategoryCode("contract")).thenReturn(null);
        var constraintViolation =
                new org.hibernate.exception.ConstraintViolationException(
                        "duplicate", new SQLException("duplicate"), "uk_am_archive_category_code");
        when(categoryRepository.insert(any(ArchiveCategory.class)))
                .thenThrow(
                        new org.springframework.dao.DataIntegrityViolationException(
                                "duplicate", constraintViolation));

        assertThatThrownBy(
                        () ->
                                categoryService.createCategory(
                                        new ArchiveMetadataTypes.ArchiveCategoryRequest(
                                                "contract",
                                                "合同档案",
                                                null,
                                                ArchiveManagementMode.ITEM_ONLY,
                                                true,
                                                0),
                                        9L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        exception ->
                                assertThat(((ResponseStatusException) exception).getStatusCode())
                                        .isEqualTo(org.springframework.http.HttpStatus.CONFLICT))
                .hasMessageContaining("分类编码已存在");
    }

    @Test
    @DisplayName("非分类编码约束的完整性异常保持原样")
    void createCategoryShouldRethrowUnrelatedIntegrityViolation() {
        when(categoryRepository.findByCategoryCode("contract")).thenReturn(null);
        var unrelated =
                new org.springframework.dao.DataIntegrityViolationException(
                        "other",
                        new org.hibernate.exception.ConstraintViolationException(
                                "other",
                                new SQLException("other"),
                                "fk_am_archive_category_scheme"));
        when(categoryRepository.insert(any(ArchiveCategory.class))).thenThrow(unrelated);

        assertThatThrownBy(
                        () ->
                                categoryService.createCategory(
                                        new ArchiveMetadataTypes.ArchiveCategoryRequest(
                                                "contract",
                                                "合同档案",
                                                null,
                                                ArchiveManagementMode.ITEM_ONLY,
                                                true,
                                                0),
                                        9L))
                .isSameAs(unrelated);
    }

    @Test
    @DisplayName("创建分类时允许选择全局树中的现有父级")
    void createCategoryShouldAllowExistingGlobalParent() {
        ArchiveCategory parent = category(20L, ArchiveManagementMode.ITEM_ONLY);
        when(categoryRepository.findById(20L)).thenReturn(Optional.of(parent));
        when(categoryRepository.insert(any(ArchiveCategory.class)))
                .thenAnswer(invocation -> withCategoryId(invocation.getArgument(0), 12L));

        ArchiveMetadataTypes.ArchiveCategoryDto response =
                categoryService.createCategory(
                        new ArchiveMetadataTypes.ArchiveCategoryRequest(
                                "contract", "合同档案", 20L, ArchiveManagementMode.ITEM_ONLY, true, 0),
                        9L);

        assertThat(response.parentId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("按全宗可用分类范围返回分类节点")
    void listCategoriesForFondsShouldUseCategoryScopes() {
        ArchiveFonds fonds = fonds("F001");
        ArchiveFondsCategoryScope scope = new ArchiveFondsCategoryScope();
        scope.setId(30L);
        scope.setFondsCode("F001");
        scope.setCategoryId(12L);
        scope.setSortOrder(0);
        ArchiveCategory category = category(12L, ArchiveManagementMode.ITEM_ONLY);
        when(fondsRepository.find("F001")).thenReturn(Optional.of(fonds));
        when(fondsCategoryScopeRepository.findByFondsCode("F001")).thenReturn(List.of(scope));
        when(categoryRepository.findById(12L)).thenReturn(Optional.of(category));

        List<ArchiveMetadataTypes.ArchiveCategoryDto> categories =
                categoryService.listCategoriesForFonds(" F001 ", true);

        assertThat(categories).extracting("id").containsExactly(12L);
    }

    @Test
    @DisplayName("全宗未勾选分类时返回空列表")
    void listCategoriesForFondsShouldNotFallbackToGlobalCategories() {
        when(fondsRepository.find("F001")).thenReturn(Optional.of(fonds("F001")));
        when(fondsCategoryScopeRepository.findByFondsCode("F001")).thenReturn(List.of());

        assertThat(categoryService.listCategoriesForFonds("F001", true)).isEmpty();
        verify(categoryRepository, never()).list();
    }

    @Test
    @DisplayName("保存全宗可用分类范围时覆盖旧关系")
    void saveFondsCategoryScopesShouldReplaceExistingScopes() {
        ArchiveFonds fonds = fonds("F001");
        ArchiveFondsCategoryScope existing = new ArchiveFondsCategoryScope();
        existing.setId(30L);
        existing.setFondsCode("F001");
        existing.setCategoryId(11L);
        ArchiveCategory category = category(12L, ArchiveManagementMode.ITEM_ONLY);
        when(fondsRepository.find("F001")).thenReturn(Optional.of(fonds));
        when(categoryRepository.findById(12L)).thenReturn(Optional.of(category));
        when(fondsCategoryScopeRepository.findByFondsCode("F001")).thenReturn(List.of(existing));
        when(fondsCategoryScopeRepository.insertAll(org.mockito.ArgumentMatchers.anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<ArchiveMetadataTypes.ArchiveFondsCategoryScopeDto> result =
                categoryService.saveFondsCategoryScopes(
                        " F001 ", List.of(new ArchiveFondsCategoryScopeRequest(12L, 1)), 9L);

        assertThat(result).extracting("categoryId").containsExactly(12L);
        assertThat(result).extracting("sortOrder").containsExactly(1);
        verify(fondsCategoryScopeRepository).deleteAll(List.of(existing));
    }

    @Test
    @DisplayName("全宗未配置分类时拒绝写入")
    void requireCategoryAvailableForFondsShouldRejectMissingScope() {
        when(fondsCategoryScopeRepository.findByFondsCodeAndCategoryId("F001", 12L))
                .thenReturn(List.of());

        assertThatThrownBy(() -> categoryService.requireCategoryAvailableForFonds(" F001 ", 12L))
                .isInstanceOf(github.luckygc.am.common.exception.BadRequestException.class)
                .hasMessageContaining("该全宗未配置此分类");
    }

    @Test
    @DisplayName("获取可用全宗时拒绝停用全宗")
    void getWritableFondsByCodeShouldRejectClosedFonds() {
        ArchiveFonds fonds = new ArchiveFonds();
        fonds.setId(1L);
        fonds.setFondsCode("F001");
        fonds.setFondsName("停用全宗");
        fonds.setStatus(ArchiveFondsStatus.CLOSED);
        when(fondsRepository.find("F001")).thenReturn(Optional.of(fonds));

        assertThatThrownBy(() -> referenceService.getWritableFondsByCode(" F001 "))
                .isInstanceOf(github.luckygc.am.common.exception.BadRequestException.class)
                .hasMessageContaining("全宗不存在或已封闭");
    }

    @Test
    @DisplayName("获取可用全宗时将不存在全宗视为不可用")
    void getWritableFondsByCodeShouldRejectMissingFonds() {
        when(fondsRepository.find("F001")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> referenceService.getWritableFondsByCode("F001"))
                .isInstanceOf(github.luckygc.am.common.exception.BadRequestException.class)
                .hasMessageContaining("全宗不存在或已封闭");
    }

    @Test
    @DisplayName("允许卷内电子字段参与唯一规则")
    void createUniqueConstraintShouldAcceptItemMetadataFields() {
        ArchiveCategory category = category(1L, ArchiveManagementMode.VOLUME_ITEM);
        ArchiveField field = field(11L, ArchiveLevel.ITEM, ArchiveFieldScope.METADATA);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(fieldRepository.list(1L)).thenReturn(List.of(field));
        when(archiveMapper.insertUniqueConstraint(
                        eq(1L),
                        eq(ArchiveLevel.ITEM.value()),
                        eq("doc_no_unique"),
                        eq("文号唯一"),
                        anyString(),
                        eq(true)))
                .thenReturn(21L);
        when(archiveMapper.getUniqueConstraint(21L))
                .thenReturn(uniqueConstraintRow(21L, 1L, ArchiveLevel.ITEM));
        when(archiveMapper.listUniqueConstraintFields(21L))
                .thenReturn(List.of(uniqueConstraintFieldRow(field)));

        ArchiveUniqueConstraintDto result =
                service.createUniqueConstraint(
                        1L,
                        new ArchiveUniqueConstraintRequest(
                                ArchiveLevel.ITEM, "doc_no_unique", "文号唯一", true, List.of(11L)),
                        9L);

        assertThat(result.archiveLevel()).isEqualTo(ArchiveLevel.ITEM);
        assertThat(result.fields()).extracting("fieldId").containsExactly(11L);
        verify(archiveMapper).markFieldsExactSearchable(1L, List.of(11L));
    }

    @Test
    @DisplayName("拒绝实物字段参与唯一规则")
    void createUniqueConstraintShouldRejectPhysicalFields() {
        ArchiveCategory category = category(1L, ArchiveManagementMode.VOLUME_ITEM);
        ArchiveField field = field(11L, ArchiveLevel.ITEM, ArchiveFieldScope.PHYSICAL);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(fieldRepository.list(1L)).thenReturn(List.of(field));

        assertThatThrownBy(
                        () ->
                                service.createUniqueConstraint(
                                        1L,
                                        new ArchiveUniqueConstraintRequest(
                                                ArchiveLevel.ITEM,
                                                "box_no_unique",
                                                "盒号唯一",
                                                true,
                                                List.of(11L)),
                                        9L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("唯一约束字段必须是案卷或卷内电子字段");

        verifyNoInteractionsOnUniqueConstraintWrites();
    }

    @Test
    @DisplayName("拒绝未启用案卷管理分类创建案卷唯一规则")
    void createUniqueConstraintShouldRejectVolumeRuleWhenCategoryDoesNotUseVolumes() {
        ArchiveCategory category = category(1L, ArchiveManagementMode.ITEM_ONLY);
        ArchiveField field = field(11L, ArchiveLevel.VOLUME, ArchiveFieldScope.METADATA);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(fieldRepository.list(1L)).thenReturn(List.of(field));

        assertThatThrownBy(
                        () ->
                                service.createUniqueConstraint(
                                        1L,
                                        new ArchiveUniqueConstraintRequest(
                                                ArchiveLevel.VOLUME,
                                                "volume_no_unique",
                                                "案卷号唯一",
                                                true,
                                                List.of(11L)),
                                        9L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("该分类未启用案卷管理");

        verifyNoInteractionsOnUniqueConstraintWrites();
    }

    private void verifyNoInteractionsOnUniqueConstraintWrites() {
        verifyNoInteractions(archiveMapper);
    }

    private ArchiveCategory category(Long id, ArchiveManagementMode managementMode) {
        ArchiveCategory category = new ArchiveCategory();
        category.setId(id);
        category.setCategoryCode("contract");
        category.setCategoryName("合同档案");
        category.setManagementMode(managementMode);
        category.setEnabled(true);
        return category;
    }

    private ArchiveCategory withCategoryId(ArchiveCategory category, Long id) {
        category.setId(id);
        return category;
    }

    private ArchiveFonds fonds(String fondsCode) {
        ArchiveFonds fonds = new ArchiveFonds();
        fonds.setId(1L);
        fonds.setFondsCode(fondsCode);
        fonds.setFondsName("默认全宗");
        fonds.setStatus(ArchiveFondsStatus.ACTIVE);
        return fonds;
    }

    private ArchiveField field(Long id, ArchiveLevel archiveLevel, ArchiveFieldScope fieldScope) {
        ArchiveField field = new ArchiveField();
        field.setId(id);
        field.setCategoryId(1L);
        field.setArchiveLevel(archiveLevel);
        field.setFieldScope(fieldScope);
        field.setFieldCode("doc_no");
        field.setFieldName("文号");
        field.setFieldType(ArchiveFieldType.TEXT);
        field.setColumnName("f_doc_no");
        field.setTextLength(100);
        field.setEditControl(ArchiveFieldControl.INPUT);
        field.setEnabled(true);
        return field;
    }

    private Map<String, Object> uniqueConstraintRow(
            Long id, Long categoryId, ArchiveLevel archiveLevel) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("categoryId", categoryId);
        row.put("archiveLevel", archiveLevel.value());
        row.put("constraintCode", "doc_no_unique");
        row.put("constraintName", "文号唯一");
        row.put("indexName", "uk_archive_item_contract_item_doc_no_unique_048ef3fc8efa");
        row.put("enabled", true);
        return row;
    }

    private Map<String, Object> uniqueConstraintFieldRow(ArchiveField field) {
        Map<String, Object> row = new HashMap<>();
        row.put("fieldId", field.getId());
        row.put("fieldOrder", 0);
        row.put("archiveLevel", field.getArchiveLevel().value());
        row.put("fieldCode", field.getFieldCode());
        row.put("fieldName", field.getFieldName());
        row.put("columnName", field.getColumnName());
        return row;
    }
}
