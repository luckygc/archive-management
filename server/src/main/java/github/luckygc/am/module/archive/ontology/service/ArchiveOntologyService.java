package github.luckygc.am.module.archive.ontology.service;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.governance.ArchiveGovernanceBindingType;
import github.luckygc.am.module.archive.governance.service.ArchiveGovernanceService;
import github.luckygc.am.module.archive.metadata.ArchiveField;
import github.luckygc.am.module.archive.metadata.repository.ArchiveFieldDataRepository;
import github.luckygc.am.module.archive.ontology.ArchiveOntologyAttributeMapping;
import github.luckygc.am.module.archive.ontology.ArchiveOntologyAttributeMappingKind;
import github.luckygc.am.module.archive.ontology.ArchiveOntologyAttributeType;
import github.luckygc.am.module.archive.ontology.ArchiveOntologyCardinality;
import github.luckygc.am.module.archive.ontology.ArchiveOntologyObjectType;
import github.luckygc.am.module.archive.ontology.ArchiveOntologyObjectTypeCode;
import github.luckygc.am.module.archive.ontology.repository.ArchiveOntologyAttributeMappingDataRepository;
import github.luckygc.am.module.archive.ontology.repository.ArchiveOntologyAttributeTypeDataRepository;
import github.luckygc.am.module.archive.ontology.repository.ArchiveOntologyEventTypeDataRepository;
import github.luckygc.am.module.archive.ontology.repository.ArchiveOntologyObjectTypeDataRepository;
import github.luckygc.am.module.archive.ontology.repository.ArchiveOntologyRelationTypeDataRepository;
import github.luckygc.am.module.archive.rule.repository.ArchiveRuleDefinitionDataRepository;

@Service
public class ArchiveOntologyService extends ArchiveOntologyTypes {

    private static final Pattern CODE_PATTERN = Pattern.compile("[a-zA-Z][a-zA-Z0-9_-]*");

    private final ArchiveOntologyObjectTypeDataRepository objectTypeRepository;
    private final ArchiveOntologyAttributeTypeDataRepository attributeTypeRepository;
    private final ArchiveOntologyAttributeMappingDataRepository mappingRepository;
    private final ArchiveOntologyRelationTypeDataRepository relationTypeRepository;
    private final ArchiveOntologyEventTypeDataRepository eventTypeRepository;
    private final ArchiveRuleDefinitionDataRepository ruleRepository;
    private final ArchiveFieldDataRepository fieldRepository;
    private final ArchiveGovernanceService governanceService;
    private final ArchiveOntologyRelationService relationService;

    public ArchiveOntologyService(
            ArchiveOntologyObjectTypeDataRepository objectTypeRepository,
            ArchiveOntologyAttributeTypeDataRepository attributeTypeRepository,
            ArchiveOntologyAttributeMappingDataRepository mappingRepository,
            ArchiveOntologyRelationTypeDataRepository relationTypeRepository,
            ArchiveOntologyEventTypeDataRepository eventTypeRepository,
            ArchiveRuleDefinitionDataRepository ruleRepository,
            ArchiveFieldDataRepository fieldRepository,
            ArchiveGovernanceService governanceService,
            ArchiveOntologyRelationService relationService) {
        this.objectTypeRepository = objectTypeRepository;
        this.attributeTypeRepository = attributeTypeRepository;
        this.mappingRepository = mappingRepository;
        this.relationTypeRepository = relationTypeRepository;
        this.eventTypeRepository = eventTypeRepository;
        this.ruleRepository = ruleRepository;
        this.fieldRepository = fieldRepository;
        this.governanceService = governanceService;
        this.relationService = relationService;
    }

    @Transactional(readOnly = true)
    public List<ArchiveOntologyObjectTypeResponse> listObjectTypes(@Nullable Boolean enabled) {
        return (enabled == null ? objectTypeRepository.list() : objectTypeRepository.list(enabled))
                .stream().map(this::toObjectTypeResponse).toList();
    }

    @Transactional
    public List<ArchiveOntologyObjectTypeResponse> initializeBuiltInObjectTypes(Long userId) {
        List<ArchiveOntologyObjectType> missing =
                java.util.Arrays.stream(ArchiveOntologyObjectTypeCode.values())
                        .filter(code -> objectTypeRepository.findByTypeCode(code.name()) == null)
                        .map(this::builtInObjectType)
                        .toList();
        return missing.isEmpty()
                ? List.of()
                : objectTypeRepository.insertAll(missing).stream()
                        .map(this::toObjectTypeResponse)
                        .toList();
    }

