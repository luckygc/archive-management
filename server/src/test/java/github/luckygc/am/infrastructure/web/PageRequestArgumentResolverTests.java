package github.luckygc.am.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.data.page.PageRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import com.fasterxml.jackson.databind.ObjectMapper;

import github.luckygc.am.common.api.CursorPageRequest;
import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.api.CursorPageTokenCodec;
import github.luckygc.am.common.api.CursorPageTokenContext;
import github.luckygc.am.common.api.OffsetPageRequest;
import github.luckygc.am.common.exception.BadRequestException;

@DisplayName("分页请求参数解析")
class PageRequestArgumentResolverTests {

    private final CursorHttpFingerprint fingerprint = new CursorHttpFingerprint(new ObjectMapper());

    @Test
    @DisplayName("解析 cursor 分页参数并校验 token 查询指纹")
    void cursorResolverShouldParseAndValidateFingerprint() throws Exception {
        MockHttpServletRequest first =
                new MockHttpServletRequest("GET", "/api/v1/archive-item-audits");
        first.addParameter("archiveItemId", "10");
        CursorPageTokenContext context =
                new CursorPageTokenContext(
                        "GET /api/v1/archive-item-audits", fingerprint.fingerprint(first), "");
        String cursor = CursorPageTokenCodec.encode("next", List.of(99L), 50, context);
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/archive-item-audits");
        request.addParameter("archiveItemId", "10");
        request.addParameter("limit", "50");
        request.addParameter("cursor", cursor);
        request.addParameter("requestTotal", "true");
        CursorPageRequestArgumentResolver resolver =
                new CursorPageRequestArgumentResolver(fingerprint);

        CursorPageRequest page =
                (CursorPageRequest)
                        resolver.resolveArgument(
                                cursorParameter(), null, new ServletWebRequest(request), null);

        assertThat(page.limit()).isEqualTo(50);
        assertThat(page.requestTotal()).isTrue();
        assertThat(page.pageRequest().mode()).isEqualTo(PageRequest.Mode.CURSOR_NEXT);
        assertThat(page.pageRequest().requestTotal()).isFalse();
        assertThat(page.context()).isEqualTo(context);
    }

    @Test
    @DisplayName("解析 JSON 请求体中的 cursor 分页参数")
    void cursorResolverShouldParseJsonBodyPageParameters() throws Exception {
        MockHttpServletRequest first =
                jsonRequest(
                        "POST",
                        "/api/v1/archive-records:search",
                        "{\"keyword\":\"合同\",\"limit\":50}");
        CursorPageTokenContext context =
                new CursorPageTokenContext(
                        "POST /api/v1/archive-records:search",
                        fingerprint.fingerprint(new CachedBodyHttpServletRequestWrapper(first)),
                        "");
        String cursor = CursorPageTokenCodec.encode("next", List.of(99L), 50, context);
        MockHttpServletRequest request =
                jsonRequest(
                        "POST",
                        "/api/v1/archive-records:search",
                        "{\"keyword\":\"合同\",\"limit\":50,\"cursor\":\""
                                + cursor
                                + "\",\"requestTotal\":true}");
        CursorPageRequestArgumentResolver resolver =
                new CursorPageRequestArgumentResolver(fingerprint);

        CursorPageRequest page =
                (CursorPageRequest)
                        resolver.resolveArgument(
                                cursorParameter(),
                                null,
                                new ServletWebRequest(
                                        new CachedBodyHttpServletRequestWrapper(request)),
                                null);

        assertThat(page.limit()).isEqualTo(50);
        assertThat(page.requestTotal()).isTrue();
        assertThat(page.pageRequest().mode()).isEqualTo(PageRequest.Mode.CURSOR_NEXT);
        assertThat(page.context()).isEqualTo(context);
    }

