package github.luckygc.am.module.archive.metadata.service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.metadata.ArchiveFieldLayout;
import github.luckygc.am.module.archive.metadata.ArchiveFieldScope;
import github.luckygc.am.module.archive.metadata.ArchiveLayoutSurface;
import github.luckygc.am.module.archive.metadata.repository.ArchiveFieldLayoutDataRepository;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveFieldDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveFieldLayoutItemDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveFieldLayoutItemRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService.ArchiveFieldLayoutRequest;

@Service
public class ArchiveFieldLayoutService {

    private final ArchiveFieldLayoutDataRepository fieldLayoutRepository;
    private final ArchiveFieldDefinitionService fieldDefinitionService;

    public ArchiveFieldLayoutService(
            ArchiveFieldLayoutDataRepository fieldLayoutRepository,
            ArchiveFieldDefinitionService fieldDefinitionService) {
        this.fieldLayoutRepository = fieldLayoutRepository;
        this.fieldDefinitionService = fieldDefinitionService;
    }

    List<ArchiveFieldDto> applyEffectiveLayout(
            Long categoryId, ArchiveLayoutSurface surface, List<ArchiveFieldDto> fields) {
        Map<Long, ArchiveFieldLayoutItemDto> layoutsByFieldId =
                publicLayoutItems(categoryId, surface, fields).stream()
                        .collect(
                                Collectors.toMap(ArchiveFieldLayoutItemDto::fieldId, item -> item));
        return fields.stream()
                .map(field -> applyLayout(field, surface, layoutsByFieldId.get(field.id())))
                .sorted(
                        Comparator.comparingInt(
                                        (ArchiveFieldDto field) -> layoutOrder(surface, field))
                                .thenComparing(ArchiveFieldDto::id))
                .toList();
    }

    List<ArchiveFieldLayoutItemDto> publicLayoutItems(
            Long categoryId, ArchiveLayoutSurface surface, List<ArchiveFieldDto> fields) {
        List<ArchiveFieldLayoutItemDto> publicItems = layoutItems(categoryId, surface, fields);
        return publicItems.isEmpty() ? defaultLayoutItems(surface, fields) : publicItems;
    }

    void savePublicLayout(
            Long categoryId,
            ArchiveLayoutSurface surface,
            List<ArchiveFieldDto> enabledFields,
            @Nullable ArchiveFieldLayoutRequest request,
            Long userId) {
        List<@Nullable ArchiveFieldLayoutItemRequest> items =
                request == null || request.items() == null ? List.of() : request.items();
        Map<Long, ArchiveFieldDto> fieldsById =
                enabledFields.stream()
                        .collect(Collectors.toMap(ArchiveFieldDto::id, field -> field));
        Set<Long> seenFieldIds = new HashSet<>();
        fieldLayoutRepository.list(categoryId, surface).stream()
                .filter(layout -> fieldsById.containsKey(layout.getFieldId()))
                .forEach(
                        layout -> {
                            layout.setUpdatedBy(userId);
                            fieldLayoutRepository.update(layout);
                            fieldLayoutRepository.delete(layout);
                        });
        for (@Nullable ArchiveFieldLayoutItemRequest item : items) {
            if (item == null || item.fieldId() == null || !fieldsById.containsKey(item.fieldId())) {
                throw badRequest("布局字段只能选择当前分类字段");
            }
            if (!seenFieldIds.add(item.fieldId())) {
                throw badRequest("布局字段不能重复");
            }
            ArchiveFieldLayout layout = new ArchiveFieldLayout();
            layout.setCategoryId(categoryId);
            layout.setSurface(surface);
            layout.setFieldId(item.fieldId());
            layout.setVisible(item.visible() == null || item.visible());
            layout.setListWidth(
                    surface == ArchiveLayoutSurface.TABLE
                            ? fieldDefinitionService.normalizeListWidth(item.listWidth())
                            : null);
            layout.setColSpan(fieldDefinitionService.normalizeColSpan(item.colSpan()));
            layout.setRowOrder(item.rowOrder() == null ? 0 : item.rowOrder());
            layout.setColOrder(item.colOrder() == null ? 0 : item.colOrder());
            layout.setCreatedBy(userId);
            layout.setUpdatedBy(userId);
            fieldLayoutRepository.insert(layout);
        }
    }

    private List<ArchiveFieldLayoutItemDto> layoutItems(
            Long categoryId, ArchiveLayoutSurface surface, List<ArchiveFieldDto> fields) {
        Map<Long, ArchiveFieldDto> fieldsById =
                fields.stream().collect(Collectors.toMap(ArchiveFieldDto::id, field -> field));
        ArchiveLevel archiveLevel =
                fields.isEmpty() ? ArchiveLevel.ITEM : fields.getFirst().archiveLevel();
        ArchiveFieldScope fieldScope =
                fields.isEmpty() ? ArchiveFieldScope.METADATA : fields.getFirst().fieldScope();
        return fieldLayoutRepository.list(categoryId, surface).stream()
                .filter(layout -> fieldsById.containsKey(layout.getFieldId()))
                .filter(
                        layout ->
                                fieldsById.get(layout.getFieldId()).archiveLevel() == archiveLevel)
                .filter(layout -> fieldsById.get(layout.getFieldId()).fieldScope() == fieldScope)
                .map(layout -> mapFieldLayoutItem(layout, fieldsById.get(layout.getFieldId())))
                .toList();
    }

