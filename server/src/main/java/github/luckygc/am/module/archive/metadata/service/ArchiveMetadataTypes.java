package github.luckygc.am.module.archive.metadata.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;

import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.metadata.ArchiveFieldControl;
import github.luckygc.am.module.archive.metadata.ArchiveFieldScope;
import github.luckygc.am.module.archive.metadata.ArchiveFieldSource;
import github.luckygc.am.module.archive.metadata.ArchiveFieldType;
import github.luckygc.am.module.archive.metadata.ArchiveFondsEventType;
import github.luckygc.am.module.archive.metadata.ArchiveFondsStatus;
import github.luckygc.am.module.archive.metadata.ArchiveLayoutSurface;
import github.luckygc.am.module.archive.metadata.ArchiveManagementMode;
import github.luckygc.am.module.archive.metadata.ArchiveTableStatus;

public abstract class ArchiveMetadataTypes {

    public record CreateArchiveFondsRequest(
            @Nullable String fondsCode,
            @Nullable String fondsName,
            @Nullable LocalDate startDate,
            @Nullable LocalDate endDate,
            @Nullable String historyNote,
            @Nullable Integer sortOrder) {}

    public record UpdateArchiveFondsRequest(
            @Nullable String fondsName,
            @Nullable LocalDate startDate,
            @Nullable LocalDate endDate,
            @Nullable String historyNote,
            @Nullable Integer sortOrder) {}

    public record AssignArchiveFondsNumberRequest(
            @Nullable String fondsNo,
            @Nullable String assignedBy,
            @Nullable String reason,
            @Nullable LocalDateTime effectiveAt) {}

    public record CloseArchiveFondsRequest(
            @Nullable String reason, @Nullable LocalDateTime effectiveAt) {}

    public record ReopenArchiveFondsRequest(
            @Nullable String reason, @Nullable LocalDateTime effectiveAt) {}

