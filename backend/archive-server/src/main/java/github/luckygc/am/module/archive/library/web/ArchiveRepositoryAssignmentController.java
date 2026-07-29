package github.luckygc.am.module.archive.library.web;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.library.service.ArchiveRepositoryAssignmentService;
import github.luckygc.am.module.archive.library.service.ArchiveRepositoryAssignmentService.ChangeArchiveRepositoryRequest;
import github.luckygc.am.module.archive.library.service.ArchiveRepositoryAssignmentService.ChangeArchiveRepositoryResponse;

@RestController
public class ArchiveRepositoryAssignmentController {

    private final ArchiveRepositoryAssignmentService service;

    public ArchiveRepositoryAssignmentController(ArchiveRepositoryAssignmentService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/archive-items/{id}:changeRepository")
    public ChangeArchiveRepositoryResponse changeItemRepository(
            @PathVariable Long id,
            @RequestBody ChangeArchiveRepositoryRequest request,
            Authentication authentication) {
        return service.changeItemRepository(id, request, userId(authentication));
    }

    @PostMapping("/api/v1/archive-volumes/{id}:changeRepository")
    public ChangeArchiveRepositoryResponse changeVolumeRepository(
            @PathVariable Long id,
            @RequestBody ChangeArchiveRepositoryRequest request,
            Authentication authentication) {
        return service.changeVolumeRepository(id, request, userId(authentication));
    }

    private Long userId(Authentication authentication) {
        return AuthenticatedUsers.requireUserId(
                authentication == null ? null : authentication.getPrincipal());
    }
}