    @Transactional
    public ArchiveOntologyObjectTypeResponse createObjectType(
            CreateArchiveOntologyObjectTypeRequest request, Long userId) {
        String code = normalizeCode(request.typeCode(), "typeCode", "对象类型编码不能为空");
        String name = requiredText(request.typeName(), "typeName", "对象类型名称不能为空");
        if (objectTypeRepository.findByTypeCode(code) != null) {
            throw new BadRequestException("对象类型编码已存在", "typeCode", "对象类型编码已存在");
        }
        ArchiveOntologyObjectType objectType = new ArchiveOntologyObjectType();
        objectType.setTypeCode(code);
        objectType.setTypeName(name);
        objectType.setDescription(StringUtils.trimToNull(request.description()));
        objectType.setBuiltinFlag(false);
        objectType.setEnabled(request.enabled() == null || request.enabled());
        return toObjectTypeResponse(objectTypeRepository.insert(objectType));
    }

    @Transactional
    public ArchiveOntologyObjectTypeResponse updateObjectType(
            Long objectTypeId, UpdateArchiveOntologyObjectTypeRequest request, Long userId) {
        ArchiveOntologyObjectType objectType = loadObjectType(objectTypeId);
        protectOntologyReference(objectTypeId);
        String code = normalizeCode(request.typeCode(), "typeCode", "对象类型编码不能为空");
        String name = requiredText(request.typeName(), "typeName", "对象类型名称不能为空");
        ArchiveOntologyObjectType existing = objectTypeRepository.findByTypeCode(code);
        if (existing != null && !Objects.equals(existing.getId(), objectTypeId)) {
            throw new BadRequestException("对象类型编码已存在", "typeCode", "对象类型编码已存在");
        }
        objectType.setTypeCode(code);
        objectType.setTypeName(name);
        objectType.setDescription(StringUtils.trimToNull(request.description()));
        objectType.setEnabled(request.enabled() == null || request.enabled());
        return toObjectTypeResponse(objectTypeRepository.update(objectType));
    }

    @Transactional
    public void deleteObjectType(Long objectTypeId, Long userId) {
        ArchiveOntologyObjectType objectType = loadObjectType(objectTypeId);
        if (objectType.isBuiltinFlag()) {
            throw new BadRequestException("内置对象类型不能删除");
        }
        if (attributeTypeRepository.countByObjectTypeId(objectTypeId) > 0
                || relationTypeRepository.countByObjectTypeId(objectTypeId) > 0
                || eventTypeRepository.countByObjectTypeId(objectTypeId) > 0
                || ruleRepository.countByScopeObjectTypeId(objectTypeId) > 0) {
            throw new BadRequestException("对象类型已被引用");
        }
        protectOntologyReference(objectTypeId);
        objectTypeRepository.update(objectType);
        objectTypeRepository.delete(objectType);
    }

    @Transactional(readOnly = true)
    public List<ArchiveOntologyAttributeTypeResponse> listAttributeTypes(
            @Nullable Long objectTypeId) {
        return (objectTypeId == null
                        ? attributeTypeRepository.list()
                        : attributeTypeRepository.findByObjectTypeId(objectTypeId))
                .stream().map(this::toAttributeTypeResponse).toList();
    }

    @Transactional
    public ArchiveOntologyAttributeTypeResponse createAttributeType(
            CreateArchiveOntologyAttributeTypeRequest request, Long userId) {
        String code = normalizeCode(request.attributeCode(), "attributeCode", "属性编码不能为空");
        String name = requiredText(request.attributeName(), "attributeName", "属性名称不能为空");
        if (attributeTypeRepository.findByAttributeCode(code) != null) {
            throw new BadRequestException("属性编码已存在", "attributeCode", "属性编码已存在");
        }
        ArchiveOntologyObjectType objectType = loadObjectType(request.objectTypeId());
        requireEnabledObjectType(objectType);
        ArchiveOntologyAttributeType attribute = new ArchiveOntologyAttributeType();
        applyAttributeFields(attribute, request, code, name);
        return toAttributeTypeResponse(attributeTypeRepository.insert(attribute));
    }

