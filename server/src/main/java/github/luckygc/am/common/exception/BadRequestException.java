package github.luckygc.am.common.exception;

import java.io.Serial;
import java.util.List;

import org.jspecify.annotations.Nullable;

import github.luckygc.am.common.api.ApiFieldViolation;

public class BadRequestException extends RuntimeException {

    @Serial private static final long serialVersionUID = -1L;

    private final List<ApiFieldViolation> fieldViolations;
    private final @Nullable String errorCode;
    private final @Nullable String reason;

    public BadRequestException(String message) {
        this(message, List.of());
    }

    public BadRequestException(String message, String field, String description) {
        this(message, List.of(new ApiFieldViolation(field, description)));
    }

    public BadRequestException(String message, List<ApiFieldViolation> fieldViolations) {
        this(message, fieldViolations, null, null);
    }

    public BadRequestException(
            String message,
            List<ApiFieldViolation> fieldViolations,
            @Nullable String errorCode,
            @Nullable String reason) {
        super(message);
        this.fieldViolations = List.copyOf(fieldViolations);
        this.errorCode = errorCode;
        this.reason = reason;
    }

    public List<ApiFieldViolation> fieldViolations() {
        return fieldViolations;
    }

    public @Nullable String errorCode() {
        return errorCode;
    }

    public @Nullable String reason() {
        return reason;
    }
}
