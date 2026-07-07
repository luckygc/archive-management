package github.luckygc.am.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;

import github.luckygc.am.common.exception.BadRequestException;

final class PageRequestContentTypeGuard {

    private PageRequestContentTypeGuard() {}

    static void rejectUnsupportedBodyContentType(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (StringUtils.isBlank(contentType)) {
            return;
        }
        if (StringUtils.startsWithIgnoreCase(contentType, "multipart/")) {
            throw new BadRequestException(
                    "分页参数不合法", "pagination", "multipart 请求不能携带分页参数，请使用 URL 参数");
        }
        try {
            MediaType mediaType = MediaType.parseMediaType(contentType);
            if (MediaType.APPLICATION_JSON.includes(mediaType)
                    || mediaType.getSubtype().endsWith("+json")) {
                return;
            }
        } catch (InvalidMediaTypeException exception) {
            throw unsupportedBodyType();
        }
        throw unsupportedBodyType();
    }

    private static BadRequestException unsupportedBodyType() {
        return new BadRequestException("分页参数不合法", "pagination", "分页请求体只支持 JSON");
    }
}
