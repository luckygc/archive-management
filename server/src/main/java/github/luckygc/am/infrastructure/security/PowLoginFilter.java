package github.luckygc.am.infrastructure.security;

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

import github.luckygc.am.module.auth.PowChallengeException;
import github.luckygc.am.module.auth.PowChallengeService;

@Component
public class PowLoginFilter extends OncePerRequestFilter {

    private final PowChallengeService powChallengeService;

    public PowLoginFilter(PowChallengeService powChallengeService) {
        this.powChallengeService = powChallengeService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod()) || !"/api/auth/login".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {
        try {
            powChallengeService.consumeToken(request.getParameter("powToken"));
            filterChain.doFilter(request, response);
        } catch (PowChallengeException ex) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.TEXT_PLAIN_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(ex.getMessage());
        }
    }
}
