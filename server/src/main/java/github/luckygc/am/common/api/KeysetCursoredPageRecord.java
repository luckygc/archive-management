package github.luckygc.am.common.api;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import jakarta.data.page.CursoredPage;
import jakarta.data.page.PageRequest;

import org.jspecify.annotations.Nullable;

public record KeysetCursoredPageRecord<T>(
        PageRequest pageRequest,
        List<T> content,
        List<PageRequest.Cursor> cursors,
        boolean hasPrevious,
        boolean hasNext,
        @Nullable Long total)
        implements CursoredPage<T> {

    public KeysetCursoredPageRecord {
        Objects.requireNonNull(pageRequest, "pageRequest");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(cursors, "cursors");
        content = List.copyOf(content);
        cursors = List.copyOf(cursors);
        if (content.size() != cursors.size()) {
            throw new IllegalArgumentException("分页内容数量必须和 cursor 数量一致");
        }
    }

    @Override
    public PageRequest.Cursor cursor(int index) {
        return cursors.get(index);
    }

    @Override
    public PageRequest nextPageRequest() {
        if (!hasNext) {
            throw new NoSuchElementException("没有下一页");
        }
        return pageRequest.afterCursor(cursors.getLast()).withoutTotal();
    }

    @Override
    public PageRequest previousPageRequest() {
        if (!hasPrevious) {
            throw new NoSuchElementException("没有上一页");
        }
        return pageRequest.beforeCursor(cursors.getFirst()).withoutTotal();
    }

    @Override
    public boolean hasContent() {
        return !content.isEmpty();
    }

    @Override
    public int numberOfElements() {
        return content.size();
    }

    @Override
    public boolean hasTotals() {
        return total != null;
    }

    @Override
    public long totalElements() {
        if (total == null) {
            throw new IllegalStateException("当前分页未请求总数");
        }
        return total;
    }

    @Override
    public long totalPages() {
        long totalElements = totalElements();
        if (totalElements == 0) {
            return 0;
        }
        return (totalElements + pageRequest.size() - 1) / pageRequest.size();
    }

    @Override
    public Iterator<T> iterator() {
        return content.iterator();
    }
}
