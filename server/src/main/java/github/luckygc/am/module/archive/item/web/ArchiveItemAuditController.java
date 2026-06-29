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

import github.luckygc.am.common.api.OffsetPageResponse;
import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.module.archive.item.service.ArchiveItemAuditQueryService;
import github.luckygc.am.module.archive.item.service.ArchiveItemAuditQueryService.ArchiveItemAuditQuery;
import github.luckygc.am.module.archive.item.service.ArchiveItemAuditQueryService.ArchiveItemAuditResponse;

@RestController
public class ArchiveItemAuditController {

    private final ArchiveItemAuditQueryService auditQueryService;

    public ArchiveItemAuditController(ArchiveItemAuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping("/api/v1/archive-item-audits")
    public OffsetPageResponse<ArchiveItemAuditResponse> listAudits(
            @RequestParam(required = false) @Nullable Long archiveItemId,
            @RequestParam(required = false) @Nullable String fondsCode,
            @RequestParam(required = false) @Nullable String categoryCode,
            @RequestParam(required = false) @Nullable String operationType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    @Nullable LocalDateTime operatedAfter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    @Nullable LocalDateTime operatedBefore,
            @RequestParam(required = false) @Nullable Integer limit,
            @RequestParam(required = false) @Nullable Long offset,
            @Nullable Authentication authentication) {
        currentUserId(authentication);
        return auditQueryService.listAudits(
                new ArchiveItemAuditQuery(
                        archiveItemId,
                        fondsCode,
                        categoryCode,
                        operationType,
                        operatedAfter,
                        operatedBefore,
                        limit,
                        offset));
    }

    private Long currentUserId(@Nullable Authentication authentication) {
        if (authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedUser userDetails) {
            return userDetails.id();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
    }
}
