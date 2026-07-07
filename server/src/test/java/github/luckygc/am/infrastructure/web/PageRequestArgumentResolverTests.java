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

import github.luckygc.am.common.api.CursorPageRequest;
import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.api.CursorPageTokenCodec;
import github.luckygc.am.common.api.CursorPageTokenContext;
import github.luckygc.am.common.api.OffsetPageRequest;
import github.luckygc.am.common.exception.BadRequestException;

@DisplayName("分页请求参数解析")
class PageRequestArgumentResolverTests {

    @Test
    @DisplayName("cursor resolver 只解析分页参数，不校验查询摘要")
    void cursorResolverShouldOnlyParsePageParameters() throws Exception {
        CursorPageTokenContext context = new CursorPageTokenContext("first-fingerprint");
        String cursor = CursorPageTokenCodec.encode("next", List.of(99L), 50, context);
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/archive-item-audits");
        request.addParameter("archiveItemId", "10");
        request.addParameter("limit", "50");
        request.addParameter("cursor", cursor);
        request.addParameter("requestTotal", "true");
        CursorPageRequestArgumentResolver resolver = new CursorPageRequestArgumentResolver();

        CursorPageRequest page =
                (CursorPageRequest)
                        resolver.resolveArgument(
                                cursorParameter(), null, new ServletWebRequest(request), null);

        assertThat(page.limit()).isEqualTo(50);
        assertThat(page.requestTotal()).isTrue();
        assertThat(page.pageRequest().mode()).isEqualTo(PageRequest.Mode.CURSOR_NEXT);
        assertThat(page.pageRequest().requestTotal()).isFalse();
    }

    @Test
    @DisplayName("cursor 分页只解析 URL 查询参数")
    void cursorResolverShouldParseOnlyUrlQueryPageParameters() throws Exception {
        MockHttpServletRequest first =
                jsonRequest("POST", "/api/v1/archive-records:search", "{\"keyword\":\"合同\"}");
        first.addParameter("limit", "50");
        CursorPageTokenContext context = new CursorPageTokenContext("fingerprint");
        String cursor = CursorPageTokenCodec.encode("next", List.of(99L), 50, context);
        MockHttpServletRequest request =
                jsonRequest(
                        "POST",
                        "/api/v1/archive-records:search",
                        "{\"keyword\":\"合同\",\"limit\":10,\"cursor\":\"ignored\",\"requestTotal\":false}");
        request.addParameter("limit", "50");
        request.addParameter("cursor", cursor);
        request.addParameter("requestTotal", "true");
        CursorPageRequestArgumentResolver resolver = new CursorPageRequestArgumentResolver();

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
    }

    @Test
    @DisplayName("cursor 分页不读取 JSON 请求体中的分页参数")
    void cursorResolverShouldIgnoreJsonBodyPageParameters() throws Exception {
        MockHttpServletRequest request =
                jsonRequest(
                        "POST",
                        "/api/v1/archive-records:search",
                        "{\"keyword\":\"合同\",\"limit\":50,\"requestTotal\":true}");
        CursorPageRequestArgumentResolver resolver = new CursorPageRequestArgumentResolver();

        CursorPageRequest page =
                (CursorPageRequest)
                        resolver.resolveArgument(
                                cursorParameter(),
                                null,
                                new ServletWebRequest(
                                        new CachedBodyHttpServletRequestWrapper(request)),
                                null);

        assertThat(page.limit()).isEqualTo(100);
        assertThat(page.requestTotal()).isFalse();
    }

    @Test
    @DisplayName("multipart 请求不能携带 cursor 分页参数")
    void cursorResolverShouldRejectMultipartPageParameters() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/v1/archive-records:search");
        request.setContentType("multipart/form-data; boundary=----test");
        request.addParameter("cursor", "opaque-token");
        CursorPageRequestArgumentResolver resolver = new CursorPageRequestArgumentResolver();

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
                                                                    "multipart 请求不能携带分页参数，请使用 URL 参数");
                                                }));
    }

    @Test
    @DisplayName("非 JSON 请求体不能携带 cursor 分页参数")
    void cursorResolverShouldRejectFormPageParameters() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/v1/archive-records:search");
        request.setContentType("application/x-www-form-urlencoded");
        request.addParameter("limit", "50");
        CursorPageRequestArgumentResolver resolver = new CursorPageRequestArgumentResolver();

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
                                                            .isEqualTo("分页请求体只支持 JSON");
                                                }));
    }

    @Test
    @DisplayName("解析 offset 分页 URL 参数")
    void offsetResolverShouldParsePageSizeAndPageNo() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/items");
        request.addParameter("pageSize", "200");
        request.addParameter("pageNo", "3");
        OffsetPageRequestArgumentResolver resolver = new OffsetPageRequestArgumentResolver();

        OffsetPageRequest page =
                (OffsetPageRequest)
                        resolver.resolveArgument(
                                offsetParameter(), null, new ServletWebRequest(request), null);

        assertThat(page.pageSize()).isEqualTo(200);
        assertThat(page.pageNo()).isEqualTo(3);
        assertThat(page.offset()).isEqualTo(400);
    }

    @Test
    @DisplayName("offset 分页只解析 URL 查询参数")
    void offsetResolverShouldParseOnlyUrlQueryPageParameters() throws Exception {
        MockHttpServletRequest request =
                jsonRequest("POST", "/api/v1/items:search", "{\"pageSize\":10,\"pageNo\":2}");
        request.addParameter("pageSize", "200");
        request.addParameter("pageNo", "3");
        OffsetPageRequestArgumentResolver resolver = new OffsetPageRequestArgumentResolver();

        OffsetPageRequest page =
                (OffsetPageRequest)
                        resolver.resolveArgument(
                                offsetParameter(),
                                null,
                                new ServletWebRequest(
                                        new CachedBodyHttpServletRequestWrapper(request)),
                                null);

        assertThat(page.pageSize()).isEqualTo(200);
        assertThat(page.pageNo()).isEqualTo(3);
        assertThat(page.offset()).isEqualTo(400);
    }

    @Test
    @DisplayName("offset 分页不读取 JSON 请求体中的分页参数")
    void offsetResolverShouldIgnoreJsonBodyPageParameters() throws Exception {
        MockHttpServletRequest request =
                jsonRequest("POST", "/api/v1/items:search", "{\"pageSize\":200,\"pageNo\":3}");
        OffsetPageRequestArgumentResolver resolver = new OffsetPageRequestArgumentResolver();

        OffsetPageRequest page =
                (OffsetPageRequest)
                        resolver.resolveArgument(
                                offsetParameter(),
                                null,
                                new ServletWebRequest(
                                        new CachedBodyHttpServletRequestWrapper(request)),
                                null);

        assertThat(page.pageSize()).isEqualTo(100);
        assertThat(page.pageNo()).isEqualTo(1);
        assertThat(page.offset()).isZero();
    }

    @Test
    @DisplayName("multipart 请求不能携带 offset 分页参数")
    void offsetResolverShouldRejectMultipartPageParameters() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/items");
        request.setContentType("multipart/form-data; boundary=----test");
        request.addParameter("pageSize", "200");
        request.addParameter("pageNo", "3");
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
                                                                    "multipart 请求不能携带分页参数，请使用 URL 参数");
                                                }));
    }

    @Test
    @DisplayName("非 JSON 请求体不能携带 offset 分页参数")
    void offsetResolverShouldRejectFormPageParameters() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/items");
        request.setContentType("application/x-www-form-urlencoded");
        request.addParameter("pageSize", "200");
        request.addParameter("pageNo", "3");
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
                                                            .isEqualTo("分页请求体只支持 JSON");
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
