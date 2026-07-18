package github.luckygc.am.module.archive.metadata.service;

import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.metadata.ArchiveDynamicTableNames;
import github.luckygc.am.module.archive.metadata.ArchiveField;
import github.luckygc.am.module.archive.metadata.ArchiveFieldControl;
import github.luckygc.am.module.archive.metadata.ArchiveFieldScope;
import github.luckygc.am.module.archive.metadata.ArchiveFieldType;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFieldDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFieldRequest;

@Service
public class ArchiveFieldDefinitionService {

    private static final Pattern FIELD_CODE_PATTERN = Pattern.compile("[a-z][a-z0-9_]*");
    private static final int POSTGRESQL_IDENTIFIER_LIMIT = 63;
    private static final int DEFAULT_TEXT_LENGTH = 500;
    private static final int DEFAULT_DECIMAL_PRECISION = 18;
    private static final int DEFAULT_DECIMAL_SCALE = 2;
    private static final Set<String> RESERVED_RECORD_FIELD_CODES =
            Set.of(
                    "id",
                    "category_code",
                    "category_name",
                    "archive_no",
                    "electronic_status",
                    "security_level_id",
                    "retention_period_id",
                    "sort_order",
                    "archived_at",
                    "archive_year",
                    "locked_flag",
                    "lock_reason",
                    "locked_by",
                    "locked_at",
                    "deleted_flag",
                    "deleted_at",
                    "deleted_by",
                    "created_by",
                    "created_at",
                    "updated_by",
                    "updated_at",
                    "fonds_code",
                    "fonds_name");

    ArchiveFieldValues validate(ArchiveFieldRequest request) {
        validateRequired(request.fieldCode(), "字段编码不能为空");
        validateRequired(request.fieldName(), "字段名称不能为空");
        String fieldCode = request.fieldCode().trim();
        if (!FIELD_CODE_PATTERN.matcher(fieldCode).matches()) {
            throw badRequest("字段编码只允许小写字母、数字和下划线，并且必须以小写字母开头");
        }
        if (RESERVED_RECORD_FIELD_CODES.contains(fieldCode)) {
            throw badRequest("字段编码属于档案记录固定字段，不能作为动态字段：" + fieldCode);
        }
        String columnName = toColumnName(fieldCode);
        if (columnName.length() > POSTGRESQL_IDENTIFIER_LIMIT) {
            throw badRequest("字段编码过长，生成的动态列名超过 PostgreSQL 标识符长度限制");
        }
        ArchiveFieldType fieldType = request.fieldType();
        if (fieldType == null) {
            throw badRequest("字段类型不能为空");
        }
        Integer textLength = request.textLength();
        if (fieldType == ArchiveFieldType.TEXT && (textLength == null || textLength <= 0)) {
            textLength = DEFAULT_TEXT_LENGTH;
        }
        Integer decimalPrecision = request.decimalPrecision();
        Integer decimalScale = request.decimalScale();
        if (fieldType == ArchiveFieldType.DECIMAL) {
            decimalPrecision =
                    decimalPrecision == null ? DEFAULT_DECIMAL_PRECISION : decimalPrecision;
            decimalScale = decimalScale == null ? DEFAULT_DECIMAL_SCALE : decimalScale;
            if (decimalPrecision <= 0 || decimalScale < 0 || decimalScale >= decimalPrecision) {
                throw badRequest("小数字段精度配置不合法");
            }
        }
        ArchiveFieldControl editControl = defaultEditControl(fieldType, request.editControl());
        validateEditControl(fieldType, editControl);
        return new ArchiveFieldValues(
                normalizeArchiveLevel(request.archiveLevel()),
                normalizeFieldScope(request.fieldScope()),
                fieldCode,
                request.fieldName().trim(),
                fieldType,
                columnName,
                textLength,
                decimalPrecision,
                decimalScale,
                editControl,
                request.listVisible() == null || request.listVisible(),
                normalizeListWidth(request.listWidth()),
                request.listSortOrder() == null
                        ? layoutOrder(request.sortOrder())
                        : request.listSortOrder(),
                request.detailVisible() == null || request.detailVisible(),
                normalizeColSpan(request.detailColSpan()),
                request.detailSortOrder() == null
                        ? layoutOrder(request.sortOrder())
                        : request.detailSortOrder(),
                request.editVisible() == null || request.editVisible(),
                normalizeColSpan(request.editColSpan()),
                request.editSortOrder() == null
                        ? layoutOrder(request.sortOrder())
                        : request.editSortOrder(),
                request.exactSearchable() != null && request.exactSearchable(),
                request.dataScopeFilterable() != null && request.dataScopeFilterable(),
                request.enabled() == null || request.enabled(),
                request.sortOrder() == null ? 0 : request.sortOrder());
    }

