package github.luckygc.am.infrastructure.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import github.luckygc.am.common.api.CursorPageTokenCodec;
import github.luckygc.am.common.api.CursorPageTokenContext;
import github.luckygc.am.common.exception.BadRequestException;

import tools.jackson.databind.json.JsonMapper;

@Component
// Spring Security 默认过滤器顺序是 -100；cursor 必须在其后运行才能绑定登录用户。
@Order(-99)
public class CursorPageTokenValidationFilter extends OncePerRequestFilter {

    private static final String CONTEXT_ATTRIBUTE =
            CursorPageTokenValidationFilter.class.getName() + ".context";
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1000;

    private final CursorHttpFingerprint fingerprint;
    private final JsonMapper jsonMapper;

    public CursorPageTokenValidationFilter(
            CursorHttpFingerprint fingerprint, JsonMapper jsonMapper) {
        this.fingerprint = fingerprint;
        this.jsonMapper = jsonMapper;
    }

    public static CursorPageTokenContext context(ServletRequest request) {
        Object context = request.getAttribute(CONTEXT_ATTRIBUTE);
        return context instanceof CursorPageTokenContext cursorContext
                ? cursorContext
                : new CursorPageTokenContext("", "");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            validateCursor(request);
        } catch (BadRequestException exception) {
            writeBadRequest(response, exception);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void validateCursor(HttpServletRequest request) {
        CursorPageTokenContext context =
                new CursorPageTokenContext(fingerprint.fingerprint(request), userKey(request));
        request.setAttribute(CONTEXT_ATTRIBUTE, context);
        PageRequestParameters parameters = PageRequestParameters.from(request);
        String cursor = StringUtils.trimToNull(parameters.value("cursor"));
        if (cursor != null) {
            PageRequestContentTypeGuard.rejectMultipart(request);
            CursorPageTokenCodec.validate(cursor, limit(parameters.value("limit")), context);
        }
    }

    private int limit(@Nullable String value) {
        if (StringUtils.isBlank(value)) {
            return DEFAULT_LIMIT;
        }
        try {
            int limit = Integer.parseInt(value);
            if (limit <= 0) {
                throw new NumberFormatException("limit must be positive");
            }
            if (limit > MAX_LIMIT) {
                throw new BadRequestException("分页参数不合法", "limit", "limit 不能大于 1000");
            }
            return limit;
        } catch (NumberFormatException exception) {
            throw new BadRequestException("分页参数不合法", "limit", "limit 必须为正整数");
        }
    }

    private String userKey(HttpServletRequest request) {
        return request.getUserPrincipal() == null ? "" : request.getUserPrincipal().getName();
    }

    private void writeBadRequest(HttpServletResponse response, BadRequestException exception)
            throws IOException {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("请求参数无效");
        problem.setProperty("code", "INVALID_ARGUMENT");
        problem.setProperty("reason", "FIELD_VIOLATION");
        problem.setProperty("fieldViolations", exception.fieldViolations());
        jsonMapper.writeValue(response.getWriter(), GlobalExceptionHandler.problemBody(problem));
    }
}
