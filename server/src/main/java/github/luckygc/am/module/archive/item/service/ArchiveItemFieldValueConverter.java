package github.luckygc.am.module.archive.item.service;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.item.service.ArchiveItemLineTableService.ArchiveItemLineFieldDto;
import github.luckygc.am.module.archive.metadata.ArchiveFieldType;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFieldDto;

@Service
class ArchiveItemFieldValueConverter {

    Map<String, @Nullable Object> convertFields(
            List<ArchiveFieldDto> fields,
            Map<String, @Nullable Object> fieldValues,
            String fieldPathPrefix) {
        return convertDefinitions(
                fields.stream()
                        .map(
                                field ->
                                        new FieldDefinition(
                                                field.fieldCode(),
                                                field.fieldName(),
                                                field.fieldType(),
                                                field.textLength()))
                        .toList(),
                fieldValues,
                fieldPathPrefix);
    }

    Map<String, @Nullable Object> convertLineFields(
            List<ArchiveItemLineFieldDto> fields,
            Map<String, @Nullable Object> fieldValues,
            String fieldPathPrefix) {
        return convertDefinitions(
                fields.stream()
                        .map(
                                field ->
                                        new FieldDefinition(
                                                field.fieldCode(),
                                                field.fieldName(),
                                                field.fieldType(),
                                                null))
                        .toList(),
                fieldValues,
                fieldPathPrefix);
    }

    private Map<String, @Nullable Object> convertDefinitions(
            List<FieldDefinition> fields,
            Map<String, @Nullable Object> fieldValues,
            String fieldPathPrefix) {
        Map<String, FieldDefinition> fieldsByCode =
                fields.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        FieldDefinition::fieldCode, field -> field));
        for (String fieldCode : fieldValues.keySet()) {
            if (!fieldsByCode.containsKey(fieldCode)) {
                throw badRequest(
                        "字段不存在：" + fieldCode, fieldPath(fieldPathPrefix, fieldCode), "字段不存在");
            }
        }

        Map<String, @Nullable Object> converted = new LinkedHashMap<>();
        for (FieldDefinition field : fields) {
            converted.put(
                    field.fieldCode(),
                    convertValue(field, fieldValues.get(field.fieldCode()), fieldPathPrefix));
        }
        return converted;
    }

    private @Nullable Object convertValue(
            FieldDefinition field, @Nullable Object value, String fieldPathPrefix) {
        if (value instanceof String text) {
            value = StringUtils.trimToNull(text);
        }
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?>
                || value instanceof Iterable<?>
                || value.getClass().isArray()) {
            throw badRequest(
                    field.fieldName() + "格式不合法",
                    fieldPath(fieldPathPrefix, field.fieldCode()),
                    field.fieldName() + "格式不合法");
        }
        try {
            return switch (field.fieldType()) {
                case TEXT -> convertTextValue(field, value, fieldPathPrefix);
                case INTEGER -> new BigDecimal(value.toString()).intValueExact();
                case DECIMAL ->
                        value instanceof BigDecimal decimal
                                ? decimal
                                : new BigDecimal(value.toString());
                case DATE ->
                        value instanceof LocalDate localDate
                                ? Date.valueOf(localDate)
                                : Date.valueOf(value.toString());
                case DATETIME ->
                        value instanceof LocalDateTime localDateTime
                                ? Timestamp.valueOf(localDateTime)
                                : dateTimeValue(value.toString());
            };
        } catch (DateTimeParseException
                | IllegalArgumentException
                | ArithmeticException exception) {
            throw badRequest(
                    field.fieldName() + "格式不合法",
                    fieldPath(fieldPathPrefix, field.fieldCode()),
                    field.fieldName() + "格式不合法");
        }
    }

    private Timestamp dateTimeValue(String value) {
        return value.indexOf(' ') >= 0
                ? Timestamp.valueOf(value)
                : Timestamp.valueOf(LocalDateTime.parse(value));
    }

    private String convertTextValue(FieldDefinition field, Object value, String fieldPathPrefix) {
        String text = StringUtils.trimToNull(value.toString());
        if (text == null) {
            return "";
        }
        if (field.textLength() != null && text.length() > field.textLength()) {
            String message = field.fieldName() + "长度不能超过 " + field.textLength();
            throw badRequest(message, fieldPath(fieldPathPrefix, field.fieldCode()), message);
        }
        return text;
    }

    private String fieldPath(String fieldPathPrefix, String fieldCode) {
        return fieldPathPrefix + "." + fieldCode;
    }

    private BadRequestException badRequest(String message) {
        return new BadRequestException(message);
    }

    private BadRequestException badRequest(String message, String field, String description) {
        return new BadRequestException(message, field, description);
    }

    private record FieldDefinition(
            String fieldCode,
            String fieldName,
            ArchiveFieldType fieldType,
            @Nullable Integer textLength) {}
}
