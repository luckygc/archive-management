package github.luckygc.am.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.Order;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import github.luckygc.am.common.api.CursorPageTokenCodec;
import github.luckygc.am.common.api.CursorPageTokenContext;

import tools.jackson.databind.json.JsonMapper;

@DisplayName("cursor token 请求处理校验")
class CursorPageTokenValidationFilterTests {

    private static final int SPRING_SECURITY_DEFAULT_FILTER_ORDER = -100;

    private final CursorHttpFingerprint fingerprint = new CursorHttpFingerprint();
    private final CursorPageTokenValidationFilter filter =
            new CursorPageTokenValidationFilter(fingerprint, JsonMapper.builder().build());

    @Test
    @DisplayName("cursor 校验过滤器必须排在 Spring Security 之后才能绑定登录用户")
    void filterShouldRunAfterSpringSecurity() {
        Order order = CursorPageTokenValidationFilter.class.getAnnotation(Order.class);

        assertThat(order).isNotNull();
        assertThat(order.value()).isGreaterThan(SPRING_SECURITY_DEFAULT_FILTER_ORDER);
    }

    @Test
    @DisplayName("请求处理阶段校验 cursor token 与当前请求一致")
    void filterShouldValidateCursorTokenAgainstCurrentRequest() throws Exception {
        MockHttpServletRequest first =
                jsonRequest(
                        """
                        {"categoryId":1,"orderBy":[{"field":"createdAt","direction":"DESC"}]}
                        """);
        first.addParameter("limit", "50");
        CursorPageTokenContext context =
                new CursorPageTokenContext(
                        fingerprint.fingerprint(new CachedBodyHttpServletRequestWrapper(first)),
                        "");
        String cursor = CursorPageTokenCodec.encode("next", List.of(99L), 50, context);
        MockHttpServletRequest request =
                jsonRequest(
                        """
                        {"orderBy":[{"direction":"DESC","field":"createdAt"}],"categoryId":1}
                        """);
        request.addParameter("limit", "50");
        request.addParameter("cursor", cursor);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(
                new CachedBodyHttpServletRequestWrapper(request),
                new MockHttpServletResponse(),
                chain);

        assertThat(CursorPageTokenValidationFilter.context(chain.getRequest())).isEqualTo(context);
    }

    @Test
    @DisplayName("请求体排序变化时拒绝旧 cursor")
    void filterShouldRejectCursorWhenBodyOrderChanged() throws Exception {
        MockHttpServletRequest first =
                jsonRequest(
                        """
                        {"categoryId":1,"orderBy":[{"field":"createdAt","direction":"DESC"}]}
                        """);
        first.addParameter("limit", "50");
        CursorPageTokenContext context =
                new CursorPageTokenContext(
                        fingerprint.fingerprint(new CachedBodyHttpServletRequestWrapper(first)),
                        "");
        String cursor = CursorPageTokenCodec.encode("next", List.of(99L), 50, context);
        MockHttpServletRequest request =
                jsonRequest(
                        """
                        {"categoryId":1,"orderBy":[{"field":"createdAt","direction":"ASC"}]}
                        """);
        request.addParameter("limit", "50");
        request.addParameter("cursor", cursor);

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(
                new CachedBodyHttpServletRequestWrapper(request), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString())
                .contains("\"code\":\"INVALID_ARGUMENT\"", "cursor 查询条件不匹配");
    }

    private static MockHttpServletRequest jsonRequest(String body) {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/v1/archive-items:search");
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
