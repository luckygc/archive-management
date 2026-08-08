package github.luckygc.am.module.authentication;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import github.luckygc.am.module.authentication.service.AuthenticationAuditService;

import tools.jackson.databind.json.JsonMapper;

public class TotpChallengeAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private static final String ERROR_MESSAGE = "账号或凭证错误";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final JsonMapper jsonMapper;
    private final AuthenticationAuditService auditService;

    public TotpChallengeAuthenticationFailureHandler(
            JsonMapper jsonMapper, AuthenticationAuditService auditService) {
        this.jsonMapper = jsonMapper;
        this.auditService = auditService;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception)
            throws IOException {
        String code = "TOTP_CHALLENGE_INVALID";
        String username = null;
        if (exception instanceof TotpChallengeAuthenticationException totpException) {
            code = totpException.errorCode();
            username = totpException.username();
        }
        if (username != null) {
            auditService.recordLoginFailure(request, ERROR_MESSAGE, username);
        }
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getWriter(), problemBody(request, response, code));
    }

    private Map<String, Object> problemBody(
            HttpServletRequest request, HttpServletResponse response, String code) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", "Unauthorized");
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("detail", ERROR_MESSAGE);
        body.put("code", code);
        body.put("reason", code);
        String traceId = response.getHeader(TRACE_ID_HEADER);
        if (StringUtils.isNotBlank(traceId)) {
            body.put("traceId", traceId);
        }
        body.put("path", request.getRequestURI());
        return body;
    }
}
