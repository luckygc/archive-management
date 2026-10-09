package github.luckygc.am.module.archive.metadata.service;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.metadata.ArchiveCategory;
import github.luckygc.am.module.archive.metadata.ArchiveFieldScope;
import github.luckygc.am.module.archive.metadata.ArchiveFieldType;
import github.luckygc.am.module.archive.metadata.repository.ArchiveCategoryDataRepository;
import github.luckygc.am.module.archive.metadata.repository.ArchiveFieldDataRepository;

/** 真实字段目录的只读定义查询，不依赖元数据写入和运行时规则校验。 */
@Service
public class ArchiveMetadataCatalogService {

    private final ArchiveCategoryDataRepository categoryRepository;
    private final ArchiveFieldDataRepository fieldRepository;

    public ArchiveMetadataCatalogService(
            ArchiveCategoryDataRepository categoryRepository,
            ArchiveFieldDataRepository fieldRepository) {
        this.categoryRepository = categoryRepository;
        this.fieldRepository = fieldRepository;
    }

    @Transactional(readOnly = true)
    public @Nullable CategoryDefinition findCategoryByCode(String categoryCode) {
        ArchiveCategory category = categoryRepository.findByCategoryCode(categoryCode);
        return category == null
                ? null
                : new CategoryDefinition(
                        category.getId(), category.getCategoryCode(), category.isEnabled());
    }

    @Transactional(readOnly = true)
    public List<FieldDefinition> listEnabledFields(
            Long categoryId, ArchiveLevel level, ArchiveFieldScope fieldScope) {
        return fieldRepository.list(categoryId, level, fieldScope, true).stream()
                .map(
                        field ->
                                new FieldDefinition(
                                        field.getFieldCode(),
                                        field.getFieldName(),
                                        field.getFieldType(),
                                        field.isEditVisible()))
                .toList();
    }

    public record CategoryDefinition(Long id, String categoryCode, boolean enabled) {}

    public record FieldDefinition(
            String fieldCode, String fieldName, ArchiveFieldType fieldType, boolean editVisible) {}
}
