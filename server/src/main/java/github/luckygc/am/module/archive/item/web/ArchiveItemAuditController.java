package github.luckygc.am.module.archive.item.web;

import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.api.CursorPageRequest;
import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.module.archive.item.service.ArchiveItemAuditSearchService;
import github.luckygc.am.module.archive.item.service.ArchiveItemAuditSearchService.ArchiveItemAuditResponse;
import github.luckygc.am.module.archive.item.service.ArchiveItemAuditSearchService.ListArchiveItemAuditsRequest;

@RestController
public class ArchiveItemAuditController {

    private final ArchiveItemAuditSearchService auditSearchService;

    public ArchiveItemAuditController(ArchiveItemAuditSearchService auditSearchService) {
        this.auditSearchService = auditSearchService;
    }

    @GetMapping("/api/v1/archive-item-audits")
    public CursorPageResponse<ArchiveItemAuditResponse> listAudits(
            @RequestParam(required = false) @Nullable Long archiveItemId,
            @RequestParam(required = false) @Nullable String fondsCode,
            @RequestParam(required = false) @Nullable String categoryCode,
            @RequestParam(required = false) @Nullable String operationType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    @Nullable LocalDateTime operatedAfter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    @Nullable LocalDateTime operatedBefore,
            CursorPageRequest page,
            @Nullable Authentication authentication) {
        Long userId = currentUserId(authentication);
        return auditSearchService.listAudits(
                new ListArchiveItemAuditsRequest(
                        archiveItemId,
                        fondsCode,
                        categoryCode,
                        operationType,
                        operatedAfter,
                        operatedBefore),
                page,
                userId);
    }

    private Long currentUserId(@Nullable Authentication authentication) {
        if (authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedUser userDetails) {
            return userDetails.id();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
    }
}