    void applyValues(ArchiveField field, Long categoryId, ArchiveFieldValues values) {
        field.setCategoryId(categoryId);
        field.setArchiveLevel(values.archiveLevel());
        field.setFieldScope(values.fieldScope());
        field.setFieldCode(values.fieldCode());
        field.setFieldName(values.fieldName());
        field.setFieldType(values.fieldType());
        field.setColumnName(values.columnName());
        field.setTextLength(values.textLength());
        field.setDecimalPrecision(values.decimalPrecision());
        field.setDecimalScale(values.decimalScale());
        field.setEditControl(values.editControl());
        field.setListVisible(values.listVisible());
        field.setListWidth(values.listWidth());
        field.setListSortOrder(values.listSortOrder());
        field.setDetailVisible(values.detailVisible());
        field.setDetailColSpan(values.detailColSpan());
        field.setDetailSortOrder(values.detailSortOrder());
        field.setEditVisible(values.editVisible());
        field.setEditColSpan(values.editColSpan());
        field.setEditSortOrder(values.editSortOrder());
        field.setExactSearchable(values.exactSearchable());
        field.setDataScopeFilterable(values.dataScopeFilterable());
        field.setEnabled(values.enabled());
        field.setSortOrder(values.sortOrder());
    }

    String sqlType(ArchiveFieldDto field) {
        return switch (field.fieldType()) {
            case TEXT ->
                    "varchar("
                            + (field.textLength() == null
                                    ? DEFAULT_TEXT_LENGTH
                                    : field.textLength())
                            + ")";
            case INTEGER -> "integer";
            case DECIMAL ->
                    "numeric(%d,%d)"
                            .formatted(
                                    field.decimalPrecision() == null
                                            ? DEFAULT_DECIMAL_PRECISION
                                            : field.decimalPrecision(),
                                    field.decimalScale() == null
                                            ? DEFAULT_DECIMAL_SCALE
                                            : field.decimalScale());
            case DATE -> "date";
            case DATETIME -> "timestamp";
        };
    }

    ArchiveLevel normalizeArchiveLevel(@Nullable ArchiveLevel archiveLevel) {
        return ArchiveDynamicTableNames.normalizeArchiveLevel(archiveLevel);
    }

    ArchiveFieldScope normalizeFieldScope(@Nullable ArchiveFieldScope fieldScope) {
        return ArchiveDynamicTableNames.normalizeFieldScope(fieldScope);
    }

    void ensureArchiveLevelAllowed(ArchiveCategoryDto category, ArchiveLevel archiveLevel) {
        if (!ArchiveDynamicTableNames.supportsArchiveLevel(category, archiveLevel)) {
            throw badRequest("该分类未启用案卷管理");
        }
    }

    @Nullable Integer normalizeListWidth(@Nullable Integer listWidth) {
        if (listWidth == null) {
            return null;
        }
        if (listWidth < 80 || listWidth > 600) {
            throw badRequest("列表列宽必须在 80 到 600 之间");
        }
        return listWidth;
    }

    int normalizeColSpan(@Nullable Integer colSpan) {
        if (colSpan == null) {
            return 1;
        }
        if (colSpan < 1 || colSpan > 2) {
            throw badRequest("布局跨列数必须为 1 或 2");
        }
        return colSpan;
    }

    private String toColumnName(String fieldCode) {
        return "f_" + fieldCode;
    }

    private ArchiveFieldControl defaultEditControl(
            ArchiveFieldType fieldType, @Nullable ArchiveFieldControl editControl) {
        if (editControl != null) {
            return editControl;
        }
        return switch (fieldType) {
            case TEXT -> ArchiveFieldControl.INPUT;
            case INTEGER, DECIMAL -> ArchiveFieldControl.NUMBER;
            case DATE -> ArchiveFieldControl.DATE;
            case DATETIME -> ArchiveFieldControl.DATETIME;
        };
    }

    private void validateEditControl(ArchiveFieldType fieldType, ArchiveFieldControl editControl) {
        boolean valid =
                switch (fieldType) {
                    case TEXT ->
                            editControl == ArchiveFieldControl.INPUT
                                    || editControl == ArchiveFieldControl.TEXTAREA;
                    case INTEGER, DECIMAL -> editControl == ArchiveFieldControl.NUMBER;
                    case DATE -> editControl == ArchiveFieldControl.DATE;
                    case DATETIME -> editControl == ArchiveFieldControl.DATETIME;
                };
        if (!valid) {
            throw badRequest("编辑控件与字段类型不匹配");
        }
    }

    private int layoutOrder(@Nullable Integer sortOrder) {
        return sortOrder == null ? 0 : sortOrder;
    }

    private void validateRequired(String value, String message) {
        if (StringUtils.isBlank(value)) {
            throw badRequest(message);
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    record ArchiveFieldValues(
            ArchiveLevel archiveLevel,
            ArchiveFieldScope fieldScope,
            String fieldCode,
            String fieldName,
            ArchiveFieldType fieldType,
            String columnName,
            Integer textLength,
            Integer decimalPrecision,
            Integer decimalScale,
            ArchiveFieldControl editControl,
            boolean listVisible,
            Integer listWidth,
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
            int sortOrder) {}
}
