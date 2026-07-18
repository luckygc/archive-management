package github.luckygc.am.infrastructure.web;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractJacksonHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import github.luckygc.am.common.api.RawRequestStrings;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@ControllerAdvice
public class RequestBodyStringNormalizationAdvice extends RequestBodyAdviceAdapter {

    private final JsonMapper jsonMapper;

    public RequestBodyStringNormalizationAdvice(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public boolean supports(
            MethodParameter methodParameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        return AbstractJacksonHttpMessageConverter.class.isAssignableFrom(converterType)
                && shouldNormalize(methodParameter);
    }

    @Override
    public HttpInputMessage beforeBodyRead(
            HttpInputMessage inputMessage,
            MethodParameter parameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType)
            throws IOException {
        if (!AbstractJacksonHttpMessageConverter.class.isAssignableFrom(converterType)
                || !shouldNormalize(parameter)
                || !isJson(inputMessage.getHeaders().getContentType())) {
            return inputMessage;
        }
        byte[] body = inputMessage.getBody().readAllBytes();
        if (body.length == 0) {
            return inputMessage;
        }
        JsonNode normalized = normalize(jsonMapper.readTree(body));
        return new ByteArrayHttpInputMessage(
                jsonMapper.writeValueAsBytes(normalized), inputMessage.getHeaders());
    }

    private boolean shouldNormalize(MethodParameter parameter) {
        if (hasRawRequestStrings(parameter)) {
            return false;
        }
        HttpMethod method = currentMethod();
        return HttpMethod.POST.equals(method)
                || HttpMethod.PUT.equals(method)
                || HttpMethod.PATCH.equals(method);
    }

    private boolean hasRawRequestStrings(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(RawRequestStrings.class)
                || parameter.hasMethodAnnotation(RawRequestStrings.class)
                || parameter.getParameterType().isAnnotationPresent(RawRequestStrings.class)
                || parameter.getContainingClass().isAnnotationPresent(RawRequestStrings.class);
    }

    private @Nullable HttpMethod currentMethod() {
        if (!(RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        return HttpMethod.valueOf(attributes.getRequest().getMethod());
    }

    private boolean isJson(@Nullable MediaType contentType) {
        return contentType != null
                && (MediaType.APPLICATION_JSON.includes(contentType)
                        || (contentType.getSubtype() != null
                                && contentType.getSubtype().endsWith("+json")));
    }

    private JsonNode normalize(JsonNode node) {
        if (node.isTextual()) {
            String value = StringUtils.trimToNull(node.asText());
            return value == null
                    ? JsonNodeFactory.instance.nullNode()
                    : JsonNodeFactory.instance.stringNode(value);
        }
        if (node.isObject()) {
            normalizeObject(node.asObject());
        } else if (node.isArray()) {
            normalizeArray(node.asArray());
        }
        return node;
    }

    private void normalizeObject(ObjectNode node) {
        List<String> propertyNames = new ArrayList<>(node.propertyNames());
        for (String propertyName : propertyNames) {
            node.set(propertyName, normalize(node.get(propertyName)));
        }
    }

    private void normalizeArray(ArrayNode node) {
        for (int index = 0; index < node.size(); index++) {
            node.set(index, normalize(node.get(index)));
        }
    }

    private static final class ByteArrayHttpInputMessage implements HttpInputMessage {

        private final byte[] body;
        private final HttpHeaders headers;

        private ByteArrayHttpInputMessage(byte[] body, HttpHeaders sourceHeaders) {
            this.body = body;
            this.headers = HttpHeaders.copyOf(sourceHeaders);
            this.headers.setContentLength(body.length);
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }
    }
}
