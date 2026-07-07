package github.luckygc.am.common.api;

import java.util.List;
import java.util.function.Function;

import jakarta.data.page.CursoredPage;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class CursorPageResponse<T> {

    @JsonProperty("items")
    private final List<T> items;

    @JsonProperty("self")
    private final @Nullable String self;

    @JsonProperty("prev")
    private final @Nullable String prev;

    @JsonProperty("next")
    private final @Nullable String next;

    @JsonProperty("first")
    private final @Nullable String first;

    @JsonProperty("total")
    private final @Nullable Long total;

    @JsonIgnore private final transient int limit;

    @JsonIgnore private final transient @Nullable List<?> selfValues;

    @JsonIgnore private final transient @Nullable List<?> prevValues;

    @JsonIgnore private final transient @Nullable List<?> nextValues;

    @JsonIgnore private final transient @Nullable List<?> firstValues;

    public CursorPageResponse(
            List<T> items,
            @Nullable String self,
            @Nullable String prev,
            @Nullable String next,
            @Nullable String first,
            @Nullable Long total) {
        this(items, self, prev, next, first, total, 0, null, null, null, null);
    }

    private CursorPageResponse(
            List<T> items,
            @Nullable String self,
            @Nullable String prev,
            @Nullable String next,
            @Nullable String first,
            @Nullable Long total,
            int limit,
            @Nullable List<?> selfValues,
            @Nullable List<?> prevValues,
            @Nullable List<?> nextValues,
            @Nullable List<?> firstValues) {
        this.items = List.copyOf(items);
        this.self = self;
        this.prev = prev;
        this.next = next;
        this.first = first;
        this.total = total;
        this.limit = limit;
        this.selfValues = copyValues(selfValues);
        this.prevValues = copyValues(prevValues);
        this.nextValues = copyValues(nextValues);
        this.firstValues = copyValues(firstValues);
    }

    public static <S, T> CursorPageResponse<T> from(
            CursoredPage<S> page, CursorPageRequest request, Function<S, T> mapper) {
        return withCursorValues(
                page.content().stream().map(mapper).toList(),
                request.limit(),
                page.numberOfElements() == 0 ? null : page.cursor(0).elements(),
                page.hasPrevious()
                        ? page.previousPageRequest().cursor().orElseThrow().elements()
                        : null,
                page.hasNext() ? page.nextPageRequest().cursor().orElseThrow().elements() : null,
                null,
                page.hasTotals() ? page.totalElements() : null);
    }

    public static <T> CursorPageResponse<T> withCursorValues(
            List<T> items,
            int limit,
            @Nullable List<?> selfValues,
            @Nullable List<?> prevValues,
            @Nullable List<?> nextValues,
            @Nullable List<?> firstValues,
            @Nullable Long total) {
        return new CursorPageResponse<>(
                items,
                null,
                null,
                null,
                null,
                total,
                limit,
                selfValues,
                prevValues,
                nextValues,
                firstValues);
    }

    public CursorPageResponse<T> encodeCursorTokens(CursorPageTokenContext context) {
        if (limit <= 0) {
            return this;
        }
        return new CursorPageResponse<>(
                items,
                CursorPageTokenCodec.encode("self", selfValues, limit, context),
                CursorPageTokenCodec.encode("prev", prevValues, limit, context),
                CursorPageTokenCodec.encode("next", nextValues, limit, context),
                CursorPageTokenCodec.encode("first", firstValues, limit, context),
                total);
    }

    public List<T> items() {
        return items;
    }

    public @Nullable String self() {
        return self;
    }

    public @Nullable String prev() {
        return prev;
    }

    public @Nullable String next() {
        return next;
    }

    public @Nullable String first() {
        return first;
    }

    public @Nullable Long total() {
        return total;
    }

    @JsonIgnore
    public int limit() {
        return limit;
    }

    @JsonIgnore
    public @Nullable List<?> selfValues() {
        return selfValues;
    }

    @JsonIgnore
    public @Nullable List<?> prevValues() {
        return prevValues;
    }

    @JsonIgnore
    public @Nullable List<?> nextValues() {
        return nextValues;
    }

    @JsonIgnore
    public @Nullable List<?> firstValues() {
        return firstValues;
    }

    private static @Nullable List<?> copyValues(@Nullable List<?> values) {
        return values == null ? null : List.copyOf(values);
    }
}
