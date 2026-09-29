package github.luckygc.am.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ApiAuthenticationEntryPointTests {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void unauthenticatedApiRequestReturnsProblemDetail() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/archive-items");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ApiAuthenticationEntryPoint(jsonMapper)
                .commence(
                        request,
                        response,
                        new InsufficientAuthenticationException("Full authentication is required"));

        JsonNode body = jsonMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(body.get("type").asText()).endsWith("api-problems.md#unauthenticated");
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("path").asText()).isEqualTo("/archive-items");
        assertThat(response.getContentAsString()).doesNotContain("Full authentication is required");
    }
}
