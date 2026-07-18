package github.luckygc.am.module.archive.rule.web;

import java.util.Map;

import jakarta.data.page.PageRequest;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import github.luckygc.am.common.api.CollectionResponse;
import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeStatus;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeTriggerPoint;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeDefinitionService;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeDefinitionService.ArchiveRuntimeDefinitionResponse;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeDefinitionService.SaveArchiveRuntimeDefinitionRequest;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeExecutionService;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeExecutionService.ArchiveRuntimeExecutionRequest;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeExecutionService.ArchiveRuntimeExecutionResult;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeFieldCatalogService;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeFieldCatalogService.ArchiveRuntimeFieldCatalog;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeSnapshotService;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeSnapshotService.ArchiveRuntimeSnapshot;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeSnapshotService.ArchiveRuntimeSnapshotImportRequest;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeSnapshotService.ArchiveRuntimeSnapshotImportResult;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeSnapshotService.ArchiveRuntimeSnapshotPreflightRequest;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeSnapshotService.ArchiveRuntimeSnapshotPreflightResult;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeSnapshotService.ArchiveRuntimeSnapshotRestoreRequest;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeSnapshotService.ArchiveRuntimeSnapshotRestoreResult;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeTraceService;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeTraceService.SearchArchiveRuntimeTracesRequest;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@RestController
public class ArchiveRuntimeController {

    private final ArchiveRuntimeDefinitionService definitionService;
    private final ArchiveRuntimeFieldCatalogService fieldCatalogService;
    private final ArchiveRuntimeExecutionService executionService;
    private final ArchiveRuntimeTraceService traceService;
    private final ArchiveRuntimeSnapshotService snapshotService;
    private final AuthorizationPermissionService permissionService;

    public ArchiveRuntimeController(
            ArchiveRuntimeDefinitionService definitionService,
            ArchiveRuntimeFieldCatalogService fieldCatalogService,
            ArchiveRuntimeExecutionService executionService,
            ArchiveRuntimeTraceService traceService,
            ArchiveRuntimeSnapshotService snapshotService,
            AuthorizationPermissionService permissionService) {
        this.definitionService = definitionService;
        this.fieldCatalogService = fieldCatalogService;
        this.executionService = executionService;
        this.traceService = traceService;
        this.snapshotService = snapshotService;
        this.permissionService = permissionService;
    }

    @GetMapping("/api/v1/archive-runtime-definitions")
    public CollectionResponse<ArchiveRuntimeDefinitionResponse> listDefinitions(
            @RequestParam Long schemeVersionId,
            @RequestParam(required = false) ArchiveRuntimeStatus status,
            Authentication authentication) {
        requireManage(authentication);
        return CollectionResponse.of(definitionService.listDefinitions(schemeVersionId, status));
    }

    @GetMapping("/api/v1/archive-runtime-definitions/{definitionId}")
    public ArchiveRuntimeDefinitionResponse getDefinition(
            @PathVariable Long definitionId, Authentication authentication) {
        requireManage(authentication);
        return definitionService.getDefinition(definitionId);
    }

    @PostMapping("/api/v1/archive-runtime-definitions")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveRuntimeDefinitionResponse createDefinition(
            @RequestBody SaveArchiveRuntimeDefinitionRequest request,
            Authentication authentication) {
        return definitionService.createDefinition(request, requireManage(authentication));
    }

    @PutMapping("/api/v1/archive-runtime-definitions/{definitionId}")
    public ArchiveRuntimeDefinitionResponse updateDefinition(
            @PathVariable Long definitionId,
            @RequestBody SaveArchiveRuntimeDefinitionRequest request,
            Authentication authentication) {
        return definitionService.updateDefinition(
                definitionId, request, requireManage(authentication));
    }

    @PostMapping("/api/v1/archive-runtime-definitions/{definitionId}:publish")
    public ArchiveRuntimeDefinitionResponse publishDefinition(
            @PathVariable Long definitionId, Authentication authentication) {
        return definitionService.publishDefinition(definitionId, requireManage(authentication));
    }

