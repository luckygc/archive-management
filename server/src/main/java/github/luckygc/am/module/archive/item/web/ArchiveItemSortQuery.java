package github.luckygc.am.module.archive.item.web;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.item.service.ArchiveItemSearchService.ArchiveItemOrderByRequest;

final class ArchiveItemSortQuery {

    private ArchiveItemSortQuery() {}

    static @Nullable List<ArchiveItemOrderByRequest> parse(@Nullable String sort) {
        if (sort == null) {
            return null;
        }
        List<ArchiveItemOrderByRequest> orders = new ArrayList<>();
        for (String term : sort.split(",", -1)) {
            if (term.length() < 2 || (term.charAt(0) != '+' && term.charAt(0) != '-')) {
                throw invalidSort();
            }
            String field = term.substring(1);
            if (field.isBlank() || !field.equals(field.trim())) {
                throw invalidSort();
            }
            orders.add(
                    new ArchiveItemOrderByRequest(field, term.charAt(0) == '+' ? "ASC" : "DESC"));
        }
        return orders;
    }

    private static BadRequestException invalidSort() {
        return new BadRequestException("排序参数格式不正确", "sort", "使用 +field,-field 格式");
    }
}
