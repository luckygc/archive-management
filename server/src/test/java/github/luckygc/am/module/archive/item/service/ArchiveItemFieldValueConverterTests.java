package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.metadata.ArchiveFieldControl;
import github.luckygc.am.module.archive.metadata.ArchiveFieldScope;
import github.luckygc.am.module.archive.metadata.ArchiveFieldType;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFieldDto;

@DisplayName("档案字段值转换")
class ArchiveItemFieldValueConverterTests {

    private final ArchiveItemFieldValueConverter converter = new ArchiveItemFieldValueConverter();

    @Test
    @DisplayName("实物字段格式错误返回实物字段路径")
    void physicalFieldErrorShouldUsePhysicalFieldPath() {
        ArchiveFieldDto field = field("box_count", "盒数", ArchiveFieldType.INTEGER, null);

        assertThatThrownBy(
                        () ->
                                converter.convertFields(
                                        List.of(field),
                                        Map.of("box_count", "不是数字"),
                                        "physicalFields"))
                .isInstanceOfSatisfying(
                        BadRequestException.class,
                        exception ->
                                assertThat(exception.fieldViolations())
                                        .extracting(violation -> violation.field())
                                        .containsExactly("physicalFields.box_count"));
    }

    @Test
    @DisplayName("动态字段不存在时保持动态字段路径")
    void unknownDynamicFieldShouldUseDynamicFieldPath() {
        assertThatThrownBy(
                        () ->
                                converter.convertFields(
                                        List.of(), Map.of("unknown", "value"), "dynamicFields"))
                .isInstanceOfSatisfying(
                        BadRequestException.class,
                        exception ->
                                assertThat(exception.fieldViolations())
                                        .extracting(violation -> violation.field())
                                        .containsExactly("dynamicFields.unknown"));
    }

    static ArchiveFieldDto field(
            String fieldCode, String fieldName, ArchiveFieldType fieldType, Integer textLength) {
        return field(fieldCode, fieldName, fieldType, textLength, ArchiveFieldScope.METADATA);
    }

    static ArchiveFieldDto field(
            String fieldCode,
            String fieldName,
            ArchiveFieldType fieldType,
            Integer textLength,
            ArchiveFieldScope fieldScope) {
        return new ArchiveFieldDto(
                1L,
                1L,
                ArchiveLevel.ITEM,
                fieldScope,
                fieldCode,
                fieldName,
                fieldType,
                "f_" + fieldCode,
                textLength,
                null,
                null,
                fieldType == ArchiveFieldType.INTEGER
                        ? ArchiveFieldControl.NUMBER
                        : ArchiveFieldControl.INPUT,
                true,
                null,
                0,
                true,
                1,
                0,
                true,
                1,
                0,
                false,
                false,
                true,
                0,
                null,
                null,
                null);
    }
}
