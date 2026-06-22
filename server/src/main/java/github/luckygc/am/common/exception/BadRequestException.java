package github.luckygc.am.common.exception;

import java.util.List;

import github.luckygc.am.common.api.ApiErrorResponse;

public class BadRequestException extends RuntimeException {

    private final List<ApiErrorResponse.FieldViolation> fieldViolations;

    public BadRequestException(String message) {
        this(message, List.of());
    }

    public BadRequestException(String message, String field, String description) {
        this(message, List.of(new ApiErrorResponse.FieldViolation(field, description)));
    }

    public BadRequestException(
            String message, List<ApiErrorResponse.FieldViolation> fieldViolations) {
        super(message);
        this.fieldViolations = List.copyOf(fieldViolations);
    }

    public List<ApiErrorResponse.FieldViolation> fieldViolations() {
        return fieldViolations;
    }
}
