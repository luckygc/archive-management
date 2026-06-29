package github.luckygc.am.module.authentication;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import github.luckygc.am.module.authentication.service.AuthenticationAuditService;
import github.luckygc.am.module.authentication.service.LoginFailureLimitService;

import tools.jackson.databind.json.JsonMapper;

@Component
public class FormLoginAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final JsonMapper jsonMapper;
    private final SecurityContextRepository securityContextRepository;
    private final AuthenticationAuditService authenticationAuditService;
    private final LoginFailureLimitService failureLimitService;

    public FormLoginAuthenticationSuccessHandler(
            JsonMapper jsonMapper,
            SecurityContextRepository securityContextRepository,
            AuthenticationAuditService authenticationAuditService,
            LoginFailureLimitService failureLimitService) {
        this.jsonMapper = jsonMapper;
        this.securityContextRepository = securityContextRepository;
        this.authenticationAuditService = authenticationAuditService;
        this.failureLimitService = failureLimitService;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        var securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);
        securityContextRepository.saveContext(securityContext, request, response);
        failureLimitService.clear(authentication.getName());
        authenticationAuditService.recordLoginSuccess(request, authentication);
        writeLoginSession(
                response, authenticationAuditService.currentLoginSession(request, authentication));
    }

    private void writeLoginSession(
            HttpServletResponse response,
            AuthenticationAuditService.LoginSessionResponse loginSession)
            throws IOException {
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getWriter(), loginSession);
    }
}
