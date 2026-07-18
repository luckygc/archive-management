package github.luckygc.am.module.archive.rule.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.metadata.ArchiveFieldDataType;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeFieldCatalogService.ArchiveRuntimeField;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeFieldCatalogService.ArchiveRuntimeFieldCatalog;

public final class ArchiveRuntimeActionExecutionContext {

    private final String definitionCode;
    private final ArchiveRuntimeFieldCatalog fieldCatalog;
    private final Map<String, @Nullable Object> candidateFacts;
    private final Map<String, FieldAssignment> assignments;

    public ArchiveRuntimeActionExecutionContext(
            String definitionCode,
            ArchiveRuntimeFieldCatalog fieldCatalog,
            Map<String, @Nullable Object> candidateFacts,
            Map<String, FieldAssignment> assignments) {
        this.definitionCode = definitionCode;
        this.fieldCatalog = fieldCatalog;
        this.candidateFacts = candidateFacts;
        this.assignments = assignments;
    }

    public void setField(String fieldCode, @Nullable Object configuredValue) {
        ArchiveRuntimeField field = fieldCatalog.fieldsByCode().get(fieldCode);
        if (field == null || !field.writable()) {
            throw new BadRequestException(
                    "运行时规则目标字段不存在或不可写：" + fieldCode, "actions.params.field", "目标字段不存在或不可写");
        }
        @Nullable Object value = convert(configuredValue, field.dataType(), fieldCode);
        FieldAssignment previous = assignments.get(fieldCode);
        if (previous != null && !Objects.equals(previous.value(), value)) {
            throw new BadRequestException(
                    "运行时规则字段赋值冲突："
                            + fieldCode
                            + "（"
                            + previous.definitionCode()
                            + " / "
                            + definitionCode
                            + "）");
        }
        assignments.putIfAbsent(fieldCode, new FieldAssignment(definitionCode, value));
        candidateFacts.put(fieldCode, value);
    }

    public Map<String, @Nullable Object> candidateFacts() {
        return candidateFacts;
    }

    public Map<String, FieldAssignment> assignments() {
        return Map.copyOf(assignments);
    }

    private @Nullable Object convert(
            @Nullable Object value, ArchiveFieldDataType dataType, String fieldCode) {
        if (value == null) return null;
        if (value instanceof Map<?, ?>
                || value instanceof Iterable<?>
                || value.getClass().isArray()) {
            throw invalidValue(fieldCode);
        }
        try {
            return switch (dataType) {
                case TEXT, ENUM, ORGANIZATION, PERSON -> String.valueOf(value);
                case INTEGER -> new BigDecimal(value.toString()).longValueExact();
                case DECIMAL, AMOUNT -> new BigDecimal(value.toString());
                case DATE ->
                        value instanceof LocalDate date ? date : LocalDate.parse(value.toString());
                case DATETIME ->
                        value instanceof LocalDateTime dateTime
                                ? dateTime
                                : LocalDateTime.parse(value.toString());
                case BOOLEAN -> convertBoolean(value, fieldCode);
                case REFERENCE ->
                        value instanceof Number number ? number.longValue() : String.valueOf(value);
            };
        } catch (ArithmeticException | NumberFormatException | DateTimeParseException exception) {
            throw invalidValue(fieldCode);
        }
    }

    private Boolean convertBoolean(Object value, String fieldCode) {
        if (value instanceof Boolean bool) return bool;
        if ("true".equalsIgnoreCase(value.toString())) return true;
        if ("false".equalsIgnoreCase(value.toString())) return false;
        throw invalidValue(fieldCode);
    }

    private BadRequestException invalidValue(String fieldCode) {
        return new BadRequestException(
                "运行时规则字段值类型不兼容：" + fieldCode, "actions.params.value", "字段值类型不兼容");
    }

    public static Map<String, @Nullable Object> mutableFacts(Map<String, @Nullable Object> source) {
        return new LinkedHashMap<>(source);
    }

    public record FieldAssignment(String definitionCode, @Nullable Object value) {}
}
