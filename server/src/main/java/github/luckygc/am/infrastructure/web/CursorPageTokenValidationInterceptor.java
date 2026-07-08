package github.luckygc.am.infrastructure.web;

import java.util.Arrays;

import jakarta.data.page.PageRequest;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import github.luckygc.am.common.api.CursorPageTokenCodec;
import github.luckygc.am.common.api.CursorPageTokenContext;
import github.luckygc.am.common.exception.BadRequestException;

@Component
public class CursorPageTokenValidationInterceptor implements HandlerInterceptor {

    private static final String CONTEXT_ATTRIBUTE =
            CursorPageTokenValidationInterceptor.class.getName() + ".context";

    private final CursorHttpFingerprint fingerprint;

    public CursorPageTokenValidationInterceptor(CursorHttpFingerprint fingerprint) {
        this.fingerprint = fingerprint;
    }

    public static CursorPageTokenContext context(ServletRequest request) {
        Object context = request.getAttribute(CONTEXT_ATTRIBUTE);
        return context instanceof CursorPageTokenContext cursorContext
                ? cursorContext
                : new CursorPageTokenContext("");
    }

    static void setContext(ServletRequest request, CursorPageTokenContext context) {
        request.setAttribute(CONTEXT_ATTRIBUTE, context);
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        boolean cursorPaged = supportsCursorPage(handlerMethod);
        String token = StringUtils.trimToNull(request.getParameter("cursor"));
        if (!cursorPaged) {
            if (token != null) {
                throw invalidCursor("当前接口不支持 cursor 分页");
            }
            return true;
        }
        CursorPageTokenContext context =
                new CursorPageTokenContext(fingerprint.fingerprint(request));
        request.setAttribute(CONTEXT_ATTRIBUTE, context);
        if (token != null) {
            PaginationContentTypeGuard.rejectUnsupportedBodyContentType(request);
            CursorPageTokenCodec.validate(
                    token, CursorPageLimits.parse(request.getParameter("limit")), context);
        }
        return true;
    }

    private boolean supportsCursorPage(HandlerMethod handlerMethod) {
        return Arrays.stream(handlerMethod.getMethodParameters())
                .anyMatch(parameter -> PageRequest.class.equals(parameter.getParameterType()));
    }

    private BadRequestException invalidCursor(String reason) {
        return new BadRequestException("分页 cursor 无效", "cursor", reason);
    }
}
