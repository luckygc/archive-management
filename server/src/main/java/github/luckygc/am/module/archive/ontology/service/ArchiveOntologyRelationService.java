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
import github.luckygc.am.module.archive.ontology.ArchiveOntologyEventType;
import github.luckygc.am.module.archive.ontology.ArchiveOntologyObjectType;
import github.luckygc.am.module.archive.ontology.ArchiveOntologyObjectTypeCode;
import github.luckygc.am.module.archive.ontology.ArchiveOntologyRelationCardinality;
import github.luckygc.am.module.archive.ontology.ArchiveOntologyRelationDirection;
import github.luckygc.am.module.archive.ontology.ArchiveOntologyRelationType;
import github.luckygc.am.module.archive.ontology.repository.ArchiveOntologyEventTypeDataRepository;
import github.luckygc.am.module.archive.ontology.repository.ArchiveOntologyObjectTypeDataRepository;
import github.luckygc.am.module.archive.ontology.repository.ArchiveOntologyRelationTypeDataRepository;
import github.luckygc.am.module.archive.ontology.service.ArchiveOntologyTypes.ArchiveOntologyEventTypeResponse;
import github.luckygc.am.module.archive.ontology.service.ArchiveOntologyTypes.ArchiveOntologyRelationTypeResponse;
import github.luckygc.am.module.archive.ontology.service.ArchiveOntologyTypes.CreateArchiveOntologyEventTypeRequest;
import github.luckygc.am.module.archive.ontology.service.ArchiveOntologyTypes.CreateArchiveOntologyRelationTypeRequest;
import github.luckygc.am.module.archive.ontology.service.ArchiveOntologyTypes.UpdateArchiveOntologyEventTypeRequest;
import github.luckygc.am.module.archive.ontology.service.ArchiveOntologyTypes.UpdateArchiveOntologyRelationTypeRequest;
import github.luckygc.am.module.archive.rule.repository.ArchiveRuleDefinitionDataRepository;

@Service
public class ArchiveOntologyRelationService {

    private static final Pattern CODE_PATTERN = Pattern.compile("[a-zA-Z][a-zA-Z0-9_-]*");
    private static final List<BuiltinEventType> BUILTIN_EVENTS =
            List.of(
                    new BuiltinEventType("CREATED", "创建"),
                    new BuiltinEventType("UPDATED", "更新"),
                    new BuiltinEventType("FILED", "归档"),
                    new BuiltinEventType("TRANSFERRED", "移交"),
                    new BuiltinEventType("ACCESSED", "利用"),
                    new BuiltinEventType("DOWNLOADED", "下载"),
                    new BuiltinEventType("EXPORTED", "导出"),
                    new BuiltinEventType("CHECKED", "检测"),
                    new BuiltinEventType("APPRAISED", "鉴定"),
                    new BuiltinEventType("DISPOSED", "处置"));

    private final ArchiveOntologyObjectTypeDataRepository objectTypeRepository;
    private final ArchiveOntologyRelationTypeDataRepository relationTypeRepository;
    private final ArchiveOntologyEventTypeDataRepository eventTypeRepository;
    private final ArchiveRuleDefinitionDataRepository ruleRepository;
    private final ArchiveGovernanceService governanceService;

    public ArchiveOntologyRelationService(
            ArchiveOntologyObjectTypeDataRepository objectTypeRepository,
            ArchiveOntologyRelationTypeDataRepository relationTypeRepository,
            ArchiveOntologyEventTypeDataRepository eventTypeRepository,
            ArchiveRuleDefinitionDataRepository ruleRepository,
            ArchiveGovernanceService governanceService) {
        this.objectTypeRepository = objectTypeRepository;
        this.relationTypeRepository = relationTypeRepository;
        this.eventTypeRepository = eventTypeRepository;
        this.ruleRepository = ruleRepository;
        this.governanceService = governanceService;
    }

    @Transactional(readOnly = true)
    public List<ArchiveOntologyRelationTypeResponse> listRelationTypes() {
        return relationTypeRepository.list().stream().map(this::toRelationTypeResponse).toList();
    }

    @Transactional
    public ArchiveOntologyRelationTypeResponse createRelationType(
            CreateArchiveOntologyRelationTypeRequest request, Long userId) {
        String code = normalizeCode(request.relationCode(), "relationCode", "关系编码不能为空");
        String name = requiredText(request.relationName(), "relationName", "关系名称不能为空");
        if (relationTypeRepository.findByRelationCode(code) != null) {
            throw new BadRequestException("关系编码已存在", "relationCode", "关系编码已存在");
        }
        requireEnabledObjectType(loadObjectType(request.sourceObjectTypeId()));
        requireEnabledObjectType(loadObjectType(request.targetObjectTypeId()));
        ArchiveOntologyRelationType relation = new ArchiveOntologyRelationType();
        applyRelationFields(relation, request, code, name);
        return toRelationTypeResponse(relationTypeRepository.insert(relation));
    }

