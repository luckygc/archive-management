package github.luckygc.am.infrastructure.web;

import java.util.Arrays;
import java.util.Collections;

import jakarta.data.page.PageRequest;
import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import github.luckygc.am.common.api.CursorPageTokenCodec;
import github.luckygc.am.common.exception.BadRequestException;

@Component
public class CursorPageArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return PageRequest.class.equals(parameter.getParameterType());
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
        PaginationContentTypeGuard.rejectUnsupportedBodyContentType(request);
        if (request.getParameterMap().containsKey("requestTotal")) {
            throw new BadRequestException(
                    "分页参数不合法", "requestTotal", "请使用 Prefer: return=total-count 请求总数");
        }
        PageRequestParameters parameters = PageRequestParameters.from(request);
        int limit = CursorPageLimits.parse(parameters.value("limit"));
        String token = StringUtils.trimToNull(parameters.value("cursor"));
        boolean requestTotal =
                Collections.list(request.getHeaders("Prefer")).stream()
                        .flatMap(value -> Arrays.stream(value.split(",")))
                        .map(value -> value.split(";", 2)[0].trim())
                        .anyMatch("return=total-count"::equalsIgnoreCase);
        return CursorPageTokenCodec.pageRequest(limit, token, requestTotal);
    }
}
