package github.luckygc.am.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import github.luckygc.am.common.exception.BadRequestException;

@DisplayName("JSON 请求体缓存包装器")
class CachedBodyHttpServletRequestWrapperTests {

    @Test
    @DisplayName("超过预算的 JSON 请求体拒绝缓存")
    void oversizedJsonBodyShouldBeRejected() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/v1/archive-records:search");
        request.setContentType("application/json");
        request.setContent(
                ("{\"keyword\":\"" + "a".repeat(1024 * 1024) + "\"}")
                        .getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new CachedBodyHttpServletRequestWrapper(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("请求体过大");
    }
}
