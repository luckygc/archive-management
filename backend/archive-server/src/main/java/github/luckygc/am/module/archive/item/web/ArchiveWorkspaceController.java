package github.luckygc.am.module.archive.item.web;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.item.service.ArchiveWorkspaceService;
import github.luckygc.am.module.archive.item.service.ArchiveWorkspaceService.ArchiveWorkspaceSummary;

@RestController
public class ArchiveWorkspaceController {

    private final ArchiveWorkspaceService workspaceService;

    public ArchiveWorkspaceController(ArchiveWorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping("/api/v1/workspace-summary")
    public WorkspaceSummaryResponse getSummary(Authentication authentication) {
        ArchiveWorkspaceSummary summary =
                workspaceService.getSummary(
                        AuthenticatedUsers.requireUserId(
                                authentication == null ? null : authentication.getPrincipal()));
        return new WorkspaceSummaryResponse(
                summary.archiveItemCount(),
                summary.draftCount(),
                summary.lockedCount(),
                summary.electronicFileCount());
    }

    public record WorkspaceSummaryResponse(
            long archiveItemCount, long draftCount, long lockedCount, long electronicFileCount) {}
}
