package github.luckygc.am.module.auth;

import github.luckygc.am.infrastructure.security.AuthenticationResponseWriter;
import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

@Component
public class FormLoginAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthenticationResponseWriter responseWriter;
    private final SecurityContextRepository securityContextRepository;

    public FormLoginAuthenticationSuccessHandler(
            AuthenticationResponseWriter responseWriter,
            SecurityContextRepository securityContextRepository) {
        this.responseWriter = responseWriter;
        this.securityContextRepository = securityContextRepository;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication)
            throws IOException, ServletException {
        var securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);
        securityContextRepository.saveContext(securityContext, request, response);
        responseWriter.writeCurrentUser(response, AuthController.CurrentUserDto.from(authentication));
    }
}
