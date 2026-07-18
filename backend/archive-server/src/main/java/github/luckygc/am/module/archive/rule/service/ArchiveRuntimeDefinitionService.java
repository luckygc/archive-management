package github.luckygc.am.module.archive.rule.service;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.governance.ArchiveGovernanceSchemeVersion;
import github.luckygc.am.module.archive.governance.ArchiveGovernanceSchemeVersionStatus;
import github.luckygc.am.module.archive.governance.repository.ArchiveGovernanceSchemeVersionDataRepository;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeAction;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeActionType;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeDefinition;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeDefinitionKind;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeStatus;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeTriggerPoint;
import github.luckygc.am.module.archive.rule.repository.ArchiveRuntimeActionDataRepository;
import github.luckygc.am.module.archive.rule.repository.ArchiveRuntimeDefinitionDataRepository;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeFieldCatalogService.ArchiveRuntimeFieldCatalog;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ArchiveRuntimeDefinitionService {

    private static final Pattern CODE_PATTERN = Pattern.compile("[a-z][a-z0-9_-]*");

    private final ArchiveRuntimeDefinitionDataRepository definitionRepository;
    private final ArchiveRuntimeActionDataRepository actionRepository;
    private final ArchiveGovernanceSchemeVersionDataRepository schemeVersionRepository;
    private final ArchiveRuntimeFieldCatalogService fieldCatalogService;
    private final Map<ArchiveRuntimeActionType, ArchiveRuntimeActionHandler> actionHandlers;
    private final ArchiveRuntimeConditionValidator conditionValidator =
            new ArchiveRuntimeConditionValidator();
    private final JsonMapper jsonMapper;

    public ArchiveRuntimeDefinitionService(
            ArchiveRuntimeDefinitionDataRepository definitionRepository,
            ArchiveRuntimeActionDataRepository actionRepository,
            ArchiveGovernanceSchemeVersionDataRepository schemeVersionRepository,
            ArchiveRuntimeFieldCatalogService fieldCatalogService,
            List<ArchiveRuntimeActionHandler> handlers,
            JsonMapper jsonMapper) {
        this.definitionRepository = definitionRepository;
        this.actionRepository = actionRepository;
        this.schemeVersionRepository = schemeVersionRepository;
        this.fieldCatalogService = fieldCatalogService;
        this.actionHandlers = indexHandlers(handlers);
        this.jsonMapper = jsonMapper;
    }

    @Transactional(readOnly = true)
    public List<ArchiveRuntimeDefinitionResponse> listDefinitions(
            Long schemeVersionId, @Nullable ArchiveRuntimeStatus status) {
        requireSchemeVersion(schemeVersionId);
        return (status == null
                        ? definitionRepository.findBySchemeVersionId(schemeVersionId)
                        : definitionRepository.findBySchemeVersionIdAndStatus(
                                schemeVersionId, status))
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ArchiveRuntimeDefinitionResponse getDefinition(Long definitionId) {
        return toResponse(loadDefinition(definitionId));
    }

    @Transactional
    public ArchiveRuntimeDefinitionResponse createDefinition(
            SaveArchiveRuntimeDefinitionRequest request, Long userId) {
        ArchiveGovernanceSchemeVersion version = requireEditableVersion(request.schemeVersionId());
        String code = normalizeCode(request.definitionCode());
        if (definitionRepository.findBySchemeVersionIdAndDefinitionCode(version.getId(), code)
                != null) {
            throw new BadRequestException("运行时定义编码已存在", "definitionCode", "同一治理版本内编码必须唯一");
        }
        ArchiveRuntimeDefinition definition = new ArchiveRuntimeDefinition();
        applyFields(definition, request, code);
        definition.setStatus(ArchiveRuntimeStatus.DRAFT);
        validateDraft(definition, request.actions());
        ArchiveRuntimeDefinition saved = definitionRepository.insert(definition);
        insertActions(saved.getId(), request.actions());
        return toResponse(saved);
    }

    @Transactional
    public ArchiveRuntimeDefinitionResponse updateDefinition(
            Long definitionId, SaveArchiveRuntimeDefinitionRequest request, Long userId) {
        ArchiveRuntimeDefinition definition = loadDefinition(definitionId);
        requireDraft(definition);
        requireEditableVersion(definition.getSchemeVersionId());
        if (!Objects.equals(definition.getSchemeVersionId(), request.schemeVersionId())) {
            throw new BadRequestException("不能把运行时定义移动到其他治理版本");
        }
        String code = normalizeCode(request.definitionCode());
        ArchiveRuntimeDefinition duplicate =
                definitionRepository.findBySchemeVersionIdAndDefinitionCode(
                        definition.getSchemeVersionId(), code);
        if (duplicate != null && !Objects.equals(duplicate.getId(), definitionId)) {
            throw new BadRequestException("运行时定义编码已存在", "definitionCode", "同一治理版本内编码必须唯一");
        }
        applyFields(definition, request, code);
        validateDraft(definition, request.actions());
        replaceActions(definitionId, request.actions());
        return toResponse(definitionRepository.update(definition));
    }

    @Transactional
    public ArchiveRuntimeDefinitionResponse publishDefinition(Long definitionId, Long userId) {
        ArchiveRuntimeDefinition definition = loadDefinition(definitionId);
        requireDraft(definition);
        requireEditableVersion(definition.getSchemeVersionId());
        List<ArchiveRuntimeAction> actions =
                actionRepository.findByDefinitionId(definition.getId());
        ArchiveRuntimeFieldCatalog fieldCatalog = validateDraft(definition, toRequests(actions));
        definition.setStatus(ArchiveRuntimeStatus.PUBLISHED);
        definition.setFieldCatalogSignature(fieldCatalog.signature());
        definition.setPublishedBy(userId);
        definition.setPublishedAt(LocalDateTime.now());
        return toResponse(definitionRepository.update(definition));
    }

    @Transactional
    public ArchiveRuntimeDefinitionResponse updateEnabled(
            Long definitionId, boolean enabled, Long userId) {
        ArchiveRuntimeDefinition definition = loadDefinition(definitionId);
        if (definition.getStatus() != ArchiveRuntimeStatus.PUBLISHED) {
            throw new BadRequestException("只有已发布运行时定义可以启停");
        }
        definition.setEnabled(enabled);
        return toResponse(definitionRepository.update(definition));
    }

    @Transactional
    public void deleteDefinition(Long definitionId, Long userId) {
        ArchiveRuntimeDefinition definition = loadDefinition(definitionId);
        requireDraft(definition);
        requireEditableVersion(definition.getSchemeVersionId());
        for (ArchiveRuntimeAction action : actionRepository.findByDefinitionId(definitionId)) {
            actionRepository.update(action);
            actionRepository.delete(action);
        }
        definitionRepository.update(definition);
        definitionRepository.delete(definition);
    }

    @Transactional(readOnly = true)
    public void validateAllForGovernancePublish(Long schemeVersionId) {
        for (ArchiveRuntimeDefinition definition :
                definitionRepository.findBySchemeVersionId(schemeVersionId)) {
            if (definition.getStatus() != ArchiveRuntimeStatus.PUBLISHED) {
                throw new BadRequestException("治理版本包含未发布运行时定义：" + definition.getDefinitionCode());
            }
            validatePublishedDefinition(definition);
        }
    }

    @Transactional(readOnly = true)
    public void validatePortableDefinition(SaveArchiveRuntimeDefinitionRequest request) {
        requireSchemeVersion(request.schemeVersionId());
        ArchiveRuntimeDefinition definition = new ArchiveRuntimeDefinition();
        applyFields(definition, request, normalizeCode(request.definitionCode()));
        definition.setStatus(ArchiveRuntimeStatus.DRAFT);
        validateDraft(definition, request.actions());
    }

    private void validatePublishedDefinition(ArchiveRuntimeDefinition definition) {
        ArchiveRuntimeFieldCatalog catalog =
                fieldCatalogService.catalog(
                        definition.getSchemeVersionId(),
                        definition.getScopeCategoryCode(),
                        definition.getTriggerPoint());
        conditionValidator.validate(
                toConditionNode(definition.getConditionJson()), catalog.fieldsByCode());
        validateActions(
                definition, actionRepository.findByDefinitionId(definition.getId()), catalog);
        if (!Objects.equals(definition.getFieldCatalogSignature(), catalog.signature())) {
            throw new BadRequestException("已发布运行时定义字段目录已失效：" + definition.getDefinitionCode());
        }
    }

    private ArchiveRuntimeFieldCatalog validateDraft(
            ArchiveRuntimeDefinition definition,
            @Nullable List<SaveArchiveRuntimeActionRequest> actionRequests) {
        validateShape(definition, actionRequests);
        ArchiveRuntimeFieldCatalog catalog =
                fieldCatalogService.catalog(
                        definition.getSchemeVersionId(),
                        definition.getScopeCategoryCode(),
                        definition.getTriggerPoint());
        conditionValidator.validate(
                toConditionNode(definition.getConditionJson()), catalog.fieldsByCode());
        validateActions(definition, toActions(definition.getId(), actionRequests), catalog);
        return catalog;
    }

    private void validateShape(
            ArchiveRuntimeDefinition definition,
            @Nullable List<SaveArchiveRuntimeActionRequest> actions) {
        if (definition.getDefinitionKind() == null) {
            throw new BadRequestException("定义类型不能为空", "definitionKind", "定义类型不能为空");
        }
        if (definition.getTriggerPoint() == null) {
            throw new BadRequestException("触发点不能为空", "triggerPoint", "触发点不能为空");
        }
        if (definition.getScopeArchiveLevel() != null
                && definition.getScopeArchiveLevel()
                        != definition.getTriggerPoint().archiveLevel()) {
            throw new BadRequestException("作用档案层级与触发点不兼容");
        }
        if (definition.getDefinitionKind() == ArchiveRuntimeDefinitionKind.CONSTRAINT) {
            if (definition.getConstraintAction() != ArchiveRuntimeActionType.REJECT
                    && definition.getConstraintAction() != ArchiveRuntimeActionType.WARN) {
                throw new BadRequestException("约束失败处理只允许 REJECT 或 WARN");
            }
            if (StringUtils.isBlank(definition.getConstraintMessage())) {
                throw new BadRequestException("约束失败消息不能为空");
            }
            if (actions != null && !actions.isEmpty()) {
                throw new BadRequestException("约束不能包含规则动作");
            }
        } else {
            if (definition.getConstraintAction() != null
                    || definition.getConstraintMessage() != null) {
                throw new BadRequestException("规则不能设置约束失败处理");
            }
            if (actions == null || actions.isEmpty()) {
                throw new BadRequestException("运行时规则至少需要一个固定动作");
            }
        }
    }

    private void validateActions(
            ArchiveRuntimeDefinition definition,
            List<ArchiveRuntimeAction> actions,
            ArchiveRuntimeFieldCatalog catalog) {
        if (definition.getDefinitionKind() == ArchiveRuntimeDefinitionKind.CONSTRAINT) {
            if (!actions.isEmpty()) throw new BadRequestException("约束不能包含规则动作");
            return;
        }
        if (actions.isEmpty()) throw new BadRequestException("运行时规则至少需要一个固定动作");
        for (ArchiveRuntimeAction action : actions) {
            ArchiveRuntimeActionHandler handler = actionHandlers.get(action.getActionType());
            if (handler == null) {
                throw new BadRequestException("系统未注册固定动作：" + action.getActionType());
            }
            handler.validate(action, definition.getTriggerPoint(), catalog);
        }
    }

    private void applyFields(
            ArchiveRuntimeDefinition definition,
            SaveArchiveRuntimeDefinitionRequest request,
            String code) {
        definition.setSchemeVersionId(request.schemeVersionId());
        definition.setDefinitionKind(request.definitionKind());
        definition.setDefinitionCode(code);
        definition.setDefinitionName(requiredText(request.definitionName(), "definitionName"));
        definition.setTriggerPoint(request.triggerPoint());
        definition.setScopeFondsCode(StringUtils.trimToNull(request.scopeFondsCode()));
        definition.setScopeCategoryCode(StringUtils.trimToNull(request.scopeCategoryCode()));
        definition.setScopeArchiveLevel(request.scopeArchiveLevel());
        definition.setPriority(request.priority() == null ? 0 : request.priority());
        definition.setConditionJson(
                request.conditionJson() == null ? Map.of() : request.conditionJson());
        definition.setConstraintAction(request.constraintAction());
        definition.setConstraintMessage(StringUtils.trimToNull(request.constraintMessage()));
        definition.setEnabled(request.enabled() == null || request.enabled());
    }

    private void insertActions(
            Long definitionId, @Nullable List<SaveArchiveRuntimeActionRequest> requests) {
        List<ArchiveRuntimeAction> actions = toActions(definitionId, requests);
        if (!actions.isEmpty()) actionRepository.insertAll(actions);
    }

    private void replaceActions(
            Long definitionId, @Nullable List<SaveArchiveRuntimeActionRequest> requests) {
        for (ArchiveRuntimeAction action : actionRepository.findByDefinitionId(definitionId)) {
            actionRepository.update(action);
            actionRepository.delete(action);
        }
        insertActions(definitionId, requests);
    }

    private List<ArchiveRuntimeAction> toActions(
            @Nullable Long definitionId, @Nullable List<SaveArchiveRuntimeActionRequest> requests) {
        if (requests == null) return List.of();
        return requests.stream()
                .map(
                        request -> {
                            if (request.actionType() == null) {
                                throw new BadRequestException("动作类型不能为空");
                            }
                            ArchiveRuntimeAction action = new ArchiveRuntimeAction();
                            action.setDefinitionId(definitionId == null ? 0L : definitionId);
                            action.setActionType(request.actionType());
                            action.setActionOrder(
                                    request.actionOrder() == null ? 0 : request.actionOrder());
                            action.setActionParams(
                                    request.actionParams() == null
                                            ? Map.of()
                                            : request.actionParams());
                            return action;
                        })
                .toList();
    }

    private List<SaveArchiveRuntimeActionRequest> toRequests(List<ArchiveRuntimeAction> actions) {
        return actions.stream()
                .map(
                        action ->
                                new SaveArchiveRuntimeActionRequest(
                                        action.getActionType(),
                                        action.getActionOrder(),
                                        action.getActionParams()))
                .toList();
    }

    private ArchiveRuntimeDefinitionResponse toResponse(ArchiveRuntimeDefinition definition) {
        return new ArchiveRuntimeDefinitionResponse(
                definition.getId(),
                definition.getSchemeVersionId(),
                definition.getDefinitionKind(),
                definition.getDefinitionCode(),
                definition.getDefinitionName(),
                definition.getTriggerPoint(),
                definition.getScopeFondsCode(),
                definition.getScopeCategoryCode(),
                definition.getScopeArchiveLevel(),
                definition.getPriority(),
                definition.getConditionJson(),
                definition.getConstraintAction(),
                definition.getConstraintMessage(),
                definition.getStatus(),
                definition.isEnabled(),
                definition.getFieldCatalogSignature(),
                actionRepository.findByDefinitionId(definition.getId()).stream()
                        .map(
                                action ->
                                        new ArchiveRuntimeActionResponse(
                                                action.getId(),
                                                action.getActionType(),
                                                action.getActionOrder(),
                                                action.getActionParams()))
                        .toList());
    }

    private ArchiveRuntimeDefinition loadDefinition(Long definitionId) {
        return definitionRepository
                .findById(definitionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "运行时定义不存在"));
    }

    private ArchiveGovernanceSchemeVersion requireSchemeVersion(Long schemeVersionId) {
        return schemeVersionRepository
                .findById(schemeVersionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "治理版本不存在"));
    }

    private ArchiveGovernanceSchemeVersion requireEditableVersion(Long schemeVersionId) {
        ArchiveGovernanceSchemeVersion version = requireSchemeVersion(schemeVersionId);
        if (version.getStatus() != ArchiveGovernanceSchemeVersionStatus.DRAFT) {
            throw new BadRequestException("只有草稿治理版本可以维护运行时定义");
        }
        return version;
    }

    private void requireDraft(ArchiveRuntimeDefinition definition) {
        if (definition.getStatus() != ArchiveRuntimeStatus.DRAFT) {
            throw new BadRequestException("已发布运行时定义不可原地修改");
        }
    }

    private String normalizeCode(@Nullable String value) {
        String code = StringUtils.trimToNull(value);
        if (code == null || !CODE_PATTERN.matcher(code).matches()) {
            throw new BadRequestException(
                    "定义编码必须为小写 snake_case 或 kebab-case", "definitionCode", "定义编码格式不合法");
        }
        return code;
    }

    private String requiredText(@Nullable String value, String field) {
        String text = StringUtils.trimToNull(value);
        if (text == null) {
            throw new BadRequestException("名称不能为空", field, "名称不能为空");
        }
        return text;
    }

    private JsonNode toConditionNode(Map<String, Object> condition) {
        try {
            return jsonMapper.readTree(jsonMapper.writeValueAsString(condition));
        } catch (Exception exception) {
            throw new BadRequestException("运行时条件不是有效 JSON 结构");
        }
    }

    private Map<ArchiveRuntimeActionType, ArchiveRuntimeActionHandler> indexHandlers(
            List<ArchiveRuntimeActionHandler> handlers) {
        Map<ArchiveRuntimeActionType, ArchiveRuntimeActionHandler> indexed =
                new EnumMap<>(ArchiveRuntimeActionType.class);
        for (ArchiveRuntimeActionHandler handler : handlers) {
            if (indexed.put(handler.actionType(), handler) != null) {
                throw new IllegalStateException("固定动作处理器重复：" + handler.actionType());
            }
        }
        return Map.copyOf(indexed);
    }

    public record SaveArchiveRuntimeDefinitionRequest(
            Long schemeVersionId,
            ArchiveRuntimeDefinitionKind definitionKind,
            String definitionCode,
            String definitionName,
            ArchiveRuntimeTriggerPoint triggerPoint,
            @Nullable String scopeFondsCode,
            @Nullable String scopeCategoryCode,
            @Nullable ArchiveLevel scopeArchiveLevel,
            @Nullable Integer priority,
            @Nullable Map<String, Object> conditionJson,
            @Nullable ArchiveRuntimeActionType constraintAction,
            @Nullable String constraintMessage,
            @Nullable Boolean enabled,
            @Nullable List<SaveArchiveRuntimeActionRequest> actions) {}

    public record SaveArchiveRuntimeActionRequest(
            ArchiveRuntimeActionType actionType,
            @Nullable Integer actionOrder,
            @Nullable Map<String, Object> actionParams) {}

    public record ArchiveRuntimeDefinitionResponse(
            Long id,
            Long schemeVersionId,
            ArchiveRuntimeDefinitionKind definitionKind,
            String definitionCode,
            String definitionName,
            ArchiveRuntimeTriggerPoint triggerPoint,
            @Nullable String scopeFondsCode,
            @Nullable String scopeCategoryCode,
            @Nullable ArchiveLevel scopeArchiveLevel,
            int priority,
            Map<String, Object> conditionJson,
            @Nullable ArchiveRuntimeActionType constraintAction,
            @Nullable String constraintMessage,
            ArchiveRuntimeStatus status,
            boolean enabled,
            @Nullable String fieldCatalogSignature,
            List<ArchiveRuntimeActionResponse> actions) {}

    public record ArchiveRuntimeActionResponse(
            Long id,
            ArchiveRuntimeActionType actionType,
            int actionOrder,
            Map<String, Object> actionParams) {}
}
