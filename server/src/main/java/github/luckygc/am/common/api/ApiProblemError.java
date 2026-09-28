package github.luckygc.am.common.api;

public record ApiProblemError(String detail, String pointer) {

    public static ApiProblemError from(ApiFieldViolation violation) {
        String field = violation.field();
        if (field.isBlank()) {
            return new ApiProblemError(violation.message(), "");
        }
        String path = field.replaceAll("\\[(\\d+)]", ".$1");
        StringBuilder pointer = new StringBuilder();
        for (String segment : path.split("\\.")) {
            pointer.append('/').append(segment.replace("~", "~0").replace("/", "~1"));
        }
        return new ApiProblemError(violation.message(), pointer.toString());
    }
}
