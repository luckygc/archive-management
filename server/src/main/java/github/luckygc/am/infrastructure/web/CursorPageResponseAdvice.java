package github.luckygc.am.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;

import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.util.UriComponentsBuilder;

import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.api.CursorPageTokenCodec;
import github.luckygc.am.common.api.CursorPageTokenContext;

@ControllerAdvice
public class CursorPageResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(
            MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        Class<?> parameterType = returnType.getParameterType();
        return CursorPageResponse.class.isAssignableFrom(parameterType);
    }

    @Override
    public @Nullable Object beforeBodyWrite(
            @Nullable Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        if (!(body instanceof CursorPageResponse<?> pageResponse)) {
            return body;
        }
        CursorPageResponse<?> encoded = pageResponse.encodeCursorTokens(context(request));
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return encoded;
        }
        HttpServletRequest httpRequest = servletRequest.getServletRequest();
        return encoded.withLinks(
                link(httpRequest, encoded.self()),
                link(httpRequest, encoded.prev()),
                link(httpRequest, encoded.next()),
                link(httpRequest, encoded.first()));
    }

    private @Nullable String link(HttpServletRequest request, @Nullable String token) {
        if (token == null) {
            return null;
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(request.getRequestURI());
        if (request.getQueryString() != null) {
            builder.query(request.getQueryString());
        }
        builder.replaceQueryParam("cursor", token);
        builder.replaceQueryParam("limit", CursorPageTokenCodec.decode(token).limit());
        return builder.build(true).toUriString();
    }

    private CursorPageTokenContext context(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpRequest = servletRequest.getServletRequest();
            return CursorPageTokenValidationInterceptor.context(httpRequest);
        }
        return new CursorPageTokenContext("");
    }
}
