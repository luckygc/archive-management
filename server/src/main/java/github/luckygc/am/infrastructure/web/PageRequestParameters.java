package github.luckygc.am.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

final class PageRequestParameters {

    private final HttpServletRequest request;

    private PageRequestParameters(HttpServletRequest request) {
        this.request = request;
    }

    static PageRequestParameters from(HttpServletRequest request) {
        return new PageRequestParameters(request);
    }

    @Nullable String value(String name) {
        return StringUtils.trimToNull(request.getParameter(name));
    }
}
