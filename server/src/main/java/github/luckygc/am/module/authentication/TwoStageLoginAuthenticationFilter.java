package github.luckygc.am.module.authentication;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import github.luckygc.am.module.authentication.service.TotpLoginChallengeService;

import tools.jackson.databind.json.JsonMapper;

public class TwoStageLoginAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    private final TotpLoginChallengeService challengeService;
    private final JsonMapper jsonMapper;

    public TwoStageLoginAuthenticationFilter(
            AuthenticationManager authenticationManager,
            TotpLoginChallengeService challengeService,
            JsonMapper jsonMapper) {
        super(authenticationManager);
        this.challengeService = challengeService;
        this.jsonMapper = jsonMapper;
        setRequiresAuthenticationRequestMatcher(
                request ->
                        "POST".equalsIgnoreCase(request.getMethod())
                                && "/api/v1/login-sessions".equals(request.getRequestURI()));
    }

    @Override
    public @Nullable Authentication attemptAuthentication(
            HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationException {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current != null && current.isAuthenticated()) {
            throw new BadCredentialsException("账号或凭证错误");
        }
        Authentication authentication = super.attemptAuthentication(request, response);
        var challenge = challengeService.startIfRequired(authentication);
        if (challenge.isPresent()) {
            writeChallenge(response, challenge.orElseThrow());
            return null;
        }
        return authentication;
    }

    private void writeChallenge(
            HttpServletResponse response,
            TotpLoginChallengeService.TotpLoginChallengeResponse challenge) {
        response.setStatus(HttpStatus.ACCEPTED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "no-store");
        try {
            jsonMapper.writeValue(response.getWriter(), challenge);
        } catch (IOException ex) {
            throw new IllegalStateException("TOTP 登录挑战响应写入失败", ex);
        }
    }
}
