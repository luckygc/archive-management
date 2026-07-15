package github.luckygc.am.module.archive.rule.web;

import java.util.Map;

import jakarta.data.page.PageRequest;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import github.luckygc.am.common.api.CollectionResponse;
import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.rule.ArchiveRuleDecision;
import github.luckygc.am.module.archive.rule.ArchiveRuleStatus;
import github.luckygc.am.module.archive.rule.service.ArchiveLocalRuleService;
import github.luckygc.am.module.archive.rule.service.ArchiveLocalRuleService.ArchiveRuleResponse;
import github.luckygc.am.module.archive.rule.service.ArchiveLocalRuleService.CreateArchiveRuleRequest;
import github.luckygc.am.module.archive.rule.service.ArchiveLocalRuleService.ExecuteArchiveRulesRequest;
import github.luckygc.am.module.archive.rule.service.ArchiveLocalRuleService.SearchArchiveRuleTracesRequest;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@RestController
public class ArchiveRuleController {

    private final ArchiveLocalRuleService ruleService;
    private final AuthorizationPermissionService permissionService;

    public ArchiveRuleController(
            ArchiveLocalRuleService ruleService, AuthorizationPermissionService permissionService) {
        this.ruleService = ruleService;
        this.permissionService = permissionService;
    }

    @GetMapping("/api/v1/archive-rules")
    public CollectionResponse<ArchiveRuleResponse> listRules(
            @RequestParam Long schemeVersionId,
            @RequestParam(required = false) ArchiveRuleStatus status) {
        return CollectionResponse.of(ruleService.listRules(schemeVersionId, status));
    }

    @PostMapping("/api/v1/archive-rules")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveRuleResponse createRule(
            @RequestBody CreateArchiveRuleRequest request, Authentication authentication) {
        return ruleService.createRule(request, requireManage(authentication));
    }

    @PostMapping("/api/v1/archive-rules/{ruleId}:publish")
    public ArchiveRuleResponse publishRule(
            @PathVariable Long ruleId, Authentication authentication) {
        return ruleService.publishRule(ruleId, requireManage(authentication));
    }

    @PostMapping("/api/v1/archive-rules/{ruleId}:enable")
    public ArchiveRuleResponse enableRule(
            @PathVariable Long ruleId, Authentication authentication) {
        return ruleService.updateRuleEnabled(ruleId, true, requireManage(authentication));
    }

    @PostMapping("/api/v1/archive-rules/{ruleId}:disable")
    public ArchiveRuleResponse disableRule(
            @PathVariable Long ruleId, Authentication authentication) {
        return ruleService.updateRuleEnabled(ruleId, false, requireManage(authentication));
    }

    @DeleteMapping("/api/v1/archive-rules/{ruleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRule(@PathVariable Long ruleId, Authentication authentication) {
        ruleService.deleteRule(ruleId, requireManage(authentication));
    }

    @PostMapping("/api/v1/archive-rules:execute")
    public CollectionResponse<ArchiveRuleDecision> executeRules(
            @RequestBody ExecuteArchiveRulesRequest request, Authentication authentication) {
        Long userId = requireManage(authentication);
        return CollectionResponse.of(ruleService.executeRules(withUserId(request, userId)));
    }

    @PostMapping("/api/v1/archive-rule-traces:search")
    public CursorPageResponse<Map<String, Object>> searchRuleTraces(
            @RequestBody SearchArchiveRuleTracesRequest request,
            PageRequest pageRequest,
            Authentication authentication) {
        Long userId = requireManage(authentication);
        return ruleService.listRuleTraces(withUserId(request, userId), pageRequest);
    }

    private ExecuteArchiveRulesRequest withUserId(ExecuteArchiveRulesRequest request, Long userId) {
        return new ExecuteArchiveRulesRequest(
                request.schemeVersionId(),
                request.triggerCode(),
                request.fondsCode(),
                request.categoryCode(),
                request.objectTypeCode(),
                request.archiveLevel(),
                request.eventCode(),
                request.facts(),
                request.includeSkipped(),
                request.recordTrace(),
                userId);
    }

    private SearchArchiveRuleTracesRequest withUserId(
            SearchArchiveRuleTracesRequest request, Long userId) {
        return new SearchArchiveRuleTracesRequest(
                request.schemeVersionId(),
                request.triggerCode(),
                request.objectTypeCode(),
                request.objectId(),
                request.ruleType(),
                userId);
    }

    private Long requireManage(Authentication authentication) {
        Long userId =
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal());
        permissionService.requirePermission(
                userId, AuthorizationPermissionCode.ARCHIVE_GOVERNANCE_MANAGE);
        return userId;
    }
}
