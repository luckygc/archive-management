package github.luckygc.am.module.approval.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.data.page.PageRequest;
import jakarta.data.restrict.Restrict;
import jakarta.data.restrict.Restriction;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.api.CursorPageResponse;
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

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ApprovalWorkflowDefinitionService {

    private static final Pattern CODE_PATTERN = Pattern.compile("[a-z][a-z0-9_-]{0,99}");
    private static final TypeReference<ApprovalWorkflowGraph> GRAPH_TYPE = new TypeReference<>() {};

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
            Long id, UpdateApprovalWorkflowDefinitionRequest request, Long userId) {
        requireManage(userId);
        ApprovalWorkflowDefinition definition = loadDefinition(id);
        if (request.definitionName() != null) {
            definition.setDefinitionName(requiredText(request.definitionName(), "definitionName"));
        }
        if (request.businessType() != null) {
            definition.setBusinessType(requiredCode(request.businessType(), "businessType"));
        }
        if (request.graph() != null) {
            definition.setGraphJson(
                    jsonMapper.writeValueAsString(graphValidator.validateDraft(request.graph())));
        }
        definition.setDraftRevision(definition.getDraftRevision() + 1);
        return toResponse(definitionRepository.update(definition));
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

    public record UpdateApprovalWorkflowDefinitionRequest(
            @Nullable String definitionName,
            @Nullable String businessType,
            @Nullable ApprovalWorkflowGraph graph) {}

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
