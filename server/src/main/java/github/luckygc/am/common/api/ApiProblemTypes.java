package github.luckygc.am.common.api;

import java.net.URI;
import java.util.Locale;

public final class ApiProblemTypes {

    private static final String DOCUMENT_URL =
            "https://github.com/luckygc/archive-management/blob/main/docs/api-problems.md#";

    private ApiProblemTypes() {}

    public static URI fromCode(String code) {
        return URI.create(DOCUMENT_URL + code.toLowerCase(Locale.ROOT).replace('_', '-'));
    }
}
