package github.luckygc.am.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.data.page.PageRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import github.luckygc.am.common.api.CursorPageTokenCodec;
import github.luckygc.am.common.api.CursorPageTokenContext;
import github.luckygc.am.common.api.OffsetPageRequest;
import github.luckygc.am.common.exception.BadRequestException;

@DisplayName("cursor token 请求处理校验")
class CursorPageTokenValidationInterceptorTests {

    private final CursorHttpFingerprint fingerprint = new CursorHttpFingerprint();
    private final CursorPageTokenValidationInterceptor interceptor =
            new CursorPageTokenValidationInterceptor(fingerprint);

    @Test
    @DisplayName("只对声明 PageRequest 的 Controller 方法校验 cursor")
    void interceptorShouldValidateOnlyCursorPageHandlers() throws Exception {
        MockHttpServletRequest first =
                jsonRequest("{\"categoryId\":1,\"orderBy\":[{\"field\":\"createdAt\"}]}");
        first.addParameter("limit", "50");
        CursorPageTokenContext context =
                new CursorPageTokenContext(
                        fingerprint.fingerprint(new CachedBodyHttpServletRequestWrapper(first)));
        String cursor = CursorPageTokenCodec.encode("next", List.of(99L), 50, context);
        MockHttpServletRequest request =
                jsonRequest("{\"categoryId\":1,\"orderBy\":[{\"field\":\"createdAt\"}]}");
        request.addParameter("limit", "50");
        request.addParameter("cursor", cursor);
        request.addParameter("requestTotal", "true");

        CachedBodyHttpServletRequestWrapper wrapped =
                new CachedBodyHttpServletRequestWrapper(request);

        interceptor.preHandle(wrapped, new MockHttpServletResponse(), cursorHandler());

        assertThat(CursorPageTokenValidationInterceptor.context(wrapped)).isEqualTo(context);
    }

    @Test
    @DisplayName("offset 分页 Controller 方法提交 cursor 时拒绝")
    void interceptorShouldRejectCursorForOffsetPageHandlers() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/items");
        request.addParameter("cursor", "opaque");

        assertThatThrownBy(
                        () ->
                                interceptor.preHandle(
                                        request, new MockHttpServletResponse(), offsetHandler()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("分页 cursor 无效");
    }

    @Test
    @DisplayName("JSON 请求体原始字节变化时拒绝旧 cursor")
    void interceptorShouldRejectCursorWhenBodyBytesChanged() throws Exception {
        MockHttpServletRequest first =
                jsonRequest("{\"categoryId\":1,\"orderBy\":[{\"field\":\"createdAt\"}]}");
        first.addParameter("limit", "50");
        CursorPageTokenContext context =
                new CursorPageTokenContext(
                        fingerprint.fingerprint(new CachedBodyHttpServletRequestWrapper(first)));
        String cursor = CursorPageTokenCodec.encode("next", List.of(99L), 50, context);
        MockHttpServletRequest request =
                jsonRequest("{\"orderBy\":[{\"field\":\"createdAt\"}],\"categoryId\":1}");
        request.addParameter("limit", "50");
        request.addParameter("cursor", cursor);

        assertThatThrownBy(
                        () ->
                                interceptor.preHandle(
                                        new CachedBodyHttpServletRequestWrapper(request),
                                        new MockHttpServletResponse(),
                                        cursorHandler()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("分页 cursor 无效")
                .satisfies(
                        exception ->
                                assertThat(((BadRequestException) exception).fieldViolations())
                                        .singleElement()
                                        .satisfies(
                                                violation ->
                                                        assertThat(violation.message())
                                                                .isEqualTo("查询条件已变化，请从第一页重新查询")));
    }

    private static MockHttpServletRequest jsonRequest(String body) {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/v1/archive-items:search");
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private static HandlerMethod cursorHandler() throws NoSuchMethodException {
        return new HandlerMethod(
                new TestController(), TestController.class.getMethod("cursor", PageRequest.class));
    }

    private static HandlerMethod offsetHandler() throws NoSuchMethodException {
        return new HandlerMethod(
                new TestController(),
                TestController.class.getMethod("offset", OffsetPageRequest.class));
    }

    static class TestController {
        public void cursor(PageRequest page) {}

        public void offset(OffsetPageRequest page) {}
    }
}