    @Test
    @DisplayName("multipart 请求不能携带 cursor 分页参数")
    void cursorResolverShouldRejectMultipartPageParameters() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/v1/archive-records:search");
        request.setContentType("multipart/form-data; boundary=----test");
        request.addParameter("cursor", "opaque-token");
        CursorPageRequestArgumentResolver resolver =
                new CursorPageRequestArgumentResolver(fingerprint);

        assertThatThrownBy(
                        () ->
                                resolver.resolveArgument(
                                        cursorParameter(),
                                        null,
                                        new ServletWebRequest(request),
                                        null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("分页参数不合法")
                .satisfies(
                        exception ->
                                assertThat(((BadRequestException) exception).fieldViolations())
                                        .singleElement()
                                        .satisfies(
                                                violation -> {
                                                    assertThat(violation.field())
                                                            .isEqualTo("pagination");
                                                    assertThat(violation.message())
                                                            .isEqualTo(
                                                                    "multipart 请求不能携带分页参数，请使用 JSON 请求体或 URL 参数");
                                                }));
    }

    @Test
    @DisplayName("解析 offset 分页参数")
    void offsetResolverShouldParseLimitAndOffset() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/items");
        request.addParameter("limit", "200");
        request.addParameter("offset", "400");
        OffsetPageRequestArgumentResolver resolver = new OffsetPageRequestArgumentResolver();

        OffsetPageRequest page =
                (OffsetPageRequest)
                        resolver.resolveArgument(
                                offsetParameter(), null, new ServletWebRequest(request), null);

        assertThat(page.limit()).isEqualTo(200);
        assertThat(page.offset()).isEqualTo(400);
    }

    @Test
    @DisplayName("解析 JSON 请求体中的 offset 分页参数")
    void offsetResolverShouldParseJsonBodyPageParameters() throws Exception {
        MockHttpServletRequest request =
                jsonRequest("POST", "/api/v1/items:search", "{\"limit\":200,\"offset\":400}");
        OffsetPageRequestArgumentResolver resolver = new OffsetPageRequestArgumentResolver();

        OffsetPageRequest page =
                (OffsetPageRequest)
                        resolver.resolveArgument(
                                offsetParameter(),
                                null,
                                new ServletWebRequest(
                                        new CachedBodyHttpServletRequestWrapper(request)),
                                null);

        assertThat(page.limit()).isEqualTo(200);
        assertThat(page.offset()).isEqualTo(400);
    }

    @Test
    @DisplayName("multipart 请求不能携带 offset 分页参数")
    void offsetResolverShouldRejectMultipartPageParameters() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/items");
        request.setContentType("multipart/form-data; boundary=----test");
        request.addParameter("limit", "200");
        request.addParameter("offset", "400");
        OffsetPageRequestArgumentResolver resolver = new OffsetPageRequestArgumentResolver();

        assertThatThrownBy(
                        () ->
                                resolver.resolveArgument(
                                        offsetParameter(),
                                        null,
                                        new ServletWebRequest(request),
                                        null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("分页参数不合法")
                .satisfies(
                        exception ->
                                assertThat(((BadRequestException) exception).fieldViolations())
                                        .singleElement()
                                        .satisfies(
                                                violation -> {
                                                    assertThat(violation.field())
                                                            .isEqualTo("pagination");
                                                    assertThat(violation.message())
                                                            .isEqualTo(
                                                                    "multipart 请求不能携带分页参数，请使用 JSON 请求体或 URL 参数");
                                                }));
    }

    private static MethodParameter cursorParameter() throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod("cursor", CursorPageRequest.class);
        return new MethodParameter(method, 0);
    }

    private static MethodParameter offsetParameter() throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod("offset", OffsetPageRequest.class);
        return new MethodParameter(method, 0);
    }

    private static MockHttpServletRequest jsonRequest(String method, String uri, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private static final class TestController {

        @SuppressWarnings("unused")
        CursorPageResponse<String> cursor(CursorPageRequest page) {
            return new CursorPageResponse<>(List.of(), null, null, null, null, null);
        }

        @SuppressWarnings("unused")
        void offset(OffsetPageRequest page) {}
    }
}