    private List<ArchiveFieldLayoutItemDto> defaultLayoutItems(
            ArchiveLayoutSurface surface, List<ArchiveFieldDto> fields) {
        return fields.stream()
                .map(
                        field ->
                                new ArchiveFieldLayoutItemDto(
                                        field.id(),
                                        field.fieldCode(),
                                        field.fieldName(),
                                        field.fieldType(),
                                        field.editControl(),
                                        surfaceVisible(surface, field),
                                        surface == ArchiveLayoutSurface.TABLE
                                                ? field.listWidth()
                                                : null,
                                        surfaceColSpan(surface, field),
                                        layoutOrder(surface, field),
                                        0))
                .sorted(
                        Comparator.comparingInt(ArchiveFieldLayoutItemDto::rowOrder)
                                .thenComparing(ArchiveFieldLayoutItemDto::fieldId))
                .toList();
    }

    private ArchiveFieldDto applyLayout(
            ArchiveFieldDto field,
            ArchiveLayoutSurface surface,
            @Nullable ArchiveFieldLayoutItemDto item) {
        if (item == null) {
            return field;
        }
        return switch (surface) {
            case TABLE ->
                    copyField(
                            field,
                            item.visible(),
                            item.listWidth(),
                            item.rowOrder(),
                            field.detailVisible(),
                            field.detailColSpan(),
                            field.detailSortOrder(),
                            field.editVisible(),
                            field.editColSpan(),
                            field.editSortOrder());
            case DETAIL ->
                    copyField(
                            field,
                            field.listVisible(),
                            field.listWidth(),
                            field.listSortOrder(),
                            item.visible(),
                            item.colSpan(),
                            item.rowOrder(),
                            field.editVisible(),
                            field.editColSpan(),
                            field.editSortOrder());
            case EDIT ->
                    copyField(
                            field,
                            field.listVisible(),
                            field.listWidth(),
                            field.listSortOrder(),
                            field.detailVisible(),
                            field.detailColSpan(),
                            field.detailSortOrder(),
                            item.visible(),
                            item.colSpan(),
                            item.rowOrder());
        };
    }

    private ArchiveFieldDto copyField(
            ArchiveFieldDto field,
            boolean listVisible,
            Integer listWidth,
            int listSortOrder,
            boolean detailVisible,
            int detailColSpan,
            int detailSortOrder,
            boolean editVisible,
            int editColSpan,
            int editSortOrder) {
        return new ArchiveFieldDto(
                field.id(),
                field.categoryId(),
                field.archiveLevel(),
                field.fieldScope(),
                field.fieldCode(),
                field.fieldName(),
                field.fieldType(),
                field.columnName(),
                field.textLength(),
                field.decimalPrecision(),
                field.decimalScale(),
                field.editControl(),
                listVisible,
                listWidth,
                listSortOrder,
                detailVisible,
                detailColSpan,
                detailSortOrder,
                editVisible,
                editColSpan,
                editSortOrder,
                field.exactSearchable(),
                field.dataScopeFilterable(),
                field.enabled(),
                field.sortOrder(),
                field.fieldSource(),
                field.createdAt(),
                field.updatedAt());
    }

    private ArchiveFieldLayoutItemDto mapFieldLayoutItem(
            ArchiveFieldLayout layout, ArchiveFieldDto field) {
        return new ArchiveFieldLayoutItemDto(
                layout.getFieldId(),
                field.fieldCode(),
                field.fieldName(),
                field.fieldType(),
                field.editControl(),
                layout.isVisible(),
                layout.getListWidth() == null ? field.listWidth() : layout.getListWidth(),
                layout.getColSpan(),
                layout.getRowOrder(),
                layout.getColOrder());
    }

    private boolean surfaceVisible(ArchiveLayoutSurface surface, ArchiveFieldDto field) {
        return switch (surface) {
            case TABLE -> field.listVisible();
            case DETAIL -> field.detailVisible();
            case EDIT -> field.editVisible();
        };
    }

    private int surfaceColSpan(ArchiveLayoutSurface surface, ArchiveFieldDto field) {
        return switch (surface) {
            case TABLE -> 1;
            case DETAIL -> field.detailColSpan();
            case EDIT -> field.editColSpan();
        };
    }

    private int layoutOrder(ArchiveLayoutSurface surface, ArchiveFieldDto field) {
        return switch (surface) {
            case TABLE -> field.listSortOrder();
            case DETAIL -> field.detailSortOrder();
            case EDIT -> field.editSortOrder();
        };
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
