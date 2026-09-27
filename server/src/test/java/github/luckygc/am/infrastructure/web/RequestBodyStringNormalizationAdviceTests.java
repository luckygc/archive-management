package github.luckygc.am.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import github.luckygc.am.common.api.RawRequestStrings;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("请求体字符串规范化")
class RequestBodyStringNormalizationAdviceTests {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final RequestBodyStringNormalizationAdvice advice =
            new RequestBodyStringNormalizationAdvice(JSON_MAPPER);

    @AfterEach
    void resetRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("POST JSON 请求体在转换前递归 trimToNull")
    void beforeBodyReadShouldNormalizePostJsonBodyBeforeConversion() throws Exception {
        bindRequest(HttpMethod.POST, "/api/v1/authentication-users");
        MethodParameter parameter = bodyParameter("write");
        HttpInputMessage input =
                jsonInput(
                        """
                        {
                          "username": "  zhangsan  ",
                          "email": "   ",
                          "profile": {"displayName": "  张三  "},
                          "tags": ["  A  ", "   ", null]
                        }
                        """);

        HttpInputMessage normalized =
                advice.beforeBodyRead(
                        input, parameter, DemoRequest.class, JacksonJsonHttpMessageConverter.class);

        JsonNode body = JSON_MAPPER.readTree(normalized.getBody());
        assertThat(body.get("username").asText()).isEqualTo("zhangsan");
        assertThat(body.get("email").isNull()).isTrue();
        assertThat(body.get("profile").get("displayName").asText()).isEqualTo("张三");
        assertThat(body.get("tags").get(0).asText()).isEqualTo("A");
        assertThat(body.get("tags").get(1).isNull()).isTrue();
        assertThat(body.get("tags").get(2).isNull()).isTrue();
    }

    @Test
    @DisplayName("标注 RawRequestStrings 的请求体不做 trimToNull")
    void beforeBodyReadShouldKeepRawBodyWhenParameterIsAnnotated() throws Exception {
        bindRequest(HttpMethod.POST, "/api/v1/archive-items:search");
        MethodParameter parameter = bodyParameter("raw");
        HttpInputMessage input = jsonInput("{\"keyword\":\"  合同  \"}");

        HttpInputMessage normalized =
                advice.beforeBodyRead(
                        input, parameter, DemoRequest.class, JacksonJsonHttpMessageConverter.class);

        JsonNode body = JSON_MAPPER.readTree(normalized.getBody());
        assertThat(body.get("keyword").asText()).isEqualTo("  合同  ");
    }

    @Test
    @DisplayName("GET 请求体不做 trimToNull")
    void beforeBodyReadShouldSkipReadOnlyMethods() throws Exception {
        bindRequest(HttpMethod.GET, "/api/v1/items");
        MethodParameter parameter = bodyParameter("write");
        HttpInputMessage input = jsonInput("{\"keyword\":\"  合同  \"}");

        HttpInputMessage normalized =
                advice.beforeBodyRead(
                        input, parameter, DemoRequest.class, JacksonJsonHttpMessageConverter.class);

        JsonNode body = JSON_MAPPER.readTree(normalized.getBody());
        assertThat(body.get("keyword").asText()).isEqualTo("  合同  ");
    }

    private static HttpInputMessage jsonInput(String body) {
        MockHttpInputMessage input =
                new MockHttpInputMessage(body.getBytes(StandardCharsets.UTF_8));
        input.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return input;
    }

    private static void bindRequest(HttpMethod method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method.name(), uri);
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static MethodParameter bodyParameter(String methodName) throws NoSuchMethodException {
        Method method = DemoController.class.getDeclaredMethod(methodName, DemoRequest.class);
        return new MethodParameter(method, 0);
    }

    private record DemoRequest(String keyword) {}

    private static final class DemoController {

        @PostMapping("/api/v1/demo")
        void write(@RequestBody DemoRequest request) throws IOException {}

        @PostMapping("/api/v1/demo:search")
        void raw(@RawRequestStrings @RequestBody DemoRequest request) throws IOException {}
    }
}
