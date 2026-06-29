package github.luckygc.am.common.api;

import java.util.List;

import org.jspecify.annotations.Nullable;

public record CursorPageResponse<T>(
        List<T> items,
        @Nullable String self,
        @Nullable String prev,
        @Nullable String next,
        @Nullable String first,
        @Nullable Long total) {

    public CursorPageResponse {
        items = List.copyOf(items);
    }
}
