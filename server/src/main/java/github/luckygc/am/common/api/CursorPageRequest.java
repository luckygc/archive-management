package github.luckygc.am.common.api;

import jakarta.data.page.PageRequest;

import org.jspecify.annotations.Nullable;

public record CursorPageRequest(
        int limit,
        @Nullable String cursor,
        boolean requestTotal,
        CursorPageTokenContext context,
        PageRequest pageRequest) {

    public static CursorPageRequest of(
            int limit,
            @Nullable String cursor,
            boolean requestTotal,
            CursorPageTokenContext context) {
        return new CursorPageRequest(
                limit,
                cursor,
                requestTotal,
                context,
                CursorPageTokenCodec.pageRequest(limit, cursor, requestTotal));
    }
}
