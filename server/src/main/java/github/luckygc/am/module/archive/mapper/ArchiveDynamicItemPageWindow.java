package github.luckygc.am.module.archive.mapper;

import java.util.List;

import org.jspecify.annotations.Nullable;

import github.luckygc.am.module.archive.mapper.ArchiveSqlOrder.Direction;

public record ArchiveDynamicItemPageWindow(
        List<ArchiveSqlOrder> orders, List<CursorPredicate> cursorPredicates, int limit) {

    public ArchiveDynamicItemPageWindow {
        orders = List.copyOf(orders);
        cursorPredicates = List.copyOf(cursorPredicates);
    }

    public record CursorPredicate(
            List<CursorComparison> equalsComparisons, CursorComparison rangeComparison) {

        public CursorPredicate {
            equalsComparisons = List.copyOf(equalsComparisons);
        }
    }

    public record CursorComparison(String expression, Direction direction, @Nullable Object value) {

        public String operatorName() {
            return direction == Direction.ASC ? "GT" : "LT";
        }
    }
}
