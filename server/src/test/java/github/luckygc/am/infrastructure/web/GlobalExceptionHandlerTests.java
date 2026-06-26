package github.luckygc.am.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.auth.PowChallengeException;

@DisplayName("全局异常处理器")
class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("ResponseStatusException 转为 ProblemDetail 错误结构")
    void responseStatusExceptionUsesProblemDetailShape() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/archive-records/1");
        MDC.put(TraceIdFilter.TRACE_ID, "trace-20260622");
        try {
            var response =
                    handler.handleResponseStatusException(
                            new ResponseStatusException(HttpStatus.NOT_FOUND, "档案记录不存在"), request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getStatus()).isEqualTo(404);
            assertThat(response.getBody().getTitle()).isEqualTo("资源不存在");
            assertThat(response.getBody().getDetail()).isEqualTo("档案记录不存在");
            assertThat(response.getBody().getProperties())
                    .containsEntry("code", "NOT_FOUND")
                    .containsEntry("reason", "NOT_FOUND_ERROR")
                    .containsEntry("traceId", "trace-20260622")
                    .containsEntry("path", "/api/v1/archive-records/1");
        } finally {
            MDC.remove(TraceIdFilter.TRACE_ID);
        }
    }

    @Test
    @DisplayName("BadRequestException 输出字段级错误明细")
    void badRequestExceptionIncludesFieldViolations() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/v1/archive-records");

        var response =
                handler.handleBadRequestException(
                        new BadRequestException("年度必须在合法范围内", "archiveYear", "年度必须在合法范围内"),
                        request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getTitle()).isEqualTo("请求参数无效");
        assertThat(response.getBody().getProperties())
                .containsEntry("code", "INVALID_ARGUMENT")
                .containsEntry("reason", "FIELD_VIOLATION");
        assertThat(response.getBody().getProperties().get("fieldViolations"))
                .asList()
                .singleElement()
                .satisfies(
                        violation ->
                                assertThat(violation)
                                        .hasFieldOrPropertyWithValue("field", "archiveYear")
                                        .hasFieldOrPropertyWithValue("message", "年度必须在合法范围内"));
    }

    @Test
    @DisplayName("CAP 验证异常输出未认证 ProblemDetail")
    void powChallengeExceptionUsesUnauthenticatedProblemDetail() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/v1/auth/cap/validateToken");

        var response =
                handler.handleResponseStatusException(
                        new PowChallengeException("安全验证已失效，请重试"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(401);
        assertThat(response.getBody().getDetail()).isEqualTo("安全验证已失效，请重试");
        assertThat(response.getBody().getProperties())
                .containsEntry("code", "UNAUTHENTICATED")
                .containsEntry("reason", "UNAUTHENTICATED_ERROR")
                .containsEntry("path", "/api/v1/auth/cap/validateToken");
    }

    @Test
    @DisplayName("Spring MVC 请求体校验异常输出字段级错误明细")
    void methodArgumentNotValidExceptionIncludesFieldViolations() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/v1/archive-categories");
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new TestRequest(""), "request");
        bindingResult.addError(new FieldError("request", "displayName", "名称不能为空"));
        MethodParameter parameter =
                new MethodParameter(
                        GlobalExceptionHandlerTests.class.getDeclaredMethod(
                                "handle", TestRequest.class),
                        0);
        MethodArgumentNotValidException exception =
                new MethodArgumentNotValidException(parameter, bindingResult);

        var response =
                handler.handleMethodArgumentNotValid(
                        exception,
                        HttpHeaders.EMPTY,
                        HttpStatus.BAD_REQUEST,
                        new ServletWebRequest(request));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
                .isNotNull()
                .isInstanceOfSatisfying(
                        org.springframework.http.ProblemDetail.class,
                        problem -> {
                            assertThat(problem.getProperties())
                                    .containsEntry("code", "INVALID_ARGUMENT")
                                    .containsEntry("reason", "FIELD_VIOLATION")
                                    .containsEntry("path", "/api/v1/archive-categories");
                            assertThat(problem.getProperties().get("fieldViolations"))
                                    .asList()
                                    .singleElement()
                                    .satisfies(
                                            violation ->
                                                    assertThat(violation)
                                                            .hasFieldOrPropertyWithValue(
                                                                    "field", "displayName")
                                                            .hasFieldOrPropertyWithValue(
                                                                    "message", "名称不能为空"));
                        });
    }

    @SuppressWarnings("unused")
    private static void handle(TestRequest request) {}

    private record TestRequest(String displayName) {}
}
