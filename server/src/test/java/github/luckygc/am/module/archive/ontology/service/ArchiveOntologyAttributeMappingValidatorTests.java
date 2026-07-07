package github.luckygc.am.module.archive.ontology.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.metadata.ArchiveField;
import github.luckygc.am.module.archive.metadata.ArchiveFieldScope;
import github.luckygc.am.module.archive.metadata.ArchiveFieldType;
import github.luckygc.am.module.archive.ontology.ArchiveOntologyAttributeDataType;
import github.luckygc.am.module.archive.ontology.ArchiveOntologyObjectTypeCode;

@DisplayName("档案本体属性映射校验")
class ArchiveOntologyAttributeMappingValidatorTests {

    @Test
    @DisplayName("拒绝在本体映射中保存档案实例值")
    void validateMappingRequestShouldRejectInstanceValue() {
        assertThatThrownBy(
                        () ->
                                ArchiveOntologyAttributeMappingValidator.rejectInstanceValue(
                                        "档案题名实例值"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("本体只定义语义和映射，不保存档案实例值");
    }

    @Test
    @DisplayName("拒绝将日期属性映射到文本动态字段")
    void validateDynamicFieldMappingShouldRejectIncompatibleDataType() {
        ArchiveField field = new ArchiveField();
        field.setId(11L);
        field.setArchiveLevel(ArchiveLevel.ITEM);
        field.setFieldScope(ArchiveFieldScope.METADATA);
        field.setFieldType(ArchiveFieldType.TEXT);
        field.setFieldCode("document_no");

        assertThatThrownBy(
                        () ->
                                ArchiveOntologyAttributeMappingValidator.validateDynamicFieldMapping(
                                        ArchiveOntologyObjectTypeCode.ARCHIVE_ITEM,
                                        ArchiveOntologyAttributeDataType.DATE,
                                        field))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("属性数据类型与动态字段类型不兼容");
    }
}