    public record ArchiveFondsDto(
            Long id,
            String fondsCode,
            @Nullable String fondsNo,
            String fondsName,
            ArchiveFondsStatus status,
            @Nullable String numberAssignedBy,
            @Nullable LocalDateTime numberAssignedAt,
            @Nullable LocalDate startDate,
            @Nullable LocalDate endDate,
            @Nullable String historyNote,
            @Nullable LocalDateTime closedAt,
            @Nullable String closureReason,
            int sortOrder,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    public record ArchiveFondsEventDto(
            Long id,
            String fondsCode,
            ArchiveFondsEventType eventType,
            @Nullable String previousValue,
            @Nullable String currentValue,
            String reason,
            LocalDateTime effectiveAt,
            @Nullable Long operatedBy,
            LocalDateTime createdAt) {}

    public record ArchiveFondsCategoryScopeRequest(
            @Nullable Long categoryId, @Nullable Integer sortOrder) {}

    public record ArchiveFondsCategoryScopeDto(
            @Nullable Long id,
            String fondsCode,
            Long categoryId,
            int sortOrder,
            @Nullable LocalDateTime createdAt,
            @Nullable LocalDateTime updatedAt) {}

    public record UpdateArchiveSecurityLevelRequest(@Nullable String levelName) {}

    public record ArchiveSecurityLevelDto(
            Long id,
            String levelName,
            boolean enabled,
            int sortOrder,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    public record UpdateArchiveRetentionPeriodRequest(@Nullable String periodName) {}

    public record ArchiveRetentionPeriodDto(
            Long id,
            String periodName,
            boolean enabled,
            int sortOrder,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    public record ArchiveCategoryRequest(
            @Nullable String categoryCode,
            @Nullable String categoryName,
            @Nullable Long parentId,
            @Nullable ArchiveManagementMode managementMode,
            @Nullable Boolean enabled,
            @Nullable Integer sortOrder) {}

    public record ArchiveCategoryDto(
            Long id,
            @Nullable Long parentId,
            String categoryCode,
            String categoryName,
            ArchiveManagementMode managementMode,
            @Nullable String volumeTableName,
            @Nullable String itemTableName,
            @Nullable String volumePhysicalTableName,
            @Nullable String itemPhysicalTableName,
            ArchiveTableStatus tableStatus,
            @Nullable LocalDateTime builtAt,
            boolean enabled,
            int sortOrder,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    public record ArchiveFieldRequest(
            @Nullable ArchiveLevel archiveLevel,
            @Nullable ArchiveFieldScope fieldScope,
            @Nullable String fieldCode,
            @Nullable String fieldName,
            @Nullable ArchiveFieldType fieldType,
            @Nullable Integer textLength,
            @Nullable Integer decimalPrecision,
            @Nullable Integer decimalScale,
            @Nullable ArchiveFieldControl editControl,
            @Nullable Boolean listVisible,
            @Nullable Integer listWidth,
            @Nullable Integer listSortOrder,
            @Nullable Boolean detailVisible,
            @Nullable Integer detailColSpan,
            @Nullable Integer detailSortOrder,
            @Nullable Boolean editVisible,
            @Nullable Integer editColSpan,
            @Nullable Integer editSortOrder,
            @Nullable Boolean exactSearchable,
            @Nullable Boolean dataScopeFilterable,
            @Nullable Boolean enabled,
            @Nullable Integer sortOrder) {}

    public record ArchiveFieldDto(
            Long id,
            Long categoryId,
            ArchiveLevel archiveLevel,
            ArchiveFieldScope fieldScope,
            String fieldCode,
            String fieldName,
            ArchiveFieldType fieldType,
            String columnName,
            @Nullable Integer textLength,
            @Nullable Integer decimalPrecision,
            @Nullable Integer decimalScale,
            ArchiveFieldControl editControl,
            boolean listVisible,
            @Nullable Integer listWidth,
            int listSortOrder,
            boolean detailVisible,
            int detailColSpan,
            int detailSortOrder,
            boolean editVisible,
            int editColSpan,
            int editSortOrder,
            boolean exactSearchable,
            boolean dataScopeFilterable,
            boolean enabled,
            int sortOrder,
            @Nullable ArchiveFieldSource fieldSource,
            @Nullable LocalDateTime createdAt,
            @Nullable LocalDateTime updatedAt) {}

    public record ArchiveFieldLayoutDto(
            ArchiveLayoutSurface surface, String scope, List<ArchiveFieldLayoutItemDto> items) {}

    public record ArchiveFieldLayoutItemDto(
            Long fieldId,
            String fieldCode,
            String fieldName,
            ArchiveFieldType fieldType,
            ArchiveFieldControl editControl,
            boolean visible,
            @Nullable Integer listWidth,
            int colSpan,
            int rowOrder,
            int colOrder) {}

    public record ArchiveFieldLayoutRequest(
            @Nullable List<@Nullable ArchiveFieldLayoutItemRequest> items) {}

    public record ArchiveFieldLayoutItemRequest(
            @Nullable Long fieldId,
            @Nullable Boolean visible,
            @Nullable Integer listWidth,
            @Nullable Integer colSpan,
            @Nullable Integer rowOrder,
            @Nullable Integer colOrder) {}

    public record ArchiveUniqueConstraintRequest(
            @Nullable ArchiveLevel archiveLevel,
            @Nullable String constraintCode,
            @Nullable String constraintName,
            @Nullable Boolean enabled,
            @Nullable List<Long> fieldIds) {}

    public record ArchiveUniqueConstraintDto(
            Long id,
            Long categoryId,
            ArchiveLevel archiveLevel,
            String constraintCode,
            String constraintName,
            @Nullable String indexName,
            boolean enabled,
            List<ArchiveUniqueConstraintFieldDto> fields,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    public record ArchiveUniqueConstraintFieldDto(
            Long fieldId,
            int fieldOrder,
            ArchiveLevel archiveLevel,
            String fieldCode,
            String fieldName,
            String columnName) {}
}
