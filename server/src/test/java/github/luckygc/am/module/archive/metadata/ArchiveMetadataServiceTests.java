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
import github.luckygc.am.module.archive.metadata.repository.ArchiveFieldDataRepository;
import github.luckygc.am.module.archive.metadata.repository.ArchiveFieldLayoutDataRepository;
import github.luckygc.am.module.archive.metadata.repository.ArchiveFondsDataRepository;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveUniqueConstraintDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveUniqueConstraintRequest;

@DisplayName("档案元数据服务")
class ArchiveMetadataServiceTests {

    private ArchiveMapper archiveMapper;
    private ArchiveCategoryDataRepository categoryRepository;
    private ArchiveFieldDataRepository fieldRepository;
    private ArchiveMetadataService service;

    @BeforeEach
    void setUp() {
        archiveMapper = mock(ArchiveMapper.class);
        ArchiveFondsDataRepository fondsRepository = mock(ArchiveFondsDataRepository.class);
        categoryRepository = mock(ArchiveCategoryDataRepository.class);
        fieldRepository = mock(ArchiveFieldDataRepository.class);
        ArchiveFieldLayoutDataRepository fieldLayoutRepository =
                mock(ArchiveFieldLayoutDataRepository.class);
        service =
                new ArchiveMetadataService(
                        archiveMapper,
                        fondsRepository,
                        categoryRepository,
                        fieldRepository,
                        fieldLayoutRepository);
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
        category.setCategoryCode("contract");
        category.setCategoryName("合同档案");
        category.setManagementMode(managementMode);
        category.setEnabled(true);
        return category;
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
