package github.luckygc.am.common.api;

import java.util.List;

public record OffsetPageResponse<T>(List<T> items, int limit, long offset, long total) {

    public OffsetPageResponse {
        items = List.copyOf(items);
    }
}
