package github.luckygc.am.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

@DisplayName("HTTP cursor 查询摘要")
class CursorHttpFingerprintTests {

    private final CursorHttpFingerprint fingerprint = new CursorHttpFingerprint();

    @Test
    @DisplayName("volumeId 纳入查询摘要且分页 URL 参数不纳入")
    void volumeIdParticipatesInFingerprintWhilePageControlsDoNot() {
        org.springframework.mock.web.MockHttpServletRequest first =
                jsonRequest("{\"volumeId\":12}");
        first.addParameter("limit", "100");
        first.addParameter("cursor", "opaque-one");
        org.springframework.mock.web.MockHttpServletRequest same = jsonRequest("{\"volumeId\":12}");
        same.addParameter("limit", "200");
        same.addParameter("cursor", "opaque-two");
        org.springframework.mock.web.MockHttpServletRequest different =
                jsonRequest("{\"volumeId\":13}");

        assertThat(fingerprint.fingerprint(first)).isEqualTo(fingerprint.fingerprint(same));
        assertThat(fingerprint.fingerprint(first)).isNotEqualTo(fingerprint.fingerprint(different));
    }

    @Test
    @DisplayName("关系列表 depth 纳入查询摘要且 limit 和 cursor 不纳入")
    void relationDepthParticipatesInFingerprint() {
        MockHttpServletRequest first =
                new MockHttpServletRequest("GET", "/api/v1/archive-items/1/relations");
        first.addParameter("depth", "1");
        first.addParameter("limit", "100");
        first.addParameter("cursor", "first-page");
        MockHttpServletRequest same =
                new MockHttpServletRequest("GET", "/api/v1/archive-items/1/relations");
        same.addParameter("depth", "1");
        same.addParameter("limit", "200");
        same.addParameter("cursor", "next-page");
        MockHttpServletRequest different =
                new MockHttpServletRequest("GET", "/api/v1/archive-items/1/relations");
        different.addParameter("depth", "2");

        assertThat(fingerprint.fingerprint(first)).isEqualTo(fingerprint.fingerprint(same));
        assertThat(fingerprint.fingerprint(first)).isNotEqualTo(fingerprint.fingerprint(different));
    }

    @Test
    @DisplayName("URL 查询参数顺序和分页控制参数不影响查询摘要")
    void queryParamOrderAndPageControlsShouldNotAffectFingerprint() {
        MockHttpServletRequest first = jsonRequest("{\"categoryId\":1,\"keyword\":\"合同\"}");
        first.addParameter("limit", "100");
        first.addParameter("pageSize", "100");
        first.addParameter("pageNo", "1");
        first.addParameter("offset", "0");
        first.addParameter("cursor", "old");
        first.addParameter("requestTotal", "true");
        first.addParameter("operationType", "update");
        first.addParameter("categoryId", "1");

        MockHttpServletRequest second = jsonRequest("{\"categoryId\":1,\"keyword\":\"合同\"}");
        second.addParameter("categoryId", "1");
        second.addParameter("operationType", "update");
        second.addParameter("requestTotal", "false");
        second.addParameter("cursor", "next");
        second.addParameter("offset", "100");
        second.addParameter("pageNo", "2");
        second.addParameter("pageSize", "200");
        second.addParameter("limit", "200");

        assertThat(fingerprint.fingerprint(first)).isEqualTo(fingerprint.fingerprint(second));
    }

    @Test
    @DisplayName("非分页查询参数变化会改变查询摘要")
    void nonPageQueryParamChangeShouldAffectFingerprint() {
        MockHttpServletRequest first = jsonRequest("{\"categoryId\":1}");
        first.addParameter("_csrf", "first");
        MockHttpServletRequest second = jsonRequest("{\"categoryId\":1}");
        second.addParameter("_csrf", "second");

        assertThat(fingerprint.fingerprint(first)).isNotEqualTo(fingerprint.fingerprint(second));
    }

    @Test
    @DisplayName("JSON 请求体按原始字节参与查询摘要")
    void jsonBodyBytesShouldAffectFingerprint() {
        MockHttpServletRequest first = jsonRequest("{\"categoryId\":1,\"keyword\":\"合同\"}");
        MockHttpServletRequest second = jsonRequest("{\"keyword\":\"合同\",\"categoryId\":1}");

        assertThat(fingerprint.fingerprint(first)).isNotEqualTo(fingerprint.fingerprint(second));
    }

    @Test
    @DisplayName("普通查询参数变化会改变查询摘要")
    void queryParamChangeShouldAffectFingerprint() {
        MockHttpServletRequest first = jsonRequest("{\"categoryId\":1}");
        first.addParameter("operationType", "update");
        MockHttpServletRequest second = jsonRequest("{\"categoryId\":1}");
        second.addParameter("operationType", "delete");

        assertThat(fingerprint.fingerprint(first)).isNotEqualTo(fingerprint.fingerprint(second));
    }

    @Test
    @DisplayName("JSON 请求体生成摘要时不解析 JSON 结构")
    void jsonBodyShouldBeHashedWithoutParsing() {
        assertThat(fingerprint.fingerprint(jsonRequest(deepJson(101)))).isNotBlank();
    }

    private static String deepJson(int depth) {
        return "[".repeat(depth) + "0" + "]".repeat(depth);
    }

    private static MockHttpServletRequest jsonRequest(String body) {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/v1/archive-records:search");
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
