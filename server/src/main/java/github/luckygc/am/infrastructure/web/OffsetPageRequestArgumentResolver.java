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

import github.luckygc.am.common.api.OffsetPageRequest;
import github.luckygc.am.common.exception.BadRequestException;

@Component
public class OffsetPageRequestArgumentResolver implements HandlerMethodArgumentResolver {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1000;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return OffsetPageRequest.class.equals(parameter.getParameterType());
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
        return new OffsetPageRequest(
                limit(parameters.value("limit")), offset(parameters.value("offset")));
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

    private long offset(@Nullable String value) {
        if (StringUtils.isBlank(value)) {
            return 0L;
        }
        try {
            long offset = Long.parseLong(value);
            if (offset < 0) {
                throw new NumberFormatException("offset must be non-negative");
            }
            return offset;
        } catch (NumberFormatException exception) {
            throw new BadRequestException("分页参数不合法", "offset", "offset 必须为非负整数");
        }
    }
}
