package github.luckygc.am.infrastructure.web;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import github.luckygc.am.common.exception.BadRequestException;

final class PageRequestParameters {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpServletRequest request;
    private final @Nullable JsonNode jsonBody;

    private PageRequestParameters(HttpServletRequest request, @Nullable JsonNode jsonBody) {
        this.request = request;
        this.jsonBody = jsonBody;
    }

    static PageRequestParameters from(HttpServletRequest request) {
        return new PageRequestParameters(request, jsonBody(request));
    }

    @Nullable String value(String name) {
        String parameterValue = StringUtils.trimToNull(request.getParameter(name));
        if (parameterValue != null) {
            return parameterValue;
        }
        if (jsonBody == null || !jsonBody.isObject() || !jsonBody.has(name)) {
            return null;
        }
        JsonNode node = jsonBody.get(name);
        if (node.isNull()) {
            return null;
        }
        if (node.isValueNode()) {
            return node.asText();
        }
        throw new BadRequestException("分页参数不合法", name, name + " 必须为标量值");
    }

    private static @Nullable JsonNode jsonBody(HttpServletRequest request) {
        if (!isJson(request.getContentType())) {
            return null;
        }
        byte[] body = requestBody(request);
        if (body.length == 0 || StringUtils.isBlank(new String(body, requestCharset(request)))) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(body);
        } catch (IOException exception) {
            throw new BadRequestException("请求体 JSON 无效", "body", "JSON 格式无效");
        }
    }

    private static boolean isJson(@Nullable String contentType) {
        if (StringUtils.isBlank(contentType)) {
            return false;
        }
        MediaType mediaType = MediaType.parseMediaType(contentType);
        return MediaType.APPLICATION_JSON.includes(mediaType)
                || mediaType.getSubtype().endsWith("+json");
    }

    private static byte[] requestBody(HttpServletRequest request) {
        if (request instanceof CachedBodyHttpServletRequestWrapper wrapper) {
            return wrapper.getCachedBody();
        }
        if (request.getContentLengthLong()
                > CachedBodyHttpServletRequestWrapper.MAX_JSON_BODY_BYTES) {
            throw new BadRequestException("请求体过大", "body", "请求体不能超过 1 MiB");
        }
        try {
            return CachedBodyHttpServletRequestWrapper.readBounded(request.getInputStream());
        } catch (IOException exception) {
            throw new BadRequestException("请求体读取失败", "body", "无法读取请求体");
        }
    }

    private static java.nio.charset.Charset requestCharset(HttpServletRequest request) {
        return request.getCharacterEncoding() == null
                ? java.nio.charset.StandardCharsets.UTF_8
                : java.nio.charset.Charset.forName(request.getCharacterEncoding());
    }
}
