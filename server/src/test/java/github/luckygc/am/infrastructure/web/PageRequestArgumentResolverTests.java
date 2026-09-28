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

import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.api.CursorPageTokenCodec;
import github.luckygc.am.common.api.CursorPageTokenContext;
import github.luckygc.am.common.exception.BadRequestException;

@DisplayName("分页请求参数解析")
class PageRequestArgumentResolverTests {

    @Test
    @DisplayName("cursor resolver 只解析分页参数，不校验查询摘要")
    void cursorResolverShouldOnlyParsePageParameters() throws Exception {
        CursorPageTokenContext context = new CursorPageTokenContext("first-fingerprint");
        String cursor = CursorPageTokenCodec.encode("next", List.of(99L), 50, context);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/archive-item-audits");
        request.addParameter("archiveItemId", "10");
        request.addParameter("limit", "50");
        request.addParameter("cursor", cursor);
        request.addHeader("Prefer", "return=total-count");
        CursorPageArgumentResolver resolver = new CursorPageArgumentResolver();

        PageRequest page =
                (PageRequest)
                        resolver.resolveArgument(
                                cursorParameter(), null, new ServletWebRequest(request), null);

        assertThat(page.size()).isEqualTo(50);
        assertThat(page.mode()).isEqualTo(PageRequest.Mode.CURSOR_NEXT);
        assertThat(page.requestTotal()).isFalse();
    }

    @Test
    @DisplayName("cursor 分页只解析 URL 查询参数")
    void cursorResolverShouldParseOnlyUrlQueryPageParameters() throws Exception {
        CursorPageTokenContext context = new CursorPageTokenContext("fingerprint");
        String cursor = CursorPageTokenCodec.encode("next", List.of(99L), 50, context);
        MockHttpServletRequest request =
                jsonRequest(
                        "POST",
                        "/archive-records:search",
                        "{\"keyword\":\"合同\",\"limit\":10,\"cursor\":\"ignored\"}");
        request.addParameter("limit", "50");
        request.addParameter("cursor", cursor);
        request.addHeader("Prefer", "respond-async, return=total-count");
        CursorPageArgumentResolver resolver = new CursorPageArgumentResolver();

        PageRequest page =
                (PageRequest)
                        resolver.resolveArgument(
                                cursorParameter(),
                                null,
                                new ServletWebRequest(
                                        new CachedBodyHttpServletRequestWrapper(request)),
                                null);

        assertThat(page.size()).isEqualTo(50);
        assertThat(page.requestTotal()).isFalse();
        assertThat(page.mode()).isEqualTo(PageRequest.Mode.CURSOR_NEXT);
    }

    @Test
    @DisplayName("cursor 分页不读取 JSON 请求体中的分页参数")
    void cursorResolverShouldIgnoreJsonBodyPageParameters() throws Exception {
        MockHttpServletRequest request =
                jsonRequest(
                        "POST",
                        "/archive-records:search",
                        "{\"keyword\":\"合同\",\"limit\":50,\"cursor\":\"ignored\"}");
        CursorPageArgumentResolver resolver = new CursorPageArgumentResolver();

        PageRequest page =
                (PageRequest)
                        resolver.resolveArgument(
                                cursorParameter(),
                                null,
                                new ServletWebRequest(
                                        new CachedBodyHttpServletRequestWrapper(request)),
                                null);

        assertThat(page.size()).isEqualTo(100);
        assertThat(page.requestTotal()).isFalse();
    }

    @Test
    @DisplayName("首页仅在请求总数偏好时计算总数")
    void cursorResolverShouldHonorTotalCountPreferenceOnlyOnFirstPage() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/archive-item-audits");
        request.addParameter("limit", "50");
        request.addHeader("Prefer", "respond-async, RETURN=TOTAL-COUNT; ignored=value");

        PageRequest page =
                (PageRequest)
                        new CursorPageArgumentResolver()
                                .resolveArgument(
                                        cursorParameter(),
                                        null,
                                        new ServletWebRequest(request),
                                        null);

        assertThat(page.requestTotal()).isTrue();
    }

    @Test
    @DisplayName("已移除的分页查询参数明确拒绝")
    void cursorResolverShouldRejectRemovedPageParameters() throws Exception {
        for (String name : List.of("requestTotal", "pageNo", "pageSize", "offset")) {
            MockHttpServletRequest request =
                    new MockHttpServletRequest("GET", "/archive-item-audits");
            request.addParameter(name, "1");

            assertThatThrownBy(
                            () ->
                                    new CursorPageArgumentResolver()
                                            .resolveArgument(
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
                                                    violation ->
                                                            assertThat(violation.field())
                                                                    .isEqualTo(name)));
        }
    }

    @Test
    @DisplayName("multipart 请求不能携带 cursor 分页参数")
    void cursorResolverShouldRejectMultipartPageParameters() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/archive-records:search");
        request.setContentType("multipart/form-data; boundary=----test");
        request.addParameter("cursor", "opaque-token");
        CursorPageArgumentResolver resolver = new CursorPageArgumentResolver();

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
                new MockHttpServletRequest("POST", "/archive-records:search");
        request.setContentType("application/x-www-form-urlencoded");
        request.addParameter("limit", "50");
        CursorPageArgumentResolver resolver = new CursorPageArgumentResolver();

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

    private static MethodParameter cursorParameter() throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod("cursor", PageRequest.class);
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
        CursorPageResponse<String> cursor(PageRequest page) {
            return CursorPageResponse.withCursorValues(List.of(), 0, null, null, null, null, null);
        }
    }
}
