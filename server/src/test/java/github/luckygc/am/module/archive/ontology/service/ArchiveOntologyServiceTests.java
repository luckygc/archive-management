package github.luckygc.am.module.archive.ontology.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.governance.service.ArchiveGovernanceService;
import github.luckygc.am.module.archive.metadata.ArchiveField;
import github.luckygc.am.module.archive.metadata.ArchiveFieldScope;
import github.luckygc.am.module.archive.metadata.ArchiveFieldType;
import github.luckygc.am.module.archive.metadata.repository.ArchiveFieldDataRepository;
import github.luckygc.am.module.archive.ontology.ArchiveOntologyAttributeDataType;
import github.luckygc.am.module.archive.ontology.ArchiveOntologyAttributeMapping;
import github.luckygc.am.module.archive.ontology.ArchiveOntologyAttributeMappingKind;
import github.luckygc.am.module.archive.ontology.ArchiveOntologyAttributeType;
import github.luckygc.am.module.archive.ontology.ArchiveOntologyCardinality;
import github.luckygc.am.module.archive.ontology.ArchiveOntologyMetadataDomain;
import github.luckygc.am.module.archive.ontology.ArchiveOntologyObjectType;
import github.luckygc.am.module.archive.ontology.repository.ArchiveOntologyAttributeMappingDataRepository;
import github.luckygc.am.module.archive.ontology.repository.ArchiveOntologyAttributeTypeDataRepository;
import github.luckygc.am.module.archive.ontology.repository.ArchiveOntologyEventTypeDataRepository;
import github.luckygc.am.module.archive.ontology.repository.ArchiveOntologyObjectTypeDataRepository;
import github.luckygc.am.module.archive.ontology.repository.ArchiveOntologyRelationTypeDataRepository;
import github.luckygc.am.module.archive.rule.repository.ArchiveRuleDefinitionDataRepository;

@DisplayName("档案本体服务")
class ArchiveOntologyServiceTests {

    private ArchiveOntologyObjectTypeDataRepository objectTypeRepository;
    private ArchiveOntologyAttributeTypeDataRepository attributeTypeRepository;
    private ArchiveOntologyAttributeMappingDataRepository mappingRepository;
    private ArchiveOntologyRelationTypeDataRepository relationTypeRepository;
    private ArchiveOntologyEventTypeDataRepository eventTypeRepository;
    private ArchiveRuleDefinitionDataRepository ruleRepository;
    private ArchiveFieldDataRepository fieldRepository;
    private ArchiveGovernanceService governanceService;
    private ArchiveOntologyService service;

    @BeforeEach
    void setUp() {
        objectTypeRepository = mock(ArchiveOntologyObjectTypeDataRepository.class);
        attributeTypeRepository = mock(ArchiveOntologyAttributeTypeDataRepository.class);
        mappingRepository = mock(ArchiveOntologyAttributeMappingDataRepository.class);
        relationTypeRepository = mock(ArchiveOntologyRelationTypeDataRepository.class);
        eventTypeRepository = mock(ArchiveOntologyEventTypeDataRepository.class);
        ruleRepository = mock(ArchiveRuleDefinitionDataRepository.class);
        fieldRepository = mock(ArchiveFieldDataRepository.class);
        governanceService = mock(ArchiveGovernanceService.class);
        service =
                new ArchiveOntologyService(
                        objectTypeRepository,
                        attributeTypeRepository,
                        mappingRepository,
                        relationTypeRepository,
                        eventTypeRepository,
                        ruleRepository,
                        fieldRepository,
                        governanceService);
    }

