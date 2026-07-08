package github.luckygc.am.module.archive.ontology.service;

import org.apache.commons.lang3.StringUtils;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.metadata.ArchiveField;
import github.luckygc.am.module.archive.metadata.ArchiveFieldType;
import github.luckygc.am.module.archive.ontology.ArchiveOntologyAttributeDataType;
import github.luckygc.am.module.archive.ontology.ArchiveOntologyObjectTypeCode;

public final class ArchiveOntologyAttributeMappingValidator {

    private ArchiveOntologyAttributeMappingValidator() {}

    public static void rejectInstanceValue(String value) {
        if (StringUtils.trimToNull(value) != null) {
            throw new BadRequestException("本体只定义语义和映射，不保存档案实例值");
        }
    }

    public static void validateDynamicFieldMapping(
            ArchiveOntologyObjectTypeCode objectType,
            ArchiveOntologyAttributeDataType attributeDataType,
            ArchiveField field) {
        validateDynamicFieldMapping(objectType.name(), attributeDataType, field);
    }

    public static void validateDynamicFieldMapping(
            String objectTypeCode,
            ArchiveOntologyAttributeDataType attributeDataType,
            ArchiveField field) {
        if (!isArchiveObjectTypeCompatible(objectTypeCode, field.getArchiveLevel())) {
            throw new BadRequestException("属性适用对象与动态字段层级不兼容");
        }
        if (!isDataTypeCompatible(attributeDataType, field.getFieldType())) {
            throw new BadRequestException("属性数据类型与动态字段类型不兼容");
        }
    }

    private static boolean isArchiveObjectTypeCompatible(
            String objectTypeCode, ArchiveLevel archiveLevel) {
        if (ArchiveOntologyObjectTypeCode.ARCHIVE_ITEM.name().equals(objectTypeCode)) {
            return archiveLevel == ArchiveLevel.ITEM;
        }
        if (ArchiveOntologyObjectTypeCode.ARCHIVE_VOLUME.name().equals(objectTypeCode)) {
            return archiveLevel == ArchiveLevel.VOLUME;
        }
        return true;
    }

    private static boolean isDataTypeCompatible(
            ArchiveOntologyAttributeDataType attributeDataType, ArchiveFieldType fieldType) {
        return switch (attributeDataType) {
            case TEXT, ENUM, REFERENCE, ORGANIZATION, PERSON -> fieldType == ArchiveFieldType.TEXT;
            case INTEGER -> fieldType == ArchiveFieldType.INTEGER;
            case DECIMAL, AMOUNT -> fieldType == ArchiveFieldType.DECIMAL;
            case DATE -> fieldType == ArchiveFieldType.DATE;
            case DATETIME -> fieldType == ArchiveFieldType.DATETIME;
            case BOOLEAN -> false;
        };
    }
}
