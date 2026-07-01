package github.luckygc.am.common.api;

import java.util.List;
import java.util.function.Function;

import jakarta.data.page.CursoredPage;

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

    public CursorPageResponse(CursoredPage<T> page) {
        this(
                page.content(),
                CursorPageTokenCodec.self(page),
                CursorPageTokenCodec.previous(page),
                CursorPageTokenCodec.next(page),
                null,
                page.hasTotals() ? page.totalElements() : null);
    }

    public static <S, T> CursorPageResponse<T> from(CursoredPage<S> page, Function<S, T> mapper) {
        return new CursorPageResponse<>(
                page.content().stream().map(mapper).toList(),
                CursorPageTokenCodec.self(page),
                CursorPageTokenCodec.previous(page),
                CursorPageTokenCodec.next(page),
                null,
                page.hasTotals() ? page.totalElements() : null);
    }
}
