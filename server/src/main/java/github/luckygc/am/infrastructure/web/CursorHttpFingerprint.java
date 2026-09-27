package github.luckygc.am.infrastructure.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import github.luckygc.am.common.exception.BadRequestException;

@Component
public class CursorHttpFingerprint {

    private static final String CURSOR_PARAM = "cursor";
    private static final String LIMIT_PARAM = "limit";
    private static final String OFFSET_PARAM = "offset";
    private static final String PAGE_NO_PARAM = "pageNo";
    private static final String PAGE_SIZE_PARAM = "pageSize";
    private static final String REQUEST_TOTAL_PARAM = "requestTotal";

    public String fingerprint(HttpServletRequest request) {
        StringBuilder builder = new StringBuilder();
        appendParameters(builder, request.getParameterMap());
        appendBody(builder, request);
        return DigestUtils.sha256Hex(builder.toString());
    }

    private void appendParameters(StringBuilder builder, Map<String, String[]> parameters) {
        TreeMap<String, String[]> ordered = new TreeMap<>(parameters);
        ordered.forEach(
                (name, values) -> {
                    if (isPageControlParameter(name)) {
                        return;
                    }
                    builder.append("p:").append(name).append('=');
                    Arrays.stream(values).forEach(value -> builder.append(value).append('\u001f'));
                    builder.append('\n');
                });
    }

    private boolean isPageControlParameter(String name) {
        return CURSOR_PARAM.equals(name)
                || LIMIT_PARAM.equals(name)
                || OFFSET_PARAM.equals(name)
                || PAGE_NO_PARAM.equals(name)
                || PAGE_SIZE_PARAM.equals(name)
                || REQUEST_TOTAL_PARAM.equals(name);
    }

    private void appendBody(StringBuilder builder, HttpServletRequest request) {
        if (!isJson(request.getContentType())) {
            return;
        }
        byte[] body = requestBody(request);
        if (body.length == 0 || StringUtils.isBlank(new String(body, StandardCharsets.UTF_8))) {
            return;
        }
        builder.append("b:").append(DigestUtils.sha256Hex(body)).append('\n');
    }

    private boolean isJson(@Nullable String contentType) {
        if (StringUtils.isBlank(contentType)) {
            return false;
        }
        MediaType mediaType = MediaType.parseMediaType(contentType);
        return MediaType.APPLICATION_JSON.includes(mediaType)
                || mediaType.getSubtype().endsWith("+json");
    }

    private byte[] requestBody(HttpServletRequest request) {
        if (request instanceof CachedBodyHttpServletRequestWrapper wrapper) {
            return wrapper.getCachedBody();
        }
        try {
            return request.getInputStream().readAllBytes();
        } catch (IOException exception) {
            throw new BadRequestException("请求体读取失败", "body", "无法读取请求体");
        }
    }
}
