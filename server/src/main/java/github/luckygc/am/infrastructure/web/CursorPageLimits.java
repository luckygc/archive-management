package github.luckygc.am.infrastructure.web;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

import github.luckygc.am.common.exception.BadRequestException;

final class CursorPageLimits {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1000;

    private CursorPageLimits() {}

    static int parse(@Nullable String value) {
        if (StringUtils.isBlank(value)) {
            return DEFAULT_LIMIT;
        }
        try {
            int limit = Integer.parseInt(value);
            if (limit <= 0) {
                throw new NumberFormatException("limit must be positive");
            }
            if (limit > MAX_LIMIT) {
                throw new BadRequestException("分页参数不合法", "limit", "limit 不能大于 1000");
            }
            return limit;
        } catch (NumberFormatException exception) {
            throw new BadRequestException("分页参数不合法", "limit", "limit 必须为正整数");
        }
    }
}
