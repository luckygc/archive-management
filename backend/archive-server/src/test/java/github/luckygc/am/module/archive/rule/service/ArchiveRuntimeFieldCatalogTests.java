package github.luckygc.am.module.archive.rule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.governance.ArchiveGovernanceSchemeVersion;
import github.luckygc.am.module.archive.governance.repository.ArchiveGovernanceSchemeVersionDataRepository;
import github.luckygc.am.module.archive.metadata.ArchiveCategory;
import github.luckygc.am.module.archive.metadata.ArchiveField;
import github.luckygc.am.module.archive.metadata.ArchiveFieldScope;
import github.luckygc.am.module.archive.metadata.ArchiveFieldType;
import github.luckygc.am.module.archive.metadata.repository.ArchiveCategoryDataRepository;
import github.luckygc.am.module.archive.metadata.repository.ArchiveFieldDataRepository;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeTriggerPoint;

@DisplayName("运行时真实字段目录")
class ArchiveRuntimeFieldCatalogTests {

    private ArchiveCategoryDataRepository categoryRepository;
    private ArchiveFieldDataRepository fieldRepository;
    private ArchiveRuntimeFieldCatalogService service;

    @BeforeEach
    void setUp() {
        ArchiveGovernanceSchemeVersionDataRepository versionRepository =
                mock(ArchiveGovernanceSchemeVersionDataRepository.class);
        categoryRepository = mock(ArchiveCategoryDataRepository.class);
        fieldRepository = mock(ArchiveFieldDataRepository.class);
        when(versionRepository.findById(11L))
                .thenReturn(Optional.of(new ArchiveGovernanceSchemeVersion()));
        service =
                new ArchiveRuntimeFieldCatalogService(
                        versionRepository, categoryRepository, fieldRepository);
    }

    @Test
    @DisplayName("创建条目目录直接组合固定字段动态字段实物字段和上下文")
    void itemCreateCatalogUsesRealFields() {
        ArchiveCategory category = category(21L, "DOC");
        when(categoryRepository.findByCategoryCode("DOC")).thenReturn(category);
        when(fieldRepository.list(21L, ArchiveLevel.ITEM, ArchiveFieldScope.METADATA, true))
                .thenReturn(List.of(field(31L, "title", "题名", ArchiveFieldType.TEXT, true)));
        when(fieldRepository.list(21L, ArchiveLevel.ITEM, ArchiveFieldScope.PHYSICAL, true))
                .thenReturn(List.of(field(32L, "boxNo", "盒号", ArchiveFieldType.INTEGER, false)));

        var catalog = service.catalog(11L, "DOC", ArchiveRuntimeTriggerPoint.ITEM_BEFORE_CREATE);

        assertThat(catalog.fieldsByCode())
                .containsKeys(
                        "item.archiveNo",
                        "metadata.title",
                        "physical.boxNo",
                        "context.userId",
                        "context.now");
        assertThat(catalog.fieldsByCode().get("metadata.title").writable()).isTrue();
        assertThat(catalog.fieldsByCode().get("physical.boxNo").writable()).isFalse();
        assertThat(catalog.fieldsByCode().get("context.userId").writable()).isFalse();
        assertThat(catalog.signature()).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("删除触发点的所有候选字段只读")
    void deleteCatalogHasNoWritableField() {
        ArchiveCategory category = category(21L, "DOC");
        when(categoryRepository.findByCategoryCode("DOC")).thenReturn(category);
        when(fieldRepository.list(21L, ArchiveLevel.ITEM, ArchiveFieldScope.METADATA, true))
                .thenReturn(List.of(field(31L, "title", "题名", ArchiveFieldType.TEXT, true)));
        when(fieldRepository.list(21L, ArchiveLevel.ITEM, ArchiveFieldScope.PHYSICAL, true))
                .thenReturn(List.of());

        var catalog = service.catalog(11L, "DOC", ArchiveRuntimeTriggerPoint.ITEM_BEFORE_DELETE);

        assertThat(catalog.fields()).noneMatch(field -> field.writable());
    }

    @Test
    @DisplayName("字段目录按分类编码隔离")
    void categoryCatalogsAreIsolated() {
        ArchiveCategory first = category(21L, "DOC");
        ArchiveCategory second = category(22L, "PHOTO");
        when(categoryRepository.findByCategoryCode("DOC")).thenReturn(first);
        when(categoryRepository.findByCategoryCode("PHOTO")).thenReturn(second);
        when(fieldRepository.list(21L, ArchiveLevel.ITEM, ArchiveFieldScope.METADATA, true))
                .thenReturn(List.of(field(31L, "title", "题名", ArchiveFieldType.TEXT, true)));
        when(fieldRepository.list(22L, ArchiveLevel.ITEM, ArchiveFieldScope.METADATA, true))
                .thenReturn(List.of(field(32L, "camera", "相机", ArchiveFieldType.TEXT, true)));
        when(fieldRepository.list(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.eq(ArchiveLevel.ITEM),
                        org.mockito.ArgumentMatchers.eq(ArchiveFieldScope.PHYSICAL),
                        org.mockito.ArgumentMatchers.eq(true)))
                .thenReturn(List.of());

        var doc = service.catalog(11L, "DOC", ArchiveRuntimeTriggerPoint.ITEM_BEFORE_UPDATE);
        var photo = service.catalog(11L, "PHOTO", ArchiveRuntimeTriggerPoint.ITEM_BEFORE_UPDATE);

        assertThat(doc.fieldsByCode())
                .containsKey("metadata.title")
                .doesNotContainKey("metadata.camera");
        assertThat(photo.fieldsByCode())
                .containsKey("metadata.camera")
                .doesNotContainKey("metadata.title");
    }

    @Test
    @DisplayName("导出目录只暴露真实存在的导出与上下文字段")
    void exportCatalogOnlyContainsExportFacts() {
        ArchiveCategory category = category(21L, "DOC");
        when(categoryRepository.findByCategoryCode("DOC")).thenReturn(category);

        var catalog = service.catalog(11L, "DOC", ArchiveRuntimeTriggerPoint.EXPORT_BEFORE_CREATE);

        assertThat(catalog.fieldsByCode())
                .containsKeys(
                        "export.itemCount",
                        "export.format",
                        "context.userId",
                        "context.now",
                        "context.operation")
                .doesNotContainKeys("item.archiveNo", "metadata.title", "physical.boxNo");
        assertThat(catalog.fields()).noneMatch(field -> field.writable());
    }

    private ArchiveCategory category(Long id, String code) {
        ArchiveCategory category = new ArchiveCategory();
        category.setId(id);
        category.setCategoryCode(code);
        category.setEnabled(true);
        return category;
    }

    private ArchiveField field(
            Long id, String code, String name, ArchiveFieldType type, boolean editVisible) {
        ArchiveField field = new ArchiveField();
        field.setId(id);
        field.setFieldCode(code);
        field.setFieldName(name);
        field.setFieldType(type);
        field.setEditVisible(editVisible);
        return field;
    }
}
