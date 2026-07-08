package github.luckygc.am.module.archive.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import github.luckygc.am.module.archive.metadata.repository.ArchiveClassificationSchemeDataRepository;
import github.luckygc.am.module.archive.metadata.repository.ArchiveFieldDataRepository;
import github.luckygc.am.module.archive.metadata.repository.ArchiveFieldLayoutDataRepository;
import github.luckygc.am.module.archive.metadata.repository.ArchiveFondsCategoryScopeDataRepository;
import github.luckygc.am.module.archive.metadata.repository.ArchiveFondsDataRepository;
import github.luckygc.am.module.archive.metadata.repository.ArchiveRetentionPeriodDataRepository;
import github.luckygc.am.module.archive.metadata.repository.ArchiveSecurityLevelDataRepository;
import github.luckygc.am.module.archive.metadata.service.ArchiveDynamicTableService;
import github.luckygc.am.module.archive.metadata.service.ArchiveFieldDefinitionService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveClassificationSchemeDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveClassificationSchemeRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveFondsCategoryScopeRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveUniqueConstraintDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveUniqueConstraintRequest;

@DisplayName("档案元数据服务")
class ArchiveMetadataServiceTests {

    private ArchiveMapper archiveMapper;
    private ArchiveFondsDataRepository fondsRepository;
    private ArchiveClassificationSchemeDataRepository classificationSchemeRepository;
    private ArchiveFondsCategoryScopeDataRepository fondsCategoryScopeRepository;
    private ArchiveCategoryDataRepository categoryRepository;
    private ArchiveFieldDataRepository fieldRepository;
    private ArchiveMetadataService service;

    @BeforeEach
    void setUp() {
        archiveMapper = mock(ArchiveMapper.class);
        fondsRepository = mock(ArchiveFondsDataRepository.class);
        classificationSchemeRepository = mock(ArchiveClassificationSchemeDataRepository.class);
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
        service =
                new ArchiveMetadataService(
                        archiveMapper,
                        fondsRepository,
                        classificationSchemeRepository,
                        fondsCategoryScopeRepository,
                        categoryRepository,
                        fieldRepository,
                        fieldLayoutRepository,
                        securityLevelRepository,
                        retentionPeriodRepository,
                        fieldDefinitionService,
                        dynamicTableService);
    }