    @PostMapping("/api/v1/archive-runtime-definitions/{definitionId}:enable")
    public ArchiveRuntimeDefinitionResponse enableDefinition(
            @PathVariable Long definitionId, Authentication authentication) {
        return definitionService.updateEnabled(definitionId, true, requireManage(authentication));
    }

    @PostMapping("/api/v1/archive-runtime-definitions/{definitionId}:disable")
    public ArchiveRuntimeDefinitionResponse disableDefinition(
            @PathVariable Long definitionId, Authentication authentication) {
        return definitionService.updateEnabled(definitionId, false, requireManage(authentication));
    }

    @DeleteMapping("/api/v1/archive-runtime-definitions/{definitionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDefinition(@PathVariable Long definitionId, Authentication authentication) {
        definitionService.deleteDefinition(definitionId, requireManage(authentication));
    }

    @GetMapping("/api/v1/archive-runtime-fields")
    public ArchiveRuntimeFieldCatalog getFieldCatalog(
            @RequestParam Long schemeVersionId,
            @RequestParam(required = false) @Nullable String categoryCode,
            @RequestParam ArchiveRuntimeTriggerPoint triggerPoint,
            Authentication authentication) {
        requireManage(authentication);
        return fieldCatalogService.catalog(schemeVersionId, categoryCode, triggerPoint);
    }

    @PostMapping("/api/v1/archive-runtime-definitions:simulate")
    public ArchiveRuntimeExecutionResult simulate(
            @RequestBody ArchiveRuntimeExecutionRequest request, Authentication authentication) {
        Long userId = requireManage(authentication);
        return executionService.simulate(withUserId(request, userId));
    }

    @PostMapping("/api/v1/archive-runtime-traces:search")
    public CursorPageResponse<Map<String, Object>> searchTraces(
            @RequestBody SearchArchiveRuntimeTracesRequest request,
            PageRequest pageRequest,
            Authentication authentication) {
        Long userId = requireManage(authentication);
        return traceService.listTraces(withUserId(request, userId), pageRequest);
    }

    @GetMapping("/api/v1/archive-governance-scheme-versions/{schemeVersionId}/runtime-snapshot")
    public ArchiveRuntimeSnapshot exportSnapshot(
            @PathVariable Long schemeVersionId, Authentication authentication) {
        requireManage(authentication);
        return snapshotService.exportSnapshot(schemeVersionId);
    }

    @PostMapping("/api/v1/archive-runtime-snapshots:preflight")
    public ArchiveRuntimeSnapshotPreflightResult preflightSnapshot(
            @RequestBody ArchiveRuntimeSnapshotPreflightRequest request,
            Authentication authentication) {
        requireManage(authentication);
        return snapshotService.preflight(request);
    }

    @PostMapping("/api/v1/archive-runtime-snapshots:import")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveRuntimeSnapshotImportResult importSnapshot(
            @RequestBody ArchiveRuntimeSnapshotImportRequest request,
            Authentication authentication) {
        return snapshotService.importAsDraft(request, requireManage(authentication));
    }

    @PostMapping(
            "/api/v1/archive-governance-scheme-versions/{schemeVersionId}:restore-runtime-snapshot")
    public ArchiveRuntimeSnapshotRestoreResult restoreSnapshot(
            @PathVariable Long schemeVersionId,
            @RequestBody ArchiveRuntimeSnapshotRestoreRequest request,
            Authentication authentication) {
        return snapshotService.restoreDraft(
                schemeVersionId, request, requireManage(authentication));
    }

    private ArchiveRuntimeExecutionRequest withUserId(
            ArchiveRuntimeExecutionRequest request, Long userId) {
        return new ArchiveRuntimeExecutionRequest(
                request.schemeVersionId(),
                request.triggerPoint(),
                request.fondsCode(),
                request.categoryCode(),
                request.archiveLevel(),
                request.objectTypeCode(),
                request.objectId(),
                request.candidateFacts(),
                userId);
    }

    private SearchArchiveRuntimeTracesRequest withUserId(
            SearchArchiveRuntimeTracesRequest request, Long userId) {
        return new SearchArchiveRuntimeTracesRequest(
                request.schemeVersionId(),
                request.triggerPoint(),
                request.objectTypeCode(),
                request.objectId(),
                request.definitionKind(),
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
