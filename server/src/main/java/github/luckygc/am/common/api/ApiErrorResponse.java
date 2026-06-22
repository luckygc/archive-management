package github.luckygc.am.common.api;

import java.util.List;
import java.util.Map;

public record ApiErrorResponse(Error error) {

    public record Error(
            int code, String message, String status, List<Map<String, Object>> details) {}

    public record FieldViolation(String field, String description) {}
}