    @Test
    @DisplayName("创建分类方案时保存编码名称和审计人")
    void createClassificationSchemeShouldPersistScheme() {
        when(classificationSchemeRepository.findBySchemeCode("enterprise_project"))
                .thenReturn(null);
        when(classificationSchemeRepository.insert(
                        org.mockito.ArgumentMatchers.any(ArchiveClassificationScheme.class)))
                .thenAnswer(invocation -> withSchemeId(invocation.getArgument(0), 5L));

        ArchiveClassificationSchemeDto response =
                service.createClassificationScheme(
                        new ArchiveClassificationSchemeRequest(
                                " enterprise_project ", " 企业项目档案分类 ", " 项目制度 ", true, 3),
                        9L);

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.schemeCode()).isEqualTo("enterprise_project");
        assertThat(response.schemeName()).isEqualTo("企业项目档案分类");
    }

    @Test
    @DisplayName("创建分类时必须归属启用分类方案")
    void createCategoryShouldRequireEnabledClassificationScheme() {
        ArchiveClassificationScheme scheme = scheme(8L, "default_classification", true);
        when(classificationSchemeRepository.findById(8L)).thenReturn(Optional.of(scheme));
        when(categoryRepository.insert(org.mockito.ArgumentMatchers.any(ArchiveCategory.class)))
                .thenAnswer(invocation -> withCategoryId(invocation.getArgument(0), 12L));

        ArchiveMetadataService.ArchiveCategoryDto response =
                service.createCategory(
                        new ArchiveMetadataService.ArchiveCategoryRequest(
                                8L,
                                "contract",
                                "合同档案",
                                null,
                                ArchiveManagementMode.ITEM_ONLY,
                                true,
                                0),
                        9L);

        assertThat(response.schemeId()).isEqualTo(8L);
        assertThat(response.categoryCode()).isEqualTo("contract");
    }

    @Test
    @DisplayName("拒绝把不同分类方案下的分类设置为父级")
    void createCategoryShouldRejectParentFromDifferentScheme() {
        ArchiveClassificationScheme scheme = scheme(8L, "default_classification", true);
        ArchiveCategory parent = category(20L, ArchiveManagementMode.ITEM_ONLY);
        parent.setSchemeId(7L);
        when(classificationSchemeRepository.findById(8L)).thenReturn(Optional.of(scheme));
        when(categoryRepository.findById(20L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(
                        () ->
                                service.createCategory(
                                        new ArchiveMetadataService.ArchiveCategoryRequest(
                                                8L,
                                                "contract",
                                                "合同档案",
                                                20L,
                                                ArchiveManagementMode.ITEM_ONLY,
                                                true,
                                                0),
                                        9L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("父级分类必须属于同一分类方案");
    }

    @Test
    @DisplayName("按全宗可用分类范围返回分类节点")
    void listCategoriesForFondsShouldUseCategoryScopes() {
        ArchiveFonds fonds = fonds("F001");
        ArchiveClassificationScheme scheme = scheme(8L, "enterprise_project", true);
        ArchiveFondsCategoryScope scope = new ArchiveFondsCategoryScope();
        scope.setId(30L);
        scope.setFondsCode("F001");
        scope.setCategoryId(12L);
        scope.setDefaultFlag(true);
        scope.setSortOrder(0);
        ArchiveCategory category = category(12L, ArchiveManagementMode.ITEM_ONLY);
        category.setSchemeId(8L);
        when(fondsRepository.find("F001")).thenReturn(Optional.of(fonds));
        when(classificationSchemeRepository.findById(8L)).thenReturn(Optional.of(scheme));
        when(fondsCategoryScopeRepository.findByFondsCode("F001")).thenReturn(List.of(scope));
        when(categoryRepository.findById(12L)).thenReturn(Optional.of(category));

        List<ArchiveMetadataService.ArchiveCategoryDto> categories =
                service.listCategoriesForFonds(" F001 ", true);

        assertThat(categories).extracting("id").containsExactly(12L);
        assertThat(categories).extracting("schemeId").containsExactly(8L);
    }

    @Test
    @DisplayName("保存全宗可用分类范围时覆盖旧关系")
    void saveFondsCategoryScopesShouldReplaceExistingScopes() {
        ArchiveFonds fonds = fonds("F001");
        ArchiveClassificationScheme scheme = scheme(8L, "enterprise_project", true);
        ArchiveFondsCategoryScope existing = new ArchiveFondsCategoryScope();
        existing.setId(30L);
        existing.setFondsCode("F001");
        existing.setCategoryId(11L);
        ArchiveCategory category = category(12L, ArchiveManagementMode.ITEM_ONLY);
        category.setSchemeId(8L);
        when(fondsRepository.find("F001")).thenReturn(Optional.of(fonds));
        when(classificationSchemeRepository.findById(8L)).thenReturn(Optional.of(scheme));
        when(categoryRepository.findById(12L)).thenReturn(Optional.of(category));
        when(fondsCategoryScopeRepository.findByFondsCode("F001")).thenReturn(List.of(existing));
        when(fondsCategoryScopeRepository.insertAll(org.mockito.ArgumentMatchers.anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<ArchiveMetadataService.ArchiveFondsCategoryScopeDto> result =
                service.saveFondsCategoryScopes(
                        " F001 ", List.of(new ArchiveFondsCategoryScopeRequest(12L, true, 1)), 9L);

        assertThat(result).extracting("categoryId").containsExactly(12L);
        assertThat(result).extracting("defaultFlag").containsExactly(true);
        verify(fondsCategoryScopeRepository).deleteAll(List.of(existing));
    }

    @Test
    @DisplayName("获取可用全宗时拒绝停用全宗")
    void getEnabledFondsByCodeShouldRejectDisabledFonds() {
        ArchiveFonds fonds = new ArchiveFonds();
        fonds.setId(1L);
        fonds.setFondsCode("F001");
        fonds.setFondsName("停用全宗");
        fonds.setEnabled(false);
        when(fondsRepository.find("F001")).thenReturn(Optional.of(fonds));

        assertThatThrownBy(() -> service.getEnabledFondsByCode(" F001 "))
                .isInstanceOf(github.luckygc.am.common.exception.BadRequestException.class)
                .hasMessageContaining("全宗不可用");
    }

    @Test
    @DisplayName("获取可用全宗时将不存在全宗视为不可用")
    void getEnabledFondsByCodeShouldRejectMissingFonds() {
        when(fondsRepository.find("F001")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEnabledFondsByCode("F001"))
                .isInstanceOf(github.luckygc.am.common.exception.BadRequestException.class)
                .hasMessageContaining("全宗不可用");
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
                        eq(true),
                        eq(9L)))
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
        verify(archiveMapper).markFieldsExactSearchable(1L, List.of(11L), 9L);
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
        category.setSchemeId(1L);
        category.setCategoryCode("contract");
        category.setCategoryName("合同档案");
        category.setManagementMode(managementMode);
        category.setEnabled(true);
        return category;
    }

    private ArchiveClassificationScheme scheme(Long id, String schemeCode, boolean enabled) {
        ArchiveClassificationScheme scheme = new ArchiveClassificationScheme();
        scheme.setId(id);
        scheme.setSchemeCode(schemeCode);
        scheme.setSchemeName("分类方案");
        scheme.setEnabled(enabled);
        return scheme;
    }

    private ArchiveClassificationScheme withSchemeId(ArchiveClassificationScheme scheme, Long id) {
        scheme.setId(id);
        return scheme;
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
        fonds.setEnabled(true);
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