    @Transactional
    public ArchiveOntologyRelationTypeResponse updateRelationType(
            Long relationTypeId, UpdateArchiveOntologyRelationTypeRequest request, Long userId) {
        ArchiveOntologyRelationType relation = loadRelationType(relationTypeId);
        protectOntologyReference(relationTypeId);
        String code = normalizeCode(request.relationCode(), "relationCode", "关系编码不能为空");
        String name = requiredText(request.relationName(), "relationName", "关系名称不能为空");
        ArchiveOntologyRelationType existing = relationTypeRepository.findByRelationCode(code);
        if (existing != null && !Objects.equals(existing.getId(), relationTypeId)) {
            throw new BadRequestException("关系编码已存在", "relationCode", "关系编码已存在");
        }
        requireEnabledObjectType(loadObjectType(request.sourceObjectTypeId()));
        requireEnabledObjectType(loadObjectType(request.targetObjectTypeId()));
        applyRelationFields(relation, request, code, name);
        return toRelationTypeResponse(relationTypeRepository.update(relation));
    }

    @Transactional
    public void deleteRelationType(Long relationTypeId, Long userId) {
        ArchiveOntologyRelationType relation = loadRelationType(relationTypeId);
        protectOntologyReference(relationTypeId);
        relationTypeRepository.update(relation);
        relationTypeRepository.delete(relation);
    }

    @Transactional(readOnly = true)
    public List<ArchiveOntologyEventTypeResponse> listEventTypes() {
        return eventTypeRepository.list().stream().map(this::toEventTypeResponse).toList();
    }

    @Transactional
    public List<ArchiveOntologyEventTypeResponse> initializeBuiltInEventTypes(Long userId) {
        ArchiveOntologyObjectType objectType =
                objectTypeRepository.findByTypeCode(
                        ArchiveOntologyObjectTypeCode.ARCHIVE_ITEM.name());
        if (objectType == null) {
            throw new BadRequestException("初始化事件类型前必须先初始化档案条目对象类型");
        }
        List<ArchiveOntologyEventType> missing =
                BUILTIN_EVENTS.stream()
                        .filter(event -> eventTypeRepository.findByEventCode(event.code()) == null)
                        .map(event -> builtInEventType(event, objectType.getId()))
                        .toList();
        return missing.isEmpty()
                ? List.of()
                : eventTypeRepository.insertAll(missing).stream()
                        .map(this::toEventTypeResponse)
                        .toList();
    }

    @Transactional
    public ArchiveOntologyEventTypeResponse createEventType(
            CreateArchiveOntologyEventTypeRequest request, Long userId) {
        String code = normalizeCode(request.eventCode(), "eventCode", "事件编码不能为空");
        String name = requiredText(request.eventName(), "eventName", "事件名称不能为空");
        if (eventTypeRepository.findByEventCode(code) != null) {
            throw new BadRequestException("事件编码已存在", "eventCode", "事件编码已存在");
        }
        requireEnabledObjectType(loadObjectType(request.objectTypeId()));
        ArchiveOntologyEventType eventType = new ArchiveOntologyEventType();
        applyEventFields(eventType, request, code, name);
        return toEventTypeResponse(eventTypeRepository.insert(eventType));
    }

    @Transactional
    public ArchiveOntologyEventTypeResponse updateEventType(
            Long eventTypeId, UpdateArchiveOntologyEventTypeRequest request, Long userId) {
        ArchiveOntologyEventType eventType = loadEventType(eventTypeId);
        protectOntologyReference(eventTypeId);
        String code = normalizeCode(request.eventCode(), "eventCode", "事件编码不能为空");
        String name = requiredText(request.eventName(), "eventName", "事件名称不能为空");
        ArchiveOntologyEventType existing = eventTypeRepository.findByEventCode(code);
        if (existing != null && !Objects.equals(existing.getId(), eventTypeId)) {
            throw new BadRequestException("事件编码已存在", "eventCode", "事件编码已存在");
        }
        requireEnabledObjectType(loadObjectType(request.objectTypeId()));
        applyEventFields(eventType, request, code, name);
        return toEventTypeResponse(eventTypeRepository.update(eventType));
    }

    @Transactional
    public void deleteEventType(Long eventTypeId, Long userId) {
        ArchiveOntologyEventType eventType = loadEventType(eventTypeId);
        if (ruleRepository.countByScopeEventTypeId(eventTypeId) > 0) {
            throw new BadRequestException("事件类型已被规则引用");
        }
        protectOntologyReference(eventTypeId);
        eventTypeRepository.update(eventType);
        eventTypeRepository.delete(eventType);
    }

