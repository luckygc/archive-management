package github.luckygc.am.module.authentication;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import github.luckygc.am.module.authentication.service.LoginFailureLimitService;

@Component
public class LoginFailureLimitFilter extends OncePerRequestFilter {

    private final LoginFailureLimitService failureLimitService;

    public LoginFailureLimitFilter(LoginFailureLimitService failureLimitService) {
        this.failureLimitService = failureLimitService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !"/login-sessions".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            failureLimitService.assertLoginAllowed(request.getParameter("username"));
            filterChain.doFilter(request, response);
        } catch (LoginBlockedException ex) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.TEXT_PLAIN_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("登录受限，请稍后重试。");
        }
    }
}
