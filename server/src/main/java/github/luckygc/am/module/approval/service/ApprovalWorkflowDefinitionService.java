package github.luckygc.am.module.approval.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.data.page.PageRequest;
import jakarta.data.restrict.Restrict;
import jakarta.data.restrict.Restriction;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonInclude;

import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.api.JsonMergePatch;
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.approval.ApprovalNodeType;
import github.luckygc.am.module.approval.ApprovalWorkflowDefinition;
import github.luckygc.am.module.approval.ApprovalWorkflowDefinitionVersion;
import github.luckygc.am.module.approval._ApprovalWorkflowDefinition;
import github.luckygc.am.module.approval.port.ApprovalProcessEngine;
import github.luckygc.am.module.approval.repository.ApprovalWorkflowDefinitionDataRepository;
import github.luckygc.am.module.approval.repository.ApprovalWorkflowDefinitionVersionDataRepository;
import github.luckygc.am.module.approval.service.ApprovalWorkflowTypes.ApprovalWorkflowGraph;
import github.luckygc.am.module.authentication.service.AuthenticationUserManagementService;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ApprovalWorkflowDefinitionService {

    private static final Pattern CODE_PATTERN = Pattern.compile("[a-z][a-z0-9_-]{0,99}");
    private static final TypeReference<ApprovalWorkflowGraph> GRAPH_TYPE = new TypeReference<>() {};
    private static final Set<String> PATCH_FIELDS =
            Set.of("definitionName", "businessType", "graph");
    private static final Set<String> READ_ONLY_FIELDS =
            Set.of(
                    "id",
                    "definitionCode",
                    "enabled",
                    "draftRevision",
                    "publishedVersionId",
                    "createdAt",
                    "updatedAt");
    private static final Set<String> GRAPH_FIELDS = Set.of("nodes", "edges");
    private static final Set<String> NODE_FIELDS =
            Set.of(
                    "nodeCode",
                    "nodeName",
                    "nodeType",
                    "x",
                    "y",
                    "candidateStrategy",
                    "candidateUserIds",
                    "allowedActions");
    private static final Set<String> EDGE_FIELDS =
            Set.of("edgeCode", "sourceNodeCode", "targetNodeCode", "defaultFlow", "condition");
    private static final Set<String> CONDITION_FIELDS = Set.of("field", "operator", "values");

    private final ApprovalWorkflowDefinitionDataRepository definitionRepository;
    private final ApprovalWorkflowDefinitionVersionDataRepository versionRepository;
    private final ApprovalProcessEngine processEngine;
    private final ApprovalBpmnXmlGenerator bpmnXmlGenerator;
    private final ApprovalWorkflowGraphValidator graphValidator;
    private final AuthorizationPermissionService permissionService;
    private final AuthenticationUserManagementService userManagementService;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public ApprovalWorkflowDefinitionService(
            ApprovalWorkflowDefinitionDataRepository definitionRepository,
            ApprovalWorkflowDefinitionVersionDataRepository versionRepository,
            ApprovalProcessEngine processEngine,
            ApprovalBpmnXmlGenerator bpmnXmlGenerator,
            ApprovalWorkflowGraphValidator graphValidator,
            AuthorizationPermissionService permissionService,
            AuthenticationUserManagementService userManagementService,
            JsonMapper jsonMapper,
            Clock clock) {
        this.definitionRepository = definitionRepository;
        this.versionRepository = versionRepository;
        this.processEngine = processEngine;
        this.bpmnXmlGenerator = bpmnXmlGenerator;
        this.graphValidator = graphValidator;
        this.permissionService = permissionService;
        this.userManagementService = userManagementService;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<ApprovalWorkflowDefinitionResponse> listDefinitions(
            @Nullable Boolean enabled, PageRequest pageRequest, Long userId) {
        requireManage(userId);
        Restriction<ApprovalWorkflowDefinition> restriction = Restrict.unrestricted();
        if (enabled != null) {
            restriction = _ApprovalWorkflowDefinition.enabled.equalTo(enabled);
        }
        return CursorPageResponse.from(
                definitionRepository.filterBy(restriction, pageRequest),
                pageRequest,
                this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<ApprovalWorkflowDefinitionOption> listEnabledOptions(Long userId) {
        requireStart(userId);
        return definitionRepository.list(true).stream()
                .filter(definition -> definition.getPublishedVersionId() != null)
                .map(
                        definition ->
                                new ApprovalWorkflowDefinitionOption(
                                        definition.getId(),
                                        definition.getDefinitionCode(),
                                        definition.getDefinitionName(),
                                        definition.getBusinessType()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ApprovalWorkflowDefinitionResponse getDefinition(Long id, Long userId) {
        requireManage(userId);
        return toResponse(loadDefinition(id));
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<ApprovalWorkflowDefinitionVersionResponse> listVersions(
            Long definitionId, PageRequest pageRequest, Long userId) {
        requireManage(userId);
        loadDefinition(definitionId);
        return CursorPageResponse.from(
                versionRepository.pageByDefinitionId(definitionId, pageRequest),
                pageRequest,
                this::toVersionResponse);
    }

    @Transactional
    public ApprovalWorkflowDefinitionResponse createDefinition(
            CreateApprovalWorkflowDefinitionRequest request, Long userId) {
        requireManage(userId);
        String code = requiredCode(request.definitionCode(), "definitionCode");
        if (definitionRepository.findByDefinitionCode(code) != null) {
            throw new BadRequestException("审批流定义编码已存在", "definitionCode", "审批流定义编码已存在");
        }
        ApprovalWorkflowGraph graph = graphValidator.validateDraft(request.graph());
        ApprovalWorkflowDefinition definition = new ApprovalWorkflowDefinition();
        definition.setDefinitionCode(code);
        definition.setDefinitionName(requiredText(request.definitionName(), "definitionName"));
        definition.setBusinessType(requiredCode(request.businessType(), "businessType"));
        definition.setGraphJson(jsonMapper.writeValueAsString(graph));
        return toResponse(definitionRepository.insert(definition));
    }

    @Transactional
    public ApprovalWorkflowDefinitionResponse updateDefinition(
            Long id, JsonNode patch, Long userId) {
        requireManage(userId);
        ApprovalWorkflowDefinition definition = loadDefinition(id);
        requireObject(patch, PATCH_FIELDS, "", true);
        for (String field : patch.propertyNames()) {
            if (READ_ONLY_FIELDS.contains(field)) {
                throw new BadRequestException("不支持修改字段 " + field, field, "字段不可修改");
            }
        }
        String name =
                patch.has("definitionName")
                        ? requiredText(textField(patch, "definitionName"), "definitionName")
                        : definition.getDefinitionName();
        String businessType =
                patch.has("businessType")
                        ? requiredCode(textField(patch, "businessType"), "businessType")
                        : definition.getBusinessType();
        ApprovalWorkflowGraph currentGraph = readGraph(definition.getGraphJson());
        ApprovalWorkflowGraph graph = currentGraph;
        if (patch.has("graph")) {
            JsonNode graphPatch = patch.get("graph");
            if (graphPatch.isNull()) {
                throw new BadRequestException("graph 不能删除", "graph", "流程图不能为空");
            }
            JsonNode mergedGraph =
                    JsonMergePatch.apply(
                            jsonMapper.readTree(definition.getGraphJson()), graphPatch);
            validateGraphShape(mergedGraph);
            try {
                graph =
                        graphValidator.validateDraft(
                                jsonMapper.readValue(mergedGraph.toString(), GRAPH_TYPE));
            } catch (JacksonException exception) {
                throw new BadRequestException("graph 不合法", "graph", "流程图格式不合法");
            }
        }
        if (name.equals(definition.getDefinitionName())
                && businessType.equals(definition.getBusinessType())
                && graph.equals(currentGraph)) {
            return toResponse(definition);
        }
        definition.setDefinitionName(name);
        definition.setBusinessType(businessType);
        definition.setGraphJson(jsonMapper.writeValueAsString(graph));
        definition.setDraftRevision(definition.getDraftRevision() + 1);
        return toResponse(definitionRepository.update(definition));
    }

    private String textField(JsonNode patch, String field) {
        JsonNode value = patch.get(field);
        if (value.isNull() || !value.isTextual()) {
            throw new BadRequestException(field + " 不合法", field, field + " 不合法");
        }
        return value.asText();
    }

    private void validateGraphShape(JsonNode graph) {
        requireObject(graph, GRAPH_FIELDS, "graph", false);
        for (String field : GRAPH_FIELDS) {
            if (!graph.has(field) || !graph.get(field).isArray()) {
                throw new BadRequestException("graph." + field + " 不合法", "graph." + field, "必须为数组");
            }
        }
        for (JsonNode node : graph.get("nodes")) {
            requireObject(node, NODE_FIELDS, "graph.nodes", false);
        }
        for (JsonNode edge : graph.get("edges")) {
            requireObject(edge, EDGE_FIELDS, "graph.edges", false);
            JsonNode condition = edge.get("condition");
            if (condition != null && !condition.isNull()) {
                requireObject(condition, CONDITION_FIELDS, "graph.edges.condition", false);
            }
        }
    }

    private void requireObject(
            JsonNode node, Set<String> fields, String path, boolean ignoreUnknownNull) {
        if (node == null || !node.isObject()) {
            throw new BadRequestException(path + " 补丁必须是对象");
        }
        for (String field : node.propertyNames()) {
            if (!fields.contains(field) && (!ignoreUnknownNull || !node.get(field).isNull())) {
                throw new BadRequestException("不支持修改字段 " + field, field, "字段不可修改");
            }
        }
    }

    @Transactional
    public ApprovalWorkflowDefinitionResponse setEnabled(Long id, boolean enabled, Long userId) {
        requireManage(userId);
        ApprovalWorkflowDefinition definition = loadDefinition(id);
        definition.setEnabled(enabled);
        return toResponse(definitionRepository.update(definition));
    }

    @Transactional
    public ApprovalWorkflowDefinitionVersionResponse publishDefinition(Long id, Long userId) {
        requireManage(userId);
        ApprovalWorkflowDefinition definition = loadDefinition(id);
        ApprovalWorkflowGraph graph =
                graphValidator.validateForPublishing(readGraph(definition.getGraphJson()));
        userManagementService.requireEnabledUsers(
                graph.nodes().stream()
                        .filter(node -> node.nodeType() == ApprovalNodeType.APPROVAL)
                        .flatMap(node -> node.candidateUserIds().stream())
                        .distinct()
                        .toList());
        int versionNumber =
                versionRepository.findByDefinitionId(id).stream()
                                .mapToInt(ApprovalWorkflowDefinitionVersion::getVersionNumber)
                                .max()
                                .orElse(0)
                        + 1;
        String processKey = "approval_" + id;
        ApprovalProcessEngine.Deployment deployment =
                processEngine.deploy(
                        processKey,
                        definition.getDefinitionName(),
                        bpmnXmlGenerator.generate(
                                processKey, definition.getDefinitionName(), graph));
        LocalDateTime now = LocalDateTime.now(clock);
        ApprovalWorkflowDefinitionVersion version = new ApprovalWorkflowDefinitionVersion();
        version.setDefinitionId(id);
        version.setVersionNumber(versionNumber);
        version.setGraphJson(jsonMapper.writeValueAsString(graph));
        version.setFlowableDeploymentId(deployment.deploymentId());
        version.setFlowableProcessDefinitionId(deployment.processDefinitionId());
        version.setFlowableProcessDefinitionKey(deployment.processDefinitionKey());
        version.setPublishedBy(userId);
        version.setPublishedAt(now);
        version = versionRepository.insert(version);
        definition.setPublishedVersionId(version.getId());
        definitionRepository.update(definition);
        return toVersionResponse(version);
    }

    ApprovalWorkflowDefinition loadDefinitionForStart(Long id) {
        return loadDefinition(id);
    }

    ApprovalWorkflowDefinitionVersion loadPublishedVersion(ApprovalWorkflowDefinition definition) {
        if (!definition.isEnabled()) {
            throw new BadRequestException("审批流定义已停用");
        }
        Long versionId = definition.getPublishedVersionId();
        if (versionId == null) {
            throw new BadRequestException("审批流定义尚未发布");
        }
        return versionRepository
                .findById(versionId)
                .orElseThrow(() -> new BadRequestException("审批流定义版本不存在"));
    }

    ApprovalWorkflowGraph readGraph(String graphJson) {
        return jsonMapper.readValue(graphJson, GRAPH_TYPE);
    }

    private ApprovalWorkflowDefinition loadDefinition(Long id) {
        return definitionRepository
                .findById(id)
                .orElseThrow(() -> new BadRequestException("审批流定义不存在", "id", "审批流定义不存在"));
    }

    private ApprovalWorkflowDefinitionResponse toResponse(ApprovalWorkflowDefinition definition) {
        return new ApprovalWorkflowDefinitionResponse(
                definition.getId(),
                definition.getDefinitionCode(),
                definition.getDefinitionName(),
                definition.getBusinessType(),
                definition.isEnabled(),
                definition.getDraftRevision(),
                definition.getPublishedVersionId(),
                readGraph(definition.getGraphJson()),
                definition.getCreatedAt(),
                definition.getUpdatedAt());
    }

    private ApprovalWorkflowDefinitionVersionResponse toVersionResponse(
            ApprovalWorkflowDefinitionVersion version) {
        return new ApprovalWorkflowDefinitionVersionResponse(
                version.getId(),
                version.getDefinitionId(),
                version.getVersionNumber(),
                readGraph(version.getGraphJson()),
                version.getPublishedBy(),
                version.getPublishedAt());
    }

    private String requiredCode(String value, String field) {
        String normalized = requiredText(value, field).toLowerCase();
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new BadRequestException("编码格式不正确", field, "编码必须以小写字母开头，且只能包含小写字母、数字、下划线或连字符");
        }
        return normalized;
    }

    private String requiredText(String value, String field) {
        if (StringUtils.isBlank(value)) {
            throw new BadRequestException("必填字段不能为空", field, "必填字段不能为空");
        }
        return value.trim();
    }

    private void requireManage(Long userId) {
        permissionService.requirePermission(
                userId, AuthorizationPermissionCode.APPROVAL_DEFINITION_MANAGE);
    }

    private void requireStart(Long userId) {
        permissionService.requirePermission(
                userId, AuthorizationPermissionCode.APPROVAL_INSTANCE_START);
    }

    public record CreateApprovalWorkflowDefinitionRequest(
            String definitionCode,
            String definitionName,
            String businessType,
            ApprovalWorkflowGraph graph) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApprovalWorkflowDefinitionResponse(
            Long id,
            String definitionCode,
            String definitionName,
            String businessType,
            boolean enabled,
            int draftRevision,
            @Nullable Long publishedVersionId,
            ApprovalWorkflowGraph graph,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    public record ApprovalWorkflowDefinitionVersionResponse(
            Long id,
            Long definitionId,
            int versionNumber,
            ApprovalWorkflowGraph graph,
            Long publishedBy,
            LocalDateTime publishedAt) {}

    public record ApprovalWorkflowDefinitionOption(
            Long id, String definitionCode, String definitionName, String businessType) {}
}
