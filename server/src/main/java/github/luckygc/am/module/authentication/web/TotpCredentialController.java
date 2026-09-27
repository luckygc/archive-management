package github.luckygc.am.module.authentication.web;

import jakarta.servlet.http.HttpServletRequest;

import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.authentication.service.TotpCredentialService;
import github.luckygc.am.module.authentication.service.TotpCredentialService.CreateTotpCredentialRequest;
import github.luckygc.am.module.authentication.service.TotpCredentialService.DisableTotpCredentialRequest;
import github.luckygc.am.module.authentication.service.TotpCredentialService.TotpEnrollmentResponse;

@RestController
public class TotpCredentialController {

    private final TotpCredentialService credentialService;

    public TotpCredentialController(TotpCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @PostMapping("/api/v1/totp-enrollments")
    public ResponseEntity<TotpEnrollmentResponse> prepareEnrollment(
            @Nullable Authentication authentication) {
        Long userId = userId(authentication);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(credentialService.prepareEnrollment(userId));
    }

    @PostMapping("/api/v1/totp-credentials")
    @ResponseStatus(HttpStatus.CREATED)
    public TotpCredentialStatusResponse createCredential(
            @RequestBody CreateTotpCredentialRequest request,
            HttpServletRequest httpRequest,
            @Nullable Authentication authentication) {
        credentialService.createCredential(
                request, userId(authentication), username(authentication), httpRequest);
        return new TotpCredentialStatusResponse(true);
    }

    @PostMapping("/api/v1/totp-credentials:disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disableCredential(
            @RequestBody DisableTotpCredentialRequest request,
            HttpServletRequest httpRequest,
            @Nullable Authentication authentication) {
        credentialService.disableCredential(
                request, userId(authentication), username(authentication), httpRequest);
    }

    private Long userId(@Nullable Authentication authentication) {
        return AuthenticatedUsers.requireUserId(
                authentication == null ? null : authentication.getPrincipal());
    }

    private String username(@Nullable Authentication authentication) {
        if (authentication == null) {
            AuthenticatedUsers.requireUserId(null);
            throw new IllegalStateException("当前用户缺失");
        }
        return authentication.getName();
    }

    public record TotpCredentialStatusResponse(boolean totpEnabled) {}
}
