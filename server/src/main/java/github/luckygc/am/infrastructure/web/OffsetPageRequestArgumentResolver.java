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

    private static final int DEFAULT_PAGE_NO = 1;
    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int MAX_PAGE_SIZE = 1000;

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
        PaginationContentTypeGuard.rejectUnsupportedBodyContentType(request);
        PageRequestParameters parameters = PageRequestParameters.from(request);
        return new OffsetPageRequest(
                pageSize(parameters.value("pageSize")), pageNo(parameters.value("pageNo")));
    }

    private int pageSize(@Nullable String value) {
        if (StringUtils.isBlank(value)) {
            return DEFAULT_PAGE_SIZE;
        }
        try {
            int pageSize = Integer.parseInt(value);
            if (pageSize <= 0) {
                throw new NumberFormatException("pageSize must be positive");
            }
            if (pageSize > MAX_PAGE_SIZE) {
                throw new BadRequestException("分页参数不合法", "pageSize", "pageSize 不能大于 1000");
            }
            return pageSize;
        } catch (NumberFormatException exception) {
            throw new BadRequestException("分页参数不合法", "pageSize", "pageSize 必须为正整数");
        }
    }

    private int pageNo(@Nullable String value) {
        if (StringUtils.isBlank(value)) {
            return DEFAULT_PAGE_NO;
        }
        try {
            int pageNo = Integer.parseInt(value);
            if (pageNo <= 0) {
                throw new NumberFormatException("pageNo must be positive");
            }
            return pageNo;
        } catch (NumberFormatException exception) {
            throw new BadRequestException("分页参数不合法", "pageNo", "pageNo 必须为正整数");
        }
    }
}
