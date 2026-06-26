package github.luckygc.am.infrastructure.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import github.luckygc.am.common.api.ApiFieldViolation;
import github.luckygc.am.common.exception.BadRequestException;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BadRequestException.class)
    ResponseEntity<ProblemDetail> handleBadRequestException(
            BadRequestException exception, HttpServletRequest request) {
        String message = StringUtils.defaultIfBlank(exception.getMessage(), "请求参数不合法");
        ProblemDetail problem =
                problem(
                        HttpStatus.BAD_REQUEST,
                        "请求参数无效",
                        message,
                        "INVALID_ARGUMENT",
                        reason(exception),
                        request);
        withFieldViolations(problem, exception.fieldViolations());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> handleResponseStatusException(
            ResponseStatusException exception, HttpServletRequest request) {
        HttpStatusCode statusCode = exception.getStatusCode();
        String message = StringUtils.defaultIfBlank(exception.getReason(), "请求处理失败");
        String code = canonicalCode(statusCode);
        ProblemDetail problem =
                problem(statusCode, title(statusCode), message, code, code + "_ERROR", request);
        return ResponseEntity.status(statusCode).body(problem);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleException(Exception exception, HttpServletRequest request) {
        log.error("未处理的接口异常", exception);
        ProblemDetail problem =
                problem(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "服务处理失败",
                        "服务处理失败，请稍后重试",
                        "INTERNAL",
                        "UNHANDLED_EXCEPTION",
                        request);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail problem =
                problem(
                        status,
                        "请求参数无效",
                        "请求字段校验失败",
                        "INVALID_ARGUMENT",
                        "FIELD_VIOLATION",
                        request);
        withFieldViolations(
                problem,
                exception.getBindingResult().getFieldErrors().stream()
                        .map(this::fieldViolation)
                        .toList());
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail problem =
                problem(
                        status,
                        "请求参数无效",
                        "请求字段校验失败",
                        "INVALID_ARGUMENT",
                        "FIELD_VIOLATION",
                        request);
        withFieldViolations(
                problem,
                exception.getParameterValidationResults().stream()
                        .flatMap(
                                result ->
                                        result.getResolvableErrors().stream()
                                                .map(
                                                        error ->
                                                                new ApiFieldViolation(
                                                                        parameterName(result),
                                                                        StringUtils.defaultIfBlank(
                                                                                error
                                                                                        .getDefaultMessage(),
                                                                                "参数不合法"))))
                        .toList());
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        Object responseBody = body;
        if (responseBody instanceof ProblemDetail problem) {
            completeProblem(problem, statusCode, request);
        } else if (responseBody == null) {
            ProblemDetail problem =
                    problem(
                            statusCode,
                            title(statusCode),
                            StringUtils.defaultIfBlank(title(statusCode), "请求处理失败"),
                            canonicalCode(statusCode),
                            canonicalCode(statusCode) + "_ERROR",
                            request);
            responseBody = problem;
        }
        return super.createResponseEntity(responseBody, headers, statusCode, request);
    }

    public static ProblemDetail problem(
            HttpStatusCode status,
            String title,
            String detail,
            String code,
            String reason,
            HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        problem.setProperty("reason", reason);
        String traceId = MDC.get(TraceIdFilter.TRACE_ID);
        if (StringUtils.isNotBlank(traceId)) {
            problem.setProperty("traceId", traceId);
        }
        problem.setProperty("path", request.getRequestURI());
        return problem;
    }

    private ProblemDetail problem(
            HttpStatusCode status,
            String title,
            String detail,
            String code,
            String reason,
            WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        problem.setProperty("reason", reason);
        withTraceAndPath(problem, request);
        return problem;
    }

    public static Map<String, Object> problemBody(ProblemDetail problem) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", problem.getType() == null ? "about:blank" : problem.getType().toString());
        body.put("title", problem.getTitle());
        body.put("status", problem.getStatus());
        body.put("detail", problem.getDetail());
        if (problem.getInstance() != null) {
            body.put("instance", problem.getInstance().toString());
        }
        if (problem.getProperties() != null) {
            problem.getProperties()
                    .forEach(
                            (key, value) -> {
                                if (value != null) {
                                    body.put(key, value);
                                }
                            });
        }
        return body;
    }

    private void withFieldViolations(
            ProblemDetail problem, List<ApiFieldViolation> fieldViolations) {
        if (!fieldViolations.isEmpty()) {
            problem.setProperty("fieldViolations", fieldViolations);
        }
    }

    private void completeProblem(
            ProblemDetail problem, HttpStatusCode statusCode, WebRequest request) {
        if (StringUtils.isBlank(problem.getTitle())) {
            problem.setTitle(title(statusCode));
        }
        if (StringUtils.isBlank(problem.getDetail())) {
            problem.setDetail(title(statusCode));
        }
        Map<String, Object> properties = problem.getProperties();
        if (properties == null || !properties.containsKey("code")) {
            problem.setProperty("code", canonicalCode(statusCode));
        }
        if (properties == null || !properties.containsKey("reason")) {
            problem.setProperty("reason", canonicalCode(statusCode) + "_ERROR");
        }
        withTraceAndPath(problem, request);
    }

    private void withTraceAndPath(ProblemDetail problem, WebRequest request) {
        String traceId = MDC.get(TraceIdFilter.TRACE_ID);
        if (StringUtils.isNotBlank(traceId)) {
            problem.setProperty("traceId", traceId);
        }
        requestPath(request).ifPresent(path -> problem.setProperty("path", path));
    }

    private Optional<String> requestPath(WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            return Optional.of(servletWebRequest.getRequest().getRequestURI());
        }
        String description = request.getDescription(false);
        if (description.startsWith("uri=")) {
            return Optional.of(description.substring("uri=".length()));
        }
        return Optional.empty();
    }

    private ApiFieldViolation fieldViolation(FieldError fieldError) {
        return new ApiFieldViolation(
                fieldError.getField(),
                StringUtils.defaultIfBlank(fieldError.getDefaultMessage(), "字段不合法"));
    }

    private String parameterName(
            org.springframework.validation.method.ParameterValidationResult result) {
        String parameterName = result.getMethodParameter().getParameterName();
        if (StringUtils.isNotBlank(parameterName)) {
            return parameterName;
        }
        return "arg" + result.getMethodParameter().getParameterIndex();
    }

    private String reason(BadRequestException exception) {
        if (!exception.fieldViolations().isEmpty()) {
            return "FIELD_VIOLATION";
        }
        return "INVALID_ARGUMENT_ERROR";
    }

    private String canonicalCode(HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        if (status == null) {
            return "UNKNOWN";
        }
        return switch (status) {
            case BAD_REQUEST -> "INVALID_ARGUMENT";
            case UNAUTHORIZED -> "UNAUTHENTICATED";
            case FORBIDDEN -> "PERMISSION_DENIED";
            case NOT_FOUND -> "NOT_FOUND";
            case CONFLICT -> "ALREADY_EXISTS";
            case TOO_MANY_REQUESTS -> "RESOURCE_EXHAUSTED";
            case NOT_IMPLEMENTED -> "UNIMPLEMENTED";
            case SERVICE_UNAVAILABLE -> "UNAVAILABLE";
            case GATEWAY_TIMEOUT -> "DEADLINE_EXCEEDED";
            default -> status.is5xxServerError() ? "INTERNAL" : "FAILED_PRECONDITION";
        };
    }

    private String title(HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        if (status == null) {
            return "请求处理失败";
        }
        return switch (status) {
            case BAD_REQUEST -> "请求参数无效";
            case UNAUTHORIZED -> "未认证";
            case FORBIDDEN -> "无访问权限";
            case NOT_FOUND -> "资源不存在";
            case CONFLICT -> "资源状态冲突";
            case TOO_MANY_REQUESTS -> "请求过于频繁";
            default -> status.is5xxServerError() ? "服务处理失败" : "请求处理失败";
        };
    }
}
