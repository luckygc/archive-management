package github.luckygc.am.module.authentication.web;

import jakarta.data.page.PageRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.authentication.service.AccessTokenManagementService;
import github.luckygc.am.module.authentication.service.AccessTokenManagementService.AccessTokenResponse;
import github.luckygc.am.module.authentication.service.AccessTokenManagementService.CreateAccessTokenRequest;
import github.luckygc.am.module.authentication.service.AccessTokenManagementService.IssuedAccessTokenResponse;

@RestController
public class AccessTokenController {

    private final AccessTokenManagementService tokenService;

    public AccessTokenController(AccessTokenManagementService tokenService) {
        this.tokenService = tokenService;
    }

    @PostMapping("/service-accounts/{serviceAccount}/access-tokens")
    @ResponseStatus(HttpStatus.CREATED)
    public IssuedAccessTokenResponse issueForServiceAccount(
            @PathVariable Long serviceAccount,
            @RequestBody CreateAccessTokenRequest request,
            @Nullable Authentication authentication,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return tokenService.issueForServiceAccount(serviceAccount, request, userId(authentication));
    }

    @GetMapping("/service-accounts/{serviceAccount}/access-tokens")
    public CursorPageResponse<AccessTokenResponse> listForServiceAccount(
            @PathVariable Long serviceAccount,
            PageRequest page,
            @Nullable Authentication authentication) {
        return tokenService.listForServiceAccount(serviceAccount, page, userId(authentication));
    }

    @PostMapping("/me/access-tokens")
    @ResponseStatus(HttpStatus.CREATED)
    public IssuedAccessTokenResponse issueForCurrentUser(
            @RequestBody CreateAccessTokenRequest request,
            @Nullable Authentication authentication,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        Object principal = authentication == null ? null : authentication.getPrincipal();
        return tokenService.issueForUser(request, userId(authentication), principal);
    }

    @GetMapping("/me/access-tokens")
    public CursorPageResponse<AccessTokenResponse> listForCurrentUser(
            PageRequest page, @Nullable Authentication authentication) {
        Object principal = authentication == null ? null : authentication.getPrincipal();
        return tokenService.listForUser(page, userId(authentication), principal);
    }

    @PostMapping("/access-tokens/{id}:revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable Long id, @Nullable Authentication authentication) {
        Object principal = authentication == null ? null : authentication.getPrincipal();
        tokenService.revoke(id, userId(authentication), principal);
    }

    private Long userId(@Nullable Authentication authentication) {
        return AuthenticatedUsers.requireUserId(
                authentication == null ? null : authentication.getPrincipal());
    }
}
