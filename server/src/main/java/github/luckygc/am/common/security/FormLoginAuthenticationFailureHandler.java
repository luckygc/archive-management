package github.luckygc.am.common.security;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

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
            throws IOException, ServletException {
        responseWriter.writeUnauthorized(response, "账号或密码错误");
    }
}
