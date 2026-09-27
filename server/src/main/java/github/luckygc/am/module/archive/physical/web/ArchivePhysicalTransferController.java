package github.luckygc.am.module.archive.physical.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.physical.service.ArchivePhysicalTransferService;
import github.luckygc.am.module.archive.physical.service.ArchivePhysicalTransferService.AcceptArchivePhysicalTransferRequest;
import github.luckygc.am.module.archive.physical.service.ArchivePhysicalTransferService.ArchivePhysicalTransferResponse;
import github.luckygc.am.module.archive.physical.service.ArchivePhysicalTransferService.CreateArchivePhysicalTransferRequest;
import github.luckygc.am.module.archive.physical.service.ArchivePhysicalTransferService.RejectArchivePhysicalTransferRequest;

@RestController
public class ArchivePhysicalTransferController {

    private final ArchivePhysicalTransferService service;

    public ArchivePhysicalTransferController(ArchivePhysicalTransferService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/archive-physical-transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchivePhysicalTransferResponse create(
            @RequestBody CreateArchivePhysicalTransferRequest request,
            Authentication authentication) {
        return service.create(request, userId(authentication));
    }

    @GetMapping("/api/v1/archive-physical-transfers/{id}")
    public ArchivePhysicalTransferResponse get(
            @PathVariable Long id, Authentication authentication) {
        return service.get(id, userId(authentication));
    }

    @PostMapping("/api/v1/archive-physical-transfers/{id}:accept")
    public ArchivePhysicalTransferResponse accept(
            @PathVariable Long id,
            @RequestBody AcceptArchivePhysicalTransferRequest request,
            Authentication authentication) {
        return service.accept(id, request, userId(authentication));
    }

    @PostMapping("/api/v1/archive-physical-transfers/{id}:reject")
    public ArchivePhysicalTransferResponse reject(
            @PathVariable Long id,
            @RequestBody RejectArchivePhysicalTransferRequest request,
            Authentication authentication) {
        return service.reject(id, request, userId(authentication));
    }

    private Long userId(Authentication authentication) {
        return AuthenticatedUsers.requireUserId(
                authentication == null ? null : authentication.getPrincipal());
    }
}
