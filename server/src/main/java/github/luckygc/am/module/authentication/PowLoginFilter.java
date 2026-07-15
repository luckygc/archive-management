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
import github.luckygc.am.module.authentication.service.PowChallengeService;

@Component
public class PowLoginFilter extends OncePerRequestFilter {

    private final PowChallengeService powChallengeService;
    private final LoginFailureLimitService failureLimitService;

    public PowLoginFilter(
            PowChallengeService powChallengeService, LoginFailureLimitService failureLimitService) {
        this.powChallengeService = powChallengeService;
        this.failureLimitService = failureLimitService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !"/api/v1/login-sessions".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            failureLimitService.assertLoginAllowed(request.getParameter("username"));
            powChallengeService.consumeToken(request.getParameter("powToken"));
            filterChain.doFilter(request, response);
        } catch (LoginBlockedException ex) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.TEXT_PLAIN_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("登录受限，请稍后重试。");
        } catch (PowChallengeException ex) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.TEXT_PLAIN_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("认证失败，请重试。");
        }
    }
}