    @Transactional
    public ArchiveOntologyAttributeTypeResponse updateAttributeType(
            Long attributeTypeId, UpdateArchiveOntologyAttributeTypeRequest request, Long userId) {
        ArchiveOntologyAttributeType attribute = loadAttributeType(attributeTypeId);
        protectOntologyReference(attributeTypeId);
        String code = normalizeCode(request.attributeCode(), "attributeCode", "属性编码不能为空");
        String name = requiredText(request.attributeName(), "attributeName", "属性名称不能为空");
        ArchiveOntologyAttributeType existing = attributeTypeRepository.findByAttributeCode(code);
        if (existing != null && !Objects.equals(existing.getId(), attributeTypeId)) {
            throw new BadRequestException("属性编码已存在", "attributeCode", "属性编码已存在");
        }
        requireEnabledObjectType(loadObjectType(request.objectTypeId()));
        applyAttributeFields(attribute, request, code, name);
        return toAttributeTypeResponse(attributeTypeRepository.update(attribute));
    }

    @Transactional(readOnly = true)
    public List<ArchiveOntologyAttributeMappingResponse> listAttributeMappings(
            @Nullable Long attributeTypeId) {
        return (attributeTypeId == null
                        ? mappingRepository.list()
                        : mappingRepository.findByAttributeTypeId(attributeTypeId))
                .stream().map(this::toAttributeMappingResponse).toList();
    }

    @Transactional
    public void deleteAttributeType(Long attributeTypeId, Long userId) {
        ArchiveOntologyAttributeType attribute = loadAttributeType(attributeTypeId);
        if (mappingRepository.countByAttributeTypeId(attributeTypeId) > 0) {
            throw new BadRequestException("属性类型已被映射引用");
        }
        protectOntologyReference(attributeTypeId);
        attributeTypeRepository.update(attribute);
        attributeTypeRepository.delete(attribute);
    }

    @Transactional
    public ArchiveOntologyAttributeMappingResponse createAttributeMapping(
            CreateArchiveOntologyAttributeMappingRequest request, Long userId) {
        ArchiveOntologyAttributeType attribute = loadAttributeType(request.attributeTypeId());
        ArchiveOntologyObjectType objectType = loadObjectType(attribute.getObjectTypeId());
        ArchiveOntologyAttributeMapping mapping = new ArchiveOntologyAttributeMapping();
        mapping.setAttributeTypeId(attribute.getId());
        mapping.setMappingKind(requireMappingKind(request.mappingKind()));
        mapping.setFixedFieldCode(StringUtils.trimToNull(request.fixedFieldCode()));
        mapping.setCategoryId(request.categoryId());
        mapping.setArchiveLevel(request.archiveLevel());
        mapping.setFieldScope(request.fieldScope());
        mapping.setDynamicFieldId(request.dynamicFieldId());
        mapping.setLineTableId(request.lineTableId());
        mapping.setLineFieldId(request.lineFieldId());
        mapping.setComponentFieldCode(StringUtils.trimToNull(request.componentFieldCode()));
        mapping.setProcessFieldCode(StringUtils.trimToNull(request.processFieldCode()));
        validateMapping(mapping, attribute, objectType);
        return toAttributeMappingResponse(mappingRepository.insert(mapping));
    }

    @Transactional
    public void deleteAttributeMapping(Long mappingId, Long userId) {
        ArchiveOntologyAttributeMapping mapping =
                mappingRepository.findById(mappingId).orElseThrow(() -> notFound("属性映射不存在"));
        mappingRepository.update(mapping);
        mappingRepository.delete(mapping);
    }

    @Transactional(readOnly = true)
    public List<ArchiveOntologyRelationTypeResponse> listRelationTypes() {
        return relationService.listRelationTypes();
    }

    @Transactional
    public ArchiveOntologyRelationTypeResponse createRelationType(
            CreateArchiveOntologyRelationTypeRequest request, Long userId) {
        return relationService.createRelationType(request, userId);
    }

    @Transactional
    public ArchiveOntologyRelationTypeResponse updateRelationType(
            Long relationTypeId, UpdateArchiveOntologyRelationTypeRequest request, Long userId) {
        return relationService.updateRelationType(relationTypeId, request, userId);
    }

