package github.luckygc.am.module.approval.web;

import jakarta.data.page.PageRequest;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import github.luckygc.am.common.api.CollectionResponse;
import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.approval.ApprovalInstanceStatus;
import github.luckygc.am.module.approval.service.ApprovalWorkflowDefinitionService;
import github.luckygc.am.module.approval.service.ApprovalWorkflowDefinitionService.ApprovalWorkflowDefinitionOption;
import github.luckygc.am.module.approval.service.ApprovalWorkflowDefinitionService.ApprovalWorkflowDefinitionResponse;
import github.luckygc.am.module.approval.service.ApprovalWorkflowDefinitionService.ApprovalWorkflowDefinitionVersionResponse;
import github.luckygc.am.module.approval.service.ApprovalWorkflowDefinitionService.CreateApprovalWorkflowDefinitionRequest;
import github.luckygc.am.module.approval.service.ApprovalWorkflowDefinitionService.UpdateApprovalWorkflowDefinitionRequest;
import github.luckygc.am.module.approval.service.ApprovalWorkflowInstanceService;
import github.luckygc.am.module.approval.service.ApprovalWorkflowInstanceService.ApprovalWorkflowInstanceActionRequest;
import github.luckygc.am.module.approval.service.ApprovalWorkflowInstanceService.ApprovalWorkflowInstanceDetailResponse;
import github.luckygc.am.module.approval.service.ApprovalWorkflowInstanceService.ApprovalWorkflowInstanceResponse;
import github.luckygc.am.module.approval.service.ApprovalWorkflowInstanceService.CompleteApprovalWorkflowTaskRequest;
import github.luckygc.am.module.approval.service.ApprovalWorkflowInstanceService.StartApprovalWorkflowInstanceRequest;

@RestController
public class ApprovalWorkflowController {

    private final ApprovalWorkflowDefinitionService definitionService;
    private final ApprovalWorkflowInstanceService instanceService;

    public ApprovalWorkflowController(
            ApprovalWorkflowDefinitionService definitionService,
            ApprovalWorkflowInstanceService instanceService) {
        this.definitionService = definitionService;
        this.instanceService = instanceService;
    }

    @GetMapping("/api/v1/approval-workflow-definitions")
    public CursorPageResponse<ApprovalWorkflowDefinitionResponse> listDefinitions(
            @RequestParam(required = false) @Nullable Boolean enabled,
            PageRequest page,
            @Nullable Authentication authentication) {
        return definitionService.listDefinitions(enabled, page, userId(authentication));
    }

    @GetMapping("/api/v1/approval-workflow-definition-options")
    public CollectionResponse<ApprovalWorkflowDefinitionOption> listDefinitionOptions(
            @Nullable Authentication authentication) {
        return CollectionResponse.of(definitionService.listEnabledOptions(userId(authentication)));
    }

    @GetMapping("/api/v1/approval-workflow-definitions/{id}")
    public ApprovalWorkflowDefinitionResponse getDefinition(
            @PathVariable Long id, @Nullable Authentication authentication) {
        return definitionService.getDefinition(id, userId(authentication));
    }

    @GetMapping("/api/v1/approval-workflow-definitions/{id}/versions")
    public CursorPageResponse<ApprovalWorkflowDefinitionVersionResponse> listDefinitionVersions(
            @PathVariable Long id, PageRequest page, @Nullable Authentication authentication) {
        return definitionService.listVersions(id, page, userId(authentication));
    }

    @PostMapping("/api/v1/approval-workflow-definitions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApprovalWorkflowDefinitionResponse createDefinition(
            @RequestBody CreateApprovalWorkflowDefinitionRequest request,
            @Nullable Authentication authentication) {
        return definitionService.createDefinition(request, userId(authentication));
    }

    @PatchMapping("/api/v1/approval-workflow-definitions/{id}")
    public ApprovalWorkflowDefinitionResponse updateDefinition(
            @PathVariable Long id,
            @RequestBody UpdateApprovalWorkflowDefinitionRequest request,
            @Nullable Authentication authentication) {
        return definitionService.updateDefinition(id, request, userId(authentication));
    }

    @PostMapping("/api/v1/approval-workflow-definitions/{id}:publish")
    public ApprovalWorkflowDefinitionVersionResponse publishDefinition(
            @PathVariable Long id, @Nullable Authentication authentication) {
        return definitionService.publishDefinition(id, userId(authentication));
    }

    @PostMapping("/api/v1/approval-workflow-definitions/{id}:enable")
    public ApprovalWorkflowDefinitionResponse enableDefinition(
            @PathVariable Long id, @Nullable Authentication authentication) {
        return definitionService.setEnabled(id, true, userId(authentication));
    }

    @PostMapping("/api/v1/approval-workflow-definitions/{id}:disable")
    public ApprovalWorkflowDefinitionResponse disableDefinition(
            @PathVariable Long id, @Nullable Authentication authentication) {
        return definitionService.setEnabled(id, false, userId(authentication));
    }

    @PostMapping("/api/v1/approval-workflow-instances")
    @ResponseStatus(HttpStatus.CREATED)
    public ApprovalWorkflowInstanceResponse startInstance(
            @RequestBody StartApprovalWorkflowInstanceRequest request,
            @Nullable Authentication authentication) {
        return instanceService.startInstance(request, userId(authentication));
    }

    @GetMapping("/api/v1/approval-workflow-instances")
    public CursorPageResponse<ApprovalWorkflowInstanceResponse> listMyStarted(
            @RequestParam(required = false) @Nullable ApprovalInstanceStatus status,
            PageRequest page,
            @Nullable Authentication authentication) {
        return instanceService.listMyStarted(status, page, userId(authentication));
    }

    @GetMapping("/api/v1/approval-workflow-instances/{id}")
    public ApprovalWorkflowInstanceDetailResponse getInstance(
            @PathVariable Long id, @Nullable Authentication authentication) {
        return instanceService.getInstance(id, userId(authentication));
    }

    @PostMapping("/api/v1/approval-workflow-instances/{id}:withdraw")
    public ApprovalWorkflowInstanceDetailResponse withdrawInstance(
            @PathVariable Long id,
            @RequestBody ApprovalWorkflowInstanceActionRequest request,
            @Nullable Authentication authentication) {
        return instanceService.withdrawInstance(id, request, userId(authentication));
    }

    @PostMapping("/api/v1/approval-workflow-instances/{id}:terminate")
    public ApprovalWorkflowInstanceDetailResponse terminateInstance(
            @PathVariable Long id,
            @RequestBody ApprovalWorkflowInstanceActionRequest request,
            @Nullable Authentication authentication) {
        return instanceService.terminateInstance(id, request, userId(authentication));
    }

    @PostMapping("/api/v1/approval-workflow-tasks/{id}:approve")
    public ApprovalWorkflowInstanceDetailResponse approveTask(
            @PathVariable Long id,
            @RequestBody CompleteApprovalWorkflowTaskRequest request,
            @Nullable Authentication authentication) {
        return instanceService.approveTask(id, request, userId(authentication));
    }

    @PostMapping("/api/v1/approval-workflow-tasks/{id}:reject")
    public ApprovalWorkflowInstanceDetailResponse rejectTask(
            @PathVariable Long id,
            @RequestBody CompleteApprovalWorkflowTaskRequest request,
            @Nullable Authentication authentication) {
        return instanceService.rejectTask(id, request, userId(authentication));
    }

    private Long userId(@Nullable Authentication authentication) {
        return AuthenticatedUsers.requireUserId(
                authentication == null ? null : authentication.getPrincipal());
    }
}
