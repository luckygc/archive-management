package github.luckygc.am.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import github.luckygc.am.common.api.CursorPageRequest;
import github.luckygc.am.common.exception.BadRequestException;

@Component
public class CursorPageRequestArgumentResolver implements HandlerMethodArgumentResolver {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1000;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CursorPageRequest.class.equals(parameter.getParameterType());
    }

    @Override
    public @Nullable Object resolveArgument(
            MethodParameter parameter,
            @Nullable ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            @Nullable WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            throw new BadRequestException("请求上下文无效", "request", "缺少 HTTP 请求上下文");
        }
        PageRequestContentTypeGuard.rejectMultipart(request);
        PageRequestParameters parameters = PageRequestParameters.from(request);
        int limit = limit(parameters.value("limit"));
        String cursor = StringUtils.trimToNull(parameters.value("cursor"));
        boolean requestTotal = Boolean.parseBoolean(parameters.value("requestTotal"));
        return CursorPageRequest.of(
                limit, cursor, requestTotal, CursorPageTokenValidationFilter.context(request));
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
}
