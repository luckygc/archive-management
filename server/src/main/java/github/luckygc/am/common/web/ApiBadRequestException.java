package github.luckygc.am.common.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class ApiBadRequestException extends ResponseStatusException {

    private final List<ApiErrorResponse.FieldViolation> fieldViolations;

    public ApiBadRequestException(String message) {
        this(message, List.of());
    }

    public ApiBadRequestException(String message, String field, String description) {
        this(message, List.of(new ApiErrorResponse.FieldViolation(field, description)));
    }

    public ApiBadRequestException(String message, List<ApiErrorResponse.FieldViolation> fieldViolations) {
        super(HttpStatus.BAD_REQUEST, message);
        this.fieldViolations = List.copyOf(fieldViolations);
    }

    public List<ApiErrorResponse.FieldViolation> fieldViolations() {
        return fieldViolations;
    }
}
