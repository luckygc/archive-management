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

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class CursorHttpFingerprint {

    private static final String CURSOR_PARAM = "cursor";
    private static final String LIMIT_PARAM = "limit";
    private static final String OFFSET_PARAM = "offset";
    private static final String PAGE_NO_PARAM = "pageNo";
    private static final String PAGE_SIZE_PARAM = "pageSize";
    private static final String REQUEST_TOTAL_PARAM = "requestTotal";
    private static final String CSRF_PARAM = "_csrf";
    private static final int MAX_JSON_DEPTH = 100;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public String fingerprint(HttpServletRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append(request.getMethod()).append('\n');
        builder.append(request.getRequestURI()).append('\n');
        appendParameters(builder, request.getParameterMap());
        appendBody(builder, request);
        return DigestUtils.sha256Hex(builder.toString());
    }

    private void appendParameters(StringBuilder builder, Map<String, String[]> parameters) {
        TreeMap<String, String[]> ordered = new TreeMap<>(parameters);
        ordered.forEach(
                (name, values) -> {
                    if (isPaginationParameter(name)) {
                        return;
                    }
                    builder.append("p:").append(name).append('=');
                    Arrays.stream(values).forEach(value -> builder.append(value).append('\u001f'));
                    builder.append('\n');
                });
    }

    private boolean isPaginationParameter(String name) {
        return CURSOR_PARAM.equals(name)
                || LIMIT_PARAM.equals(name)
                || OFFSET_PARAM.equals(name)
                || PAGE_NO_PARAM.equals(name)
                || PAGE_SIZE_PARAM.equals(name)
                || REQUEST_TOTAL_PARAM.equals(name)
                || CSRF_PARAM.equals(name);
    }

    private void appendBody(StringBuilder builder, HttpServletRequest request) {
        if (!isJson(request.getContentType())) {
            return;
        }
        byte[] body = requestBody(request);
        if (body.length == 0 || StringUtils.isBlank(new String(body, StandardCharsets.UTF_8))) {
            return;
        }
        try {
            JsonNode node = jsonMapper.readTree(body);
            builder.append("b:").append(canonicalJson(node, true, 0)).append('\n');
        } catch (JacksonException exception) {
            throw new BadRequestException("请求体 JSON 无效", "body", "JSON 格式无效");
        }
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

    private String canonicalJson(JsonNode node, boolean root, int depth) throws JacksonException {
        if (depth > MAX_JSON_DEPTH) {
            throw new BadRequestException("请求体 JSON 无效", "body", "JSON 嵌套层级过深");
        }
        if (node.isObject()) {
            StringBuilder builder = new StringBuilder("{");
            boolean first = true;
            TreeMap<String, JsonNode> fields = new TreeMap<>();
            node.properties()
                    .forEach(
                            entry -> {
                                if (!root || !isPaginationParameter(entry.getKey())) {
                                    fields.put(entry.getKey(), entry.getValue());
                                }
                            });
            for (Map.Entry<String, JsonNode> entry : fields.entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append(jsonMapper.writeValueAsString(entry.getKey()))
                        .append(':')
                        .append(canonicalJson(entry.getValue(), false, depth + 1));
            }
            return builder.append('}').toString();
        }
        if (node.isArray()) {
            StringBuilder builder = new StringBuilder("[");
            boolean first = true;
            for (JsonNode item : node) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append(canonicalJson(item, false, depth + 1));
            }
            return builder.append(']').toString();
        }
        return jsonMapper.writeValueAsString(node);
    }
}
