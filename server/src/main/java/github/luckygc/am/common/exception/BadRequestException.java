package github.luckygc.am.common.exception;

import java.util.List;

import github.luckygc.am.common.api.ApiFieldViolation;

public class BadRequestException extends RuntimeException {

    private final List<ApiFieldViolation> fieldViolations;

    public BadRequestException(String message) {
        this(message, List.of());
    }

    public BadRequestException(String message, String field, String description) {
        this(message, List.of(new ApiFieldViolation(field, description)));
    }

    public BadRequestException(String message, List<ApiFieldViolation> fieldViolations) {
        super(message);
        this.fieldViolations = List.copyOf(fieldViolations);
    }

    public List<ApiFieldViolation> fieldViolations() {
        return fieldViolations;
    }
}
