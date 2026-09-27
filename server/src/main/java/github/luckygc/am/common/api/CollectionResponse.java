package github.luckygc.am.common.api;

import java.util.List;

public record CollectionResponse<T>(List<T> items) {

    public static <T> CollectionResponse<T> of(List<T> items) {
        return new CollectionResponse<>(List.copyOf(items));
    }
}
