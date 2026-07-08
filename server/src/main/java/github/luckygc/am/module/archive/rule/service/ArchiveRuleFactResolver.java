package github.luckygc.am.module.archive.rule.service;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

import github.luckygc.am.module.archive.ontology.ArchiveOntologyAttributeDataType;
import github.luckygc.am.module.archive.ontology.ArchiveOntologyAttributeType;
import github.luckygc.am.module.archive.ontology.repository.ArchiveOntologyAttributeTypeDataRepository;

public class ArchiveRuleFactResolver
        implements ArchiveRuleConditionValidator.ArchiveRuleFieldResolver {

    private static final Map<String, ArchiveOntologyAttributeDataType> FIXED_FIELDS =
            Map.ofEntries(
                    Map.entry("fixed.archiveNo", ArchiveOntologyAttributeDataType.TEXT),
                    Map.entry("fixed.archiveYear", ArchiveOntologyAttributeDataType.INTEGER),
                    Map.entry("fixed.fondsCode", ArchiveOntologyAttributeDataType.TEXT),
                    Map.entry("fixed.categoryCode", ArchiveOntologyAttributeDataType.TEXT),
                    Map.entry("fixed.securityLevelId", ArchiveOntologyAttributeDataType.INTEGER),
                    Map.entry("fixed.retentionPeriodId", ArchiveOntologyAttributeDataType.INTEGER),
                    Map.entry("line.rowCount", ArchiveOntologyAttributeDataType.INTEGER),
                    Map.entry("component.fileCount", ArchiveOntologyAttributeDataType.INTEGER),
                    Map.entry("relation.count", ArchiveOntologyAttributeDataType.INTEGER),
                    Map.entry("event.code", ArchiveOntologyAttributeDataType.TEXT),
                    Map.entry("context.userId", ArchiveOntologyAttributeDataType.INTEGER),
                    Map.entry("context.now", ArchiveOntologyAttributeDataType.DATETIME),
                    Map.entry("context.operation", ArchiveOntologyAttributeDataType.TEXT));

    private final ArchiveOntologyAttributeTypeDataRepository attributeTypeRepository;

    public ArchiveRuleFactResolver(
            ArchiveOntologyAttributeTypeDataRepository attributeTypeRepository) {
        this.attributeTypeRepository = attributeTypeRepository;
    }

    @Override
    public @Nullable ArchiveOntologyAttributeDataType resolve(String field) {
        String normalizedField = StringUtils.trimToNull(field);
        if (normalizedField == null) {
            return null;
        }
        ArchiveOntologyAttributeDataType fixedType = FIXED_FIELDS.get(normalizedField);
        if (fixedType != null) {
            return fixedType;
        }
        String attributeCode = attributeCode(normalizedField);
        if (attributeCode == null) {
            return null;
        }
        ArchiveOntologyAttributeType attribute =
                attributeTypeRepository.findByAttributeCode(attributeCode);
        if (attribute == null || !attribute.isEnabled() || !attribute.isRuleFactVisible()) {
            return null;
        }
        return attribute.getDataType();
    }

    private @Nullable String attributeCode(String field) {
        if (field.startsWith("attribute.")) {
            return StringUtils.trimToNull(field.substring("attribute.".length()));
        }
        if (field.startsWith("dynamic.")) {
            return StringUtils.trimToNull(field.substring("dynamic.".length()));
        }
        return null;
    }
}
