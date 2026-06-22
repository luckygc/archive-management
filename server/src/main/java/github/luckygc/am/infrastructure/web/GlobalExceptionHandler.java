package github.luckygc.am.infrastructure.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.api.ApiErrorResponse;
import github.luckygc.am.common.exception.BadRequestException;

@RestControllerAdvice
class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BadRequestException.class)
    ResponseEntity<ApiErrorResponse> handleBadRequestException(
            BadRequestException exception, HttpServletRequest request) {
        String message = StringUtils.defaultIfBlank(exception.getMessage(), "请求参数不合法");
        List<Map<String, Object>> details =
                details(reason(exception), request, exception.fieldViolations());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error(HttpStatus.BAD_REQUEST.value(), message, "INVALID_ARGUMENT", details));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiErrorResponse> handleResponseStatusException(
            ResponseStatusException exception, HttpServletRequest request) {
        HttpStatusCode statusCode = exception.getStatusCode();
        String message = StringUtils.defaultIfBlank(exception.getReason(), "请求处理失败");
        String status = canonicalStatus(statusCode);
        List<Map<String, Object>> details = details(status + "_ERROR", request, List.of());
        return ResponseEntity.status(statusCode)
                .body(error(statusCode.value(), message, status, details));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleException(
            Exception exception, HttpServletRequest request) {
        log.error("未处理的接口异常", exception);
        List<Map<String, Object>> details = details("UNHANDLED_EXCEPTION", request, List.of());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                        error(
                                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                                "服务处理失败，请稍后重试",
                                "INTERNAL",
                                details));
    }

    private ApiErrorResponse error(
            int code, String message, String status, List<Map<String, Object>> details) {
        return new ApiErrorResponse(new ApiErrorResponse.Error(code, message, status, details));
    }

    private List<Map<String, Object>> details(
            String reason,
            HttpServletRequest request,
            List<ApiErrorResponse.FieldViolation> fieldViolations) {
        Map<String, Object> errorInfo = new LinkedHashMap<>();
        errorInfo.put("@type", "type.googleapis.com/google.rpc.ErrorInfo");
        errorInfo.put("reason", reason);
        errorInfo.put("domain", "archive-management");
        errorInfo.put("metadata", metadata(request));

        if (fieldViolations.isEmpty()) {
            return List.of(errorInfo);
        }

        Map<String, Object> badRequest = new LinkedHashMap<>();
        badRequest.put("@type", "type.googleapis.com/google.rpc.BadRequest");
        badRequest.put("fieldViolations", fieldViolations);
        return List.of(errorInfo, badRequest);
    }

    private Map<String, String> metadata(HttpServletRequest request) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("path", request.getRequestURI());
        String traceId = MDC.get(TraceIdFilter.TRACE_ID);
        if (StringUtils.isNotBlank(traceId)) {
            metadata.put("traceId", traceId);
        }
        return metadata;
    }

    private String reason(BadRequestException exception) {
        if (!exception.fieldViolations().isEmpty()) {
            return "FIELD_VIOLATION";
        }
        return "INVALID_ARGUMENT_ERROR";
    }

    private String canonicalStatus(HttpStatusCode statusCode) {
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
}
