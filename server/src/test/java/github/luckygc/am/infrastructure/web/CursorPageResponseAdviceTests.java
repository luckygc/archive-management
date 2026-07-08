package github.luckygc.am.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.api.CursorPageTokenCodec;
import github.luckygc.am.common.api.CursorPageTokenContext;

import tools.jackson.databind.json.JsonMapper;

@DisplayName("cursor 分页响应包装")
class CursorPageResponseAdviceTests {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final CursorPageResponseAdvice advice = new CursorPageResponseAdvice();

    @Test
    @DisplayName("将分页 slice 包装为带签名 cursor token 的统一响应")
    void shouldWrapSliceWithCursorTokens() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/items");
        CursorPageTokenContext context = new CursorPageTokenContext("digest");
        CursorPageTokenValidationInterceptor.setContext(request, context);
        CursorPageResponse<String> page =
                CursorPageResponse.withCursorValues(
                        List.of("A", "B"), 20, List.of(1L), null, List.of(2L), null, null);

        Object body =
                advice.beforeBodyWrite(
                        page,
                        returnType(),
                        MediaType.APPLICATION_JSON,
                        JacksonJsonHttpMessageConverter.class,
                        new ServletServerHttpRequest(request),
                        null);

        assertThat(body).isInstanceOf(CursorPageResponse.class);
        @SuppressWarnings("unchecked")
        CursorPageResponse<String> response = (CursorPageResponse<String>) body;
        assertThat(response.items()).containsExactly("A", "B");
        assertThat(response.prev()).isNull();
        assertThat(CursorPageTokenCodec.decode(response.self()).context()).isEqualTo(context);
        assertThat(CursorPageTokenCodec.decode(response.self()).limit()).isEqualTo(20);
        assertThat(CursorPageTokenCodec.decode(response.next()).values()).isEqualTo(List.of(2L));
    }

    @Test
    @DisplayName("内部游标状态字段不序列化")
    void cursorStateShouldNotBeSerialized() throws Exception {
        CursorPageResponse<String> page =
                CursorPageResponse.withCursorValues(
                        List.of("A"), 20, List.of(1L), null, List.of(2L), null, 9L);

        String json = JSON_MAPPER.writeValueAsString(page);

        assertThat(json).contains("\"items\"");
        assertThat(json).contains("\"total\"");
        assertThat(json)
                .doesNotContain("limit")
                .doesNotContain("selfValues")
                .doesNotContain("prevValues")
                .doesNotContain("nextValues")
                .doesNotContain("firstValues");
    }

    @Test
    @DisplayName("支持自定义 CursorPageResponse 实现")
    void shouldEncodeCustomCursorPageResponseImplementation() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/items");
        CursorPageTokenContext context = new CursorPageTokenContext("custom-digest");
        CursorPageTokenValidationInterceptor.setContext(request, context);
        CustomCursorPageResponse page =
                new CustomCursorPageResponse(
                        CursorPageResponse.withCursorValues(
                                List.of("A"), 20, List.of(1L), null, List.of(2L), null, null));

        Object body =
                advice.beforeBodyWrite(
                        page,
                        customReturnType(),
                        MediaType.APPLICATION_JSON,
                        JacksonJsonHttpMessageConverter.class,
                        new ServletServerHttpRequest(request),
                        null);

        assertThat(body).isInstanceOf(CustomCursorPageResponse.class);
        CustomCursorPageResponse response = (CustomCursorPageResponse) body;
        assertThat(response.items()).containsExactly("A");
        assertThat(CursorPageTokenCodec.decode(response.self()).context()).isEqualTo(context);
        assertThat(CursorPageTokenCodec.decode(response.next()).values()).isEqualTo(List.of(2L));
    }

    private static MethodParameter returnType() throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod("list", HttpServletRequest.class);
        return new MethodParameter(method, -1);
    }

    private static MethodParameter customReturnType() throws NoSuchMethodException {
        Method method =
                TestController.class.getDeclaredMethod("customList", HttpServletRequest.class);
        return new MethodParameter(method, -1);
    }

    private record CustomCursorPageResponse(CursorPageResponse<String> delegate)
            implements CursorPageResponse<String> {

        @Override
        public List<String> items() {
            return delegate.items();
        }

        @Override
        public String self() {
            return delegate.self();
        }

        @Override
        public String prev() {
            return delegate.prev();
        }

        @Override
        public String next() {
            return delegate.next();
        }

        @Override
        public String first() {
            return delegate.first();
        }

        @Override
        public Long total() {
            return delegate.total();
        }

        @Override
        public CustomCursorPageResponse encodeCursorTokens(CursorPageTokenContext context) {
            return new CustomCursorPageResponse(delegate.encodeCursorTokens(context));
        }
    }

    static class TestController {
        CursorPageResponse<String> list(HttpServletRequest request) {
            return CursorPageResponse.withCursorValues(List.of(), 20, null, null, null, null, null);
        }

        CustomCursorPageResponse customList(HttpServletRequest request) {
            return new CustomCursorPageResponse(
                    CursorPageResponse.withCursorValues(
                            List.of(), 20, null, null, null, null, null));
        }
    }
}
