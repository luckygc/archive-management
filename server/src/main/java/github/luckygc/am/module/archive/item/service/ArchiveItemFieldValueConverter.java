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
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFieldDto;

@Service
class ArchiveItemFieldValueConverter {

    Map<String, @Nullable Object> convertFields(
            List<ArchiveFieldDto> fields,
            Map<String, @Nullable Object> fieldValues,
            String fieldPathPrefix) {
        Map<String, ArchiveFieldDto> fieldsByCode =
                fields.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        ArchiveFieldDto::fieldCode, field -> field));
        for (String fieldCode : fieldValues.keySet()) {
            if (!fieldsByCode.containsKey(fieldCode)) {
                throw badRequest(
                        "字段不存在：" + fieldCode, fieldPath(fieldPathPrefix, fieldCode), "字段不存在");
            }
        }

        Map<String, @Nullable Object> converted = new LinkedHashMap<>();
        for (ArchiveFieldDto field : fields) {
            converted.put(
                    field.fieldCode(),
                    convertValue(field, fieldValues.get(field.fieldCode()), fieldPathPrefix));
        }
        return converted;
    }

    private @Nullable Object convertValue(
            ArchiveFieldDto field, @Nullable Object value, String fieldPathPrefix) {
        if (value instanceof String text) {
            value = StringUtils.trimToNull(text);
        }
        if (value == null) {
            return null;
        }
        try {
            return switch (field.fieldType()) {
                case TEXT -> convertTextValue(field, value, fieldPathPrefix);
                case INTEGER ->
                        value instanceof Number number
                                ? number.intValue()
                                : Integer.parseInt(value.toString());
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
                                : Timestamp.valueOf(LocalDateTime.parse(value.toString()));
            };
        } catch (DateTimeParseException | IllegalArgumentException exception) {
            throw badRequest(
                    field.fieldName() + "格式不合法",
                    fieldPath(fieldPathPrefix, field.fieldCode()),
                    field.fieldName() + "格式不合法");
        }
    }

    private String convertTextValue(ArchiveFieldDto field, Object value, String fieldPathPrefix) {
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
}
