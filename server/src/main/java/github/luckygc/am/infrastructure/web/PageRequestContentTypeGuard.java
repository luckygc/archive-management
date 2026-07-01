package github.luckygc.am.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;

import github.luckygc.am.common.exception.BadRequestException;

final class PageRequestContentTypeGuard {

    private PageRequestContentTypeGuard() {}

    static void rejectMultipart(HttpServletRequest request) {
        if (StringUtils.startsWithIgnoreCase(request.getContentType(), "multipart/")) {
            throw new BadRequestException(
                    "分页参数不合法", "pagination", "multipart 请求不能携带分页参数，请使用 JSON 请求体或 URL 参数");
        }
    }
}