    @Transactional
    public void deleteRelationType(Long relationTypeId, Long userId) {
        relationService.deleteRelationType(relationTypeId, userId);
    }

    @Transactional(readOnly = true)
    public List<ArchiveOntologyEventTypeResponse> listEventTypes() {
        return relationService.listEventTypes();
    }

    @Transactional
    public List<ArchiveOntologyEventTypeResponse> initializeBuiltInEventTypes(Long userId) {
        return relationService.initializeBuiltInEventTypes(userId);
    }

    @Transactional
    public ArchiveOntologyEventTypeResponse createEventType(
            CreateArchiveOntologyEventTypeRequest request, Long userId) {
        return relationService.createEventType(request, userId);
    }

    @Transactional
    public ArchiveOntologyEventTypeResponse updateEventType(
            Long eventTypeId, UpdateArchiveOntologyEventTypeRequest request, Long userId) {
        return relationService.updateEventType(eventTypeId, request, userId);
    }

    @Transactional
    public void deleteEventType(Long eventTypeId, Long userId) {
        relationService.deleteEventType(eventTypeId, userId);
    }

    private ArchiveOntologyObjectType builtInObjectType(ArchiveOntologyObjectTypeCode code) {
        ArchiveOntologyObjectType objectType = new ArchiveOntologyObjectType();
        objectType.setTypeCode(code.name());
        objectType.setTypeName(code.name());
        objectType.setBuiltinFlag(true);
        objectType.setEnabled(true);
        return objectType;
    }

    private void applyAttributeFields(
            ArchiveOntologyAttributeType attribute,
            AttributeTypeRequest request,
            String code,
            String name) {
        if (request.dataType() == null) {
            throw new BadRequestException("属性数据类型不能为空", "dataType", "属性数据类型不能为空");
        }
        if (request.metadataDomain() == null) {
            throw new BadRequestException("元数据域不能为空", "metadataDomain", "元数据域不能为空");
        }
        attribute.setAttributeCode(code);
        attribute.setAttributeName(name);
        attribute.setObjectTypeId(request.objectTypeId());
        attribute.setDataType(request.dataType());
        attribute.setMetadataDomain(request.metadataDomain());
        attribute.setCardinality(
                request.cardinality() == null
                        ? ArchiveOntologyCardinality.SINGLE
                        : request.cardinality());
        attribute.setExactSearchable(Boolean.TRUE.equals(request.exactSearchable()));
        attribute.setSortable(Boolean.TRUE.equals(request.sortable()));
        attribute.setDescriptionParticipating(
                Boolean.TRUE.equals(request.descriptionParticipating()));
        attribute.setReferenceCodeParticipating(
                Boolean.TRUE.equals(request.referenceCodeParticipating()));
        attribute.setRuleFactVisible(
                request.ruleFactVisible() == null || request.ruleFactVisible());
        attribute.setDescription(StringUtils.trimToNull(request.description()));
        attribute.setEnabled(request.enabled() == null || request.enabled());
    }

    private void validateMapping(
            ArchiveOntologyAttributeMapping mapping,
            ArchiveOntologyAttributeType attribute,
            ArchiveOntologyObjectType objectType) {
        switch (mapping.getMappingKind()) {
            case FIXED_FIELD -> {
                if (StringUtils.trimToNull(mapping.getFixedFieldCode()) == null) {
                    throw new BadRequestException("固定字段映射必须配置固定字段编码");
                }
            }
            case DYNAMIC_FIELD -> validateDynamicMapping(mapping, attribute, objectType);
            case LINE_FIELD -> {
                if (mapping.getLineTableId() == null || mapping.getLineFieldId() == null) {
                    throw new BadRequestException("明细字段映射必须配置明细表和字段");
                }
            }
            case FILE_COMPONENT_FIELD -> {
                if (StringUtils.trimToNull(mapping.getComponentFieldCode()) == null) {
                    throw new BadRequestException("文件组件字段映射必须配置组件字段编码");
                }
            }
            case PROCESS_FIELD -> {
                if (StringUtils.trimToNull(mapping.getProcessFieldCode()) == null) {
                    throw new BadRequestException("过程字段映射必须配置过程字段编码");
                }
            }
        }
    }

