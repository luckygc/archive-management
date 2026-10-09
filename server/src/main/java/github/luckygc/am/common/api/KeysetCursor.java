package github.luckygc.am.common.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.data.page.PageRequest;

import org.jspecify.annotations.Nullable;

/** MyBatis 键集分页使用的不可变游标，保留可空排序键的位置。 */
public record KeysetCursor(List<@Nullable Object> elements) implements PageRequest.Cursor {
    public KeysetCursor {
        elements = Collections.unmodifiableList(new ArrayList<>(elements));
    }

    @Override
    public @Nullable Object get(int index) {
        return elements.get(index);
    }

    @Override
    public int size() {
        return elements.size();
    }

    @Override
    public String toString() {
        return "KeysetCursor[size=" + elements.size() + "]";
    }
}
