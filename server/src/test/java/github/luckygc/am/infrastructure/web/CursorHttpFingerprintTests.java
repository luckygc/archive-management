package github.luckygc.am.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.fasterxml.jackson.databind.ObjectMapper;

@DisplayName("HTTP cursor 查询指纹")
class CursorHttpFingerprintTests {

    private final CursorHttpFingerprint fingerprint = new CursorHttpFingerprint(new ObjectMapper());

    @Test
    @DisplayName("JSON 字段顺序和分页参数不影响查询指纹")
    void jsonOrderAndPageParamsShouldNotAffectFingerprint() {
        MockHttpServletRequest first = jsonRequest("{\"categoryId\":1,\"keyword\":\"合同\"}");
        first.addParameter("limit", "100");
        first.addParameter("cursor", "old");
        first.addParameter("requestTotal", "true");
        first.addParameter("_csrf", "first");
        first.addParameter("operationType", "update");

        MockHttpServletRequest second = jsonRequest("{\"keyword\":\"合同\",\"categoryId\":1}");
        second.addParameter("limit", "200");
        second.addParameter("cursor", "next");
        second.addParameter("requestTotal", "false");
        second.addParameter("_csrf", "second");
        second.addParameter("operationType", "update");

        assertThat(fingerprint.fingerprint(first)).isEqualTo(fingerprint.fingerprint(second));
    }

    @Test
    @DisplayName("JSON 请求体中的分页字段不影响查询指纹")
    void jsonBodyPageFieldsShouldNotAffectFingerprint() {
        MockHttpServletRequest first =
                jsonRequest(
                        """
                        {"keyword":"合同","limit":100,"cursor":"old","requestTotal":true,"_csrf":"first"}
                        """);
        MockHttpServletRequest second =
                jsonRequest(
                        """
                        {"requestTotal":false,"cursor":"next","_csrf":"second","limit":200,"keyword":"合同"}
                        """);

        assertThat(fingerprint.fingerprint(first)).isEqualTo(fingerprint.fingerprint(second));
    }

    @Test
    @DisplayName("普通查询参数变化会改变查询指纹")
    void queryParamChangeShouldAffectFingerprint() {
        MockHttpServletRequest first = jsonRequest("{\"categoryId\":1}");
        first.addParameter("operationType", "update");
        MockHttpServletRequest second = jsonRequest("{\"categoryId\":1}");
        second.addParameter("operationType", "delete");

        assertThat(fingerprint.fingerprint(first)).isNotEqualTo(fingerprint.fingerprint(second));
    }

    private static MockHttpServletRequest jsonRequest(String body) {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/v1/archive-records:search");
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