    @Test
    @DisplayName("创建对象类型时允许本地自定义编码")
    void createObjectTypeShouldAllowLocalCustomCode() {
        when(objectTypeRepository.findByTypeCode("case_file")).thenReturn(null);
        when(objectTypeRepository.insert(any(ArchiveOntologyObjectType.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 7L));

        ArchiveOntologyService.ArchiveOntologyObjectTypeResponse response =
                service.createObjectType(
                        new ArchiveOntologyService.CreateArchiveOntologyObjectTypeRequest(
                                "case_file", "案件文件", "本地案件对象", true),
                        6L);

        assertThat(response.typeCode()).isEqualTo("case_file");
        ArgumentCaptor<ArchiveOntologyObjectType> captor =
                ArgumentCaptor.forClass(ArchiveOntologyObjectType.class);
        verify(objectTypeRepository).insert(captor.capture());
        assertThat(captor.getValue().getTypeCode()).isEqualTo("case_file");
    }

    @Test
    @DisplayName("属性类型必须绑定启用对象类型")
    void createAttributeTypeShouldRejectDisabledObjectType() {
        ArchiveOntologyObjectType objectType = objectType(3L, "ARCHIVE_ITEM", false);
        when(objectTypeRepository.findById(3L)).thenReturn(Optional.of(objectType));

        assertThatThrownBy(
                        () ->
                                service.createAttributeType(
                                        new ArchiveOntologyService
                                                .CreateArchiveOntologyAttributeTypeRequest(
                                                "document_no",
                                                "文号",
                                                3L,
                                                ArchiveOntologyAttributeDataType.TEXT,
                                                ArchiveOntologyMetadataDomain.DESCRIPTION,
                                                ArchiveOntologyCardinality.SINGLE,
                                                true,
                                                false,
                                                true,
                                                true,
                                                true,
                                                null,
                                                true),
                                        6L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("对象类型已停用");
    }

    @Test
    @DisplayName("创建动态字段映射时校验对象层级和字段类型")
    void createDynamicMappingShouldValidateDynamicFieldCompatibility() {
        ArchiveOntologyObjectType objectType = objectType(1L, "ARCHIVE_ITEM", true);
        ArchiveOntologyAttributeType attribute =
                attribute(2L, 1L, ArchiveOntologyAttributeDataType.DATE);
        ArchiveField field = new ArchiveField();
        field.setId(8L);
        field.setCategoryId(5L);
        field.setArchiveLevel(ArchiveLevel.ITEM);
        field.setFieldScope(ArchiveFieldScope.METADATA);
        field.setFieldType(ArchiveFieldType.TEXT);
        field.setFieldCode("document_no");
        when(attributeTypeRepository.findById(2L)).thenReturn(Optional.of(attribute));
        when(objectTypeRepository.findById(1L)).thenReturn(Optional.of(objectType));
        when(fieldRepository.findById(8L)).thenReturn(Optional.of(field));

        assertThatThrownBy(
                        () ->
                                service.createAttributeMapping(
                                        new ArchiveOntologyService
                                                .CreateArchiveOntologyAttributeMappingRequest(
                                                2L,
                                                ArchiveOntologyAttributeMappingKind.DYNAMIC_FIELD,
                                                null,
                                                5L,
                                                ArchiveLevel.ITEM,
                                                ArchiveFieldScope.METADATA,
                                                8L,
                                                null,
                                                null,
                                                null,
                                                null),
                                        6L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("属性数据类型与动态字段类型不兼容");
    }

    @Test
    @DisplayName("按属性类型列出属性映射")
    void listAttributeMappingsShouldReturnResponses() {
        ArchiveOntologyAttributeMapping mapping = new ArchiveOntologyAttributeMapping();
        mapping.setId(9L);
        mapping.setAttributeTypeId(2L);
        mapping.setMappingKind(ArchiveOntologyAttributeMappingKind.FIXED_FIELD);
        mapping.setFixedFieldCode("archive_no");
        when(mappingRepository.findByAttributeTypeId(2L)).thenReturn(List.of(mapping));

        List<ArchiveOntologyService.ArchiveOntologyAttributeMappingResponse> mappings =
                service.listAttributeMappings(2L);

        assertThat(mappings)
                .singleElement()
                .satisfies(
                        response -> {
                            assertThat(response.id()).isEqualTo(9L);
                            assertThat(response.attributeTypeId()).isEqualTo(2L);
                            assertThat(response.fixedFieldCode()).isEqualTo("archive_no");
                        });
    }

    private ArchiveOntologyObjectType withId(ArchiveOntologyObjectType objectType, Long id) {
        objectType.setId(id);
        return objectType;
    }

    private ArchiveOntologyObjectType objectType(Long id, String typeCode, boolean enabled) {
        ArchiveOntologyObjectType objectType = new ArchiveOntologyObjectType();
        objectType.setId(id);
        objectType.setTypeCode(typeCode);
        objectType.setTypeName(typeCode);
        objectType.setEnabled(enabled);
        return objectType;
    }

    private ArchiveOntologyAttributeType attribute(
            Long id, Long objectTypeId, ArchiveOntologyAttributeDataType dataType) {
        ArchiveOntologyAttributeType attribute = new ArchiveOntologyAttributeType();
        attribute.setId(id);
        attribute.setAttributeCode("document_no");
        attribute.setAttributeName("文号");
        attribute.setObjectTypeId(objectTypeId);
        attribute.setDataType(dataType);
        attribute.setMetadataDomain(ArchiveOntologyMetadataDomain.DESCRIPTION);
        attribute.setCardinality(ArchiveOntologyCardinality.SINGLE);
        attribute.setEnabled(true);
        return attribute;
    }
}
