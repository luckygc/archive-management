package github.luckygc.am.infrastructure.security.util;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import github.luckygc.am.infrastructure.security.AuthenticationResponseWriter;

@Component
public class FormLoginAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final AuthenticationResponseWriter responseWriter;

    public FormLoginAuthenticationFailureHandler(AuthenticationResponseWriter responseWriter) {
        this.responseWriter = responseWriter;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception)
            throws IOException {
        responseWriter.writeUnauthorized(response, "账号或密码错误");
    }
}