    private void applyRelationFields(
            ArchiveOntologyRelationType relation,
            ArchiveOntologyTypes.CreateArchiveOntologyRelationTypeRequest request,
            String code,
            String name) {
        relation.setRelationCode(code);
        relation.setRelationName(name);
        relation.setSourceObjectTypeId(request.sourceObjectTypeId());
        relation.setTargetObjectTypeId(request.targetObjectTypeId());
        relation.setRelationDirection(requireRelationDirection(request.relationDirection()));
        relation.setCardinality(
                request.cardinality() == null
                        ? ArchiveOntologyRelationCardinality.MANY_TO_MANY
                        : request.cardinality());
        relation.setDescription(StringUtils.trimToNull(request.description()));
        relation.setEnabled(request.enabled() == null || request.enabled());
    }

    private void applyRelationFields(
            ArchiveOntologyRelationType relation,
            ArchiveOntologyTypes.UpdateArchiveOntologyRelationTypeRequest request,
            String code,
            String name) {
        relation.setRelationCode(code);
        relation.setRelationName(name);
        relation.setSourceObjectTypeId(request.sourceObjectTypeId());
        relation.setTargetObjectTypeId(request.targetObjectTypeId());
        relation.setRelationDirection(requireRelationDirection(request.relationDirection()));
        relation.setCardinality(
                request.cardinality() == null
                        ? ArchiveOntologyRelationCardinality.MANY_TO_MANY
                        : request.cardinality());
        relation.setDescription(StringUtils.trimToNull(request.description()));
        relation.setEnabled(request.enabled() == null || request.enabled());
    }

    private void applyEventFields(
            ArchiveOntologyEventType eventType,
            CreateArchiveOntologyEventTypeRequest request,
            String code,
            String name) {
        eventType.setEventCode(code);
        eventType.setEventName(name);
        eventType.setObjectTypeId(request.objectTypeId());
        eventType.setDescription(StringUtils.trimToNull(request.description()));
        eventType.setEnabled(request.enabled() == null || request.enabled());
    }

    private void applyEventFields(
            ArchiveOntologyEventType eventType,
            UpdateArchiveOntologyEventTypeRequest request,
            String code,
            String name) {
        eventType.setEventCode(code);
        eventType.setEventName(name);
        eventType.setObjectTypeId(request.objectTypeId());
        eventType.setDescription(StringUtils.trimToNull(request.description()));
        eventType.setEnabled(request.enabled() == null || request.enabled());
    }

    private ArchiveOntologyObjectType loadObjectType(Long objectTypeId) {
        return objectTypeRepository.findById(objectTypeId).orElseThrow(() -> notFound("对象类型不存在"));
    }

    private ArchiveOntologyRelationType loadRelationType(Long relationTypeId) {
        return relationTypeRepository
                .findById(relationTypeId)
                .orElseThrow(() -> notFound("关系类型不存在"));
    }

    private ArchiveOntologyEventType loadEventType(Long eventTypeId) {
        return eventTypeRepository.findById(eventTypeId).orElseThrow(() -> notFound("事件类型不存在"));
    }

    private void requireEnabledObjectType(ArchiveOntologyObjectType objectType) {
        if (!objectType.isEnabled()) {
            throw new BadRequestException("对象类型已停用");
        }
    }

    private ArchiveOntologyRelationDirection requireRelationDirection(
            @Nullable ArchiveOntologyRelationDirection direction) {
        if (direction == null) {
            throw new BadRequestException("关系方向不能为空", "relationDirection", "关系方向不能为空");
        }
        return direction;
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

    private void protectOntologyReference(Long targetId) {
        governanceService.requireTargetNotReferenced(
                ArchiveGovernanceBindingType.ONTOLOGY, targetId);
    }

    private ArchiveOntologyRelationTypeResponse toRelationTypeResponse(
            ArchiveOntologyRelationType relation) {
        return new ArchiveOntologyRelationTypeResponse(
                relation.getId(),
                relation.getRelationCode(),
                relation.getRelationName(),
                relation.getSourceObjectTypeId(),
                relation.getTargetObjectTypeId(),
                relation.getRelationDirection(),
                relation.getCardinality(),
                relation.getDescription(),
                relation.isEnabled());
    }

    private ArchiveOntologyEventTypeResponse toEventTypeResponse(
            ArchiveOntologyEventType eventType) {
        return new ArchiveOntologyEventTypeResponse(
                eventType.getId(),
                eventType.getEventCode(),
                eventType.getEventName(),
                eventType.getObjectTypeId(),
                eventType.getDescription(),
                eventType.isEnabled());
    }

    private ArchiveOntologyEventType builtInEventType(BuiltinEventType event, Long objectTypeId) {
        ArchiveOntologyEventType eventType = new ArchiveOntologyEventType();
        eventType.setEventCode(event.code());
        eventType.setEventName(event.name());
        eventType.setObjectTypeId(objectTypeId);
        eventType.setEnabled(true);
        return eventType;
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private record BuiltinEventType(String code, String name) {}
}
