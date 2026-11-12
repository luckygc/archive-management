package github.luckygc.am.module.authentication.web;

import jakarta.data.page.PageRequest;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.authentication.service.AccessTokenCallService;
import github.luckygc.am.module.authentication.service.AccessTokenCallService.AccessTokenCallResponse;

@RestController
public class AccessTokenCallController {

    private final AccessTokenCallService callService;

    public AccessTokenCallController(AccessTokenCallService callService) {
        this.callService = callService;
    }

    @GetMapping("/access-token-calls")
    public CursorPageResponse<AccessTokenCallResponse> list(
            @RequestParam(required = false) @Nullable Long serviceAccountId,
            @RequestParam(required = false) @Nullable Long tokenId,
            PageRequest page,
            @Nullable Authentication authentication) {
        return callService.list(
                serviceAccountId,
                tokenId,
                page,
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal()));
    }
}
