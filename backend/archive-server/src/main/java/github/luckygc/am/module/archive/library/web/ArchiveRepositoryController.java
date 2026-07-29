package github.luckygc.am.module.archive.library.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import github.luckygc.am.common.api.CollectionResponse;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.library.service.ArchiveRepositoryService;
import github.luckygc.am.module.archive.library.service.ArchiveRepositoryService.ArchiveRepositoryResponse;
import github.luckygc.am.module.archive.library.service.ArchiveRepositoryService.CreateArchiveRepositoryRequest;
import github.luckygc.am.module.archive.library.service.ArchiveRepositoryService.UpdateArchiveRepositoryRequest;

@RestController
public class ArchiveRepositoryController {

    private final ArchiveRepositoryService service;

    public ArchiveRepositoryController(ArchiveRepositoryService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/archive-repositories")
    public CollectionResponse<ArchiveRepositoryResponse> list(Boolean enabled) {
        return CollectionResponse.of(service.list(enabled));
    }

    @PostMapping("/api/v1/archive-repositories")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveRepositoryResponse create(
            @RequestBody CreateArchiveRepositoryRequest request, Authentication authentication) {
        return service.create(request, userId(authentication));
    }

    @PatchMapping("/api/v1/archive-repositories/{id}")
    public ArchiveRepositoryResponse update(
            @PathVariable Long id,
            @RequestBody UpdateArchiveRepositoryRequest request,
            Authentication authentication) {
        return service.update(id, request, userId(authentication));
    }

    @DeleteMapping("/api/v1/archive-repositories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        service.delete(id, userId(authentication));
    }

    private Long userId(Authentication authentication) {
        return AuthenticatedUsers.requireUserId(
                authentication == null ? null : authentication.getPrincipal());
    }
}