    private void validateDynamicMapping(
            ArchiveOntologyAttributeMapping mapping,
            ArchiveOntologyAttributeType attribute,
            ArchiveOntologyObjectType objectType) {
        if (mapping.getCategoryId() == null
                || mapping.getArchiveLevel() == null
                || mapping.getFieldScope() == null
                || mapping.getDynamicFieldId() == null) {
            throw new BadRequestException("动态字段映射必须配置分类、层级、字段域和字段");
        }
        ArchiveField field =
                fieldRepository
                        .findById(mapping.getDynamicFieldId())
                        .orElseThrow(() -> notFound("动态字段不存在"));
        if (!Objects.equals(field.getCategoryId(), mapping.getCategoryId())
                || field.getArchiveLevel() != mapping.getArchiveLevel()
                || field.getFieldScope() != mapping.getFieldScope()
                || !field.isEnabled()) {
            throw new BadRequestException("动态字段不存在或不可用");
        }
        ArchiveOntologyAttributeMappingValidator.validateDynamicFieldMapping(
                objectType.getTypeCode(), attribute.getDataType(), field);
    }

    private ArchiveOntologyObjectType loadObjectType(Long objectTypeId) {
        return objectTypeRepository.findById(objectTypeId).orElseThrow(() -> notFound("对象类型不存在"));
    }

    private ArchiveOntologyAttributeType loadAttributeType(Long attributeTypeId) {
        return attributeTypeRepository
                .findById(attributeTypeId)
                .orElseThrow(() -> notFound("属性类型不存在"));
    }

    private void requireEnabledObjectType(ArchiveOntologyObjectType objectType) {
        if (!objectType.isEnabled()) {
            throw new BadRequestException("对象类型已停用");
        }
    }

    private void protectOntologyReference(Long targetId) {
        governanceService.requireTargetNotReferenced(
                ArchiveGovernanceBindingType.ONTOLOGY, targetId);
    }

    private ArchiveOntologyAttributeMappingKind requireMappingKind(
            @Nullable ArchiveOntologyAttributeMappingKind mappingKind) {
        if (mappingKind == null) {
            throw new BadRequestException("属性映射类型不能为空", "mappingKind", "属性映射类型不能为空");
        }
        return mappingKind;
    }

    private String normalizeCode(@Nullable String value, String field, String message) {
        String code = StringUtils.trimToNull(value);
        if (code == null) {
            throw new BadRequestException(message, field, message);
        }
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new BadRequestException("编码格式不合法", field, "编码格式不合法");
        }
        return code;
    }

    private String requiredText(@Nullable String value, String field, String message) {
        String text = StringUtils.trimToNull(value);
        if (text == null) {
            throw new BadRequestException(message, field, message);
        }
        return text;
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ArchiveOntologyObjectTypeResponse toObjectTypeResponse(
            ArchiveOntologyObjectType objectType) {
        return new ArchiveOntologyObjectTypeResponse(
                objectType.getId(),
                objectType.getTypeCode(),
                objectType.getTypeName(),
                objectType.getDescription(),
                objectType.isBuiltinFlag(),
                objectType.isEnabled());
    }

    private ArchiveOntologyAttributeTypeResponse toAttributeTypeResponse(
            ArchiveOntologyAttributeType attribute) {
        return new ArchiveOntologyAttributeTypeResponse(
                attribute.getId(),
                attribute.getAttributeCode(),
                attribute.getAttributeName(),
                attribute.getObjectTypeId(),
                attribute.getDataType(),
                attribute.getMetadataDomain(),
                attribute.getCardinality(),
                attribute.isExactSearchable(),
                attribute.isSortable(),
                attribute.isDescriptionParticipating(),
                attribute.isReferenceCodeParticipating(),
                attribute.isRuleFactVisible(),
                attribute.getDescription(),
                attribute.isEnabled());
    }

    private ArchiveOntologyAttributeMappingResponse toAttributeMappingResponse(
            ArchiveOntologyAttributeMapping mapping) {
        return new ArchiveOntologyAttributeMappingResponse(
                mapping.getId(),
                mapping.getAttributeTypeId(),
                mapping.getMappingKind(),
                mapping.getFixedFieldCode(),
                mapping.getCategoryId(),
                mapping.getArchiveLevel(),
                mapping.getFieldScope(),
                mapping.getDynamicFieldId(),
                mapping.getLineTableId(),
                mapping.getLineFieldId(),
                mapping.getComponentFieldCode(),
                mapping.getProcessFieldCode());
    }
}
