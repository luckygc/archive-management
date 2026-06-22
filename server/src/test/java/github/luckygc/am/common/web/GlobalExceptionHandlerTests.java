package github.luckygc.am.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void responseStatusExceptionUsesGoogleApiErrorShape() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/archive-records/1");
        MDC.put(TraceIdFilter.TRACE_ID, "trace-20260622");
        try {
            var response =
                    handler.handleResponseStatusException(
                            new ResponseStatusException(HttpStatus.NOT_FOUND, "档案记录不存在"), request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().code()).isEqualTo(404);
            assertThat(response.getBody().error().status()).isEqualTo("NOT_FOUND");
            assertThat(response.getBody().error().message()).isEqualTo("档案记录不存在");
            assertThat(response.getBody().error().details())
                    .singleElement()
                    .satisfies(
                            detail -> {
                                assertThat(detail)
                                        .containsEntry(
                                                "@type",
                                                "type.googleapis.com/google.rpc.ErrorInfo");
                                assertThat(detail).containsEntry("reason", "NOT_FOUND_ERROR");
                                assertThat(detail).containsEntry("domain", "archive-management");
                                assertThat(detail.get("metadata"))
                                        .asString()
                                        .contains("trace-20260622");
                            });
        } finally {
            MDC.remove(TraceIdFilter.TRACE_ID);
        }
    }

    @Test
    void badRequestExceptionIncludesFieldViolations() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/v1/archive-records");

        var response =
                handler.handleResponseStatusException(
                        new ApiBadRequestException("年度必须在合法范围内", "archiveYear", "年度必须在合法范围内"),
                        request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo(400);
        assertThat(response.getBody().error().status()).isEqualTo("INVALID_ARGUMENT");
        assertThat(response.getBody().error().details()).hasSize(2);
        assertThat(response.getBody().error().details().get(1))
                .containsEntry("@type", "type.googleapis.com/google.rpc.BadRequest");
        assertThat(response.getBody().error().details().get(1).get("fieldViolations"))
                .asList()
                .singleElement()
                .satisfies(
                        violation ->
                                assertThat(violation)
                                        .hasFieldOrPropertyWithValue("field", "archiveYear")
                                        .hasFieldOrPropertyWithValue("description", "年度必须在合法范围内"));
    }
}
