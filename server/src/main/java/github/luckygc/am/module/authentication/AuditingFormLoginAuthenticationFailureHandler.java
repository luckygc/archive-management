package github.luckygc.am.module.authentication;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import github.luckygc.am.module.authentication.service.AuthenticationAuditService;
import github.luckygc.am.module.authentication.service.LoginFailureLimitService;

@Component("formLoginAuthenticationFailureHandler")
public class AuditingFormLoginAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final AuthenticationAuditService authenticationAuditService;
    private final LoginFailureLimitService failureLimitService;

    public AuditingFormLoginAuthenticationFailureHandler(
            AuthenticationAuditService authenticationAuditService,
            LoginFailureLimitService failureLimitService) {
        this.authenticationAuditService = authenticationAuditService;
        this.failureLimitService = failureLimitService;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception)
            throws IOException {
        authenticationAuditService.recordLoginFailure(request, "账号或密码错误");
        failureLimitService.recordFailure(request.getParameter("username"));
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("账号或密码错误");
    }
}
