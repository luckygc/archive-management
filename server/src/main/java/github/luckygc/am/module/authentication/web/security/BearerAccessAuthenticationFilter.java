package github.luckygc.am.module.authentication.web.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import github.luckygc.am.common.api.ApiProblemTypes;
import github.luckygc.am.common.security.AccessTokenGrantHolder;
import github.luckygc.am.module.authentication.AccessTokenFailureReason;
import github.luckygc.am.module.authentication.AccessTokenRateLimiter;
import github.luckygc.am.module.authentication.service.AccessTokenAuthenticator;
import github.luckygc.am.module.authentication.service.AccessTokenAuthenticator.AuthenticationResult;
import github.luckygc.am.module.authentication.service.AccessTokenCallService;
import github.luckygc.am.module.authentication.service.AccessTokenCallService.AccessTokenCallCommand;
import github.luckygc.am.module.authentication.service.AccessTokenUsageService;

import tools.jackson.databind.json.JsonMapper;

public class BearerAccessAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log =
            LoggerFactory.getLogger(BearerAccessAuthenticationFilter.class);

    private final AccessTokenAuthenticator authenticator;
    private final AccessTokenCallService callService;
    private final AccessTokenUsageService usageService;
    private final AccessTokenRateLimiter rateLimiter;
    private final JsonMapper jsonMapper;

    public BearerAccessAuthenticationFilter(
            AccessTokenAuthenticator authenticator,
            AccessTokenCallService callService,
            AccessTokenUsageService usageService,
            AccessTokenRateLimiter rateLimiter,
            JsonMapper jsonMapper) {
        this.authenticator = authenticator;
        this.callService = callService;
        this.usageService = usageService;
        this.rateLimiter = rateLimiter;
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !hasBearer(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        AccessTokenGrantHolder.clear();
        long started = System.nanoTime();
        String rawToken = token(request);
        AuthenticationResult result = authenticator.authenticate(rawToken);
        if (!result.accepted()) {
            AccessTokenFailureReason reason =
                    result.failureReason() == null
                            ? AccessTokenFailureReason.INVALID
                            : result.failureReason();
            reject(
                    request,
                    response,
                    started,
                    result,
                    HttpStatus.UNAUTHORIZED,
                    reason,
                    detail(reason));
            return;
        }
        Long tokenId = result.tokenId();
        if (tokenId == null || result.authentication() == null || result.grant() == null) {
            reject(
                    request,
                    response,
                    started,
                    result,
                    HttpStatus.UNAUTHORIZED,
                    AccessTokenFailureReason.INVALID,
                    "令牌无效");
            return;
        }
        if (!rateLimiter.tryAcquire(tokenId)) {
            reject(
                    request,
                    response,
                    started,
                    result,
                    HttpStatus.TOO_MANY_REQUESTS,
                    AccessTokenFailureReason.RATE_LIMITED,
                    "调用过于频繁");
            return;
        }
        usageService.markUsed(tokenId);
        SecurityContextHolder.getContext().setAuthentication(result.authentication());
        AccessTokenGrantHolder.set(result.grant());
        try {
            filterChain.doFilter(request, response);
        } finally {
            AccessTokenGrantHolder.clear();
            record(request, response, started, result, response.getStatus(), null);
        }
    }

    private void reject(
            HttpServletRequest request,
            HttpServletResponse response,
            long started,
            AuthenticationResult result,
            HttpStatus status,
            AccessTokenFailureReason reason,
            String detail)
            throws IOException {
        record(request, response, started, result, status.value(), reason);
        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            response.setHeader(HttpHeaders.RETRY_AFTER, "60");
        }
        writeProblem(request, response, status, detail);
    }

    private void record(
            HttpServletRequest request,
            HttpServletResponse response,
            long started,
            AuthenticationResult result,
            int statusCode,
            @Nullable AccessTokenFailureReason reason) {
        try {
            callService.record(
                    new AccessTokenCallCommand(
                            result.tokenId(),
                            result.tokenPrefix(),
                            result.callerType(),
                            result.serviceAccountId(),
                            result.representedUserId(),
                            request.getMethod(),
                            requestPath(request),
                            statusCode,
                            (System.nanoTime() - started) / 1_000_000L,
                            sourceIp(request),
                            traceId(response),
                            reason));
        } catch (RuntimeException exception) {
            log.error("访问令牌调用审计写入失败", exception);
        }
    }

    private void writeProblem(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String detail)
            throws IOException {
        String code =
                status == HttpStatus.TOO_MANY_REQUESTS ? "RESOURCE_EXHAUSTED" : "UNAUTHENTICATED";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", ApiProblemTypes.fromCode(code).toString());
        body.put("title", status == HttpStatus.TOO_MANY_REQUESTS ? "请求过多" : "未认证");
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("path", request.getRequestURI());
        String traceId = traceId(response);
        if (StringUtils.isNotBlank(traceId)) {
            body.put("traceId", traceId);
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getWriter(), body);
    }

    private String detail(@Nullable AccessTokenFailureReason reason) {
        if (reason == null) {
            return "令牌无效";
        }
        return switch (reason) {
            case EXPIRED -> "令牌已过期";
            case REVOKED -> "令牌已吊销";
            case DISABLED -> "服务账号或用户已停用";
            case RATE_LIMITED -> "调用过于频繁";
            case INVALID -> "令牌无效";
        };
    }

    private String requestPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        int query = path.indexOf('?');
        return query < 0 ? path : path.substring(0, query);
    }

    private String sourceIp(HttpServletRequest request) {
        return StringUtils.defaultString(request.getRemoteAddr());
    }

    private @Nullable String traceId(HttpServletResponse response) {
        String header = response.getHeader("X-Trace-Id");
        if (StringUtils.isNotBlank(header)) {
            return header;
        }
        return MDC.get("traceId");
    }

    static boolean hasBearer(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        return header != null && header.regionMatches(true, 0, "Bearer ", 0, 7);
    }

    private static String token(HttpServletRequest request) {
        String header = StringUtils.defaultString(request.getHeader(HttpHeaders.AUTHORIZATION));
        return header.length() < 7 ? "" : header.substring(7).trim();
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return true;
    }
}
