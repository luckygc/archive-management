package github.luckygc.am.module.archive.authorization;

import java.util.List;

import github.luckygc.am.module.archive.item.ArchiveItemFilterOperator;

public record ArchiveDataScopeDynamicCondition(List<DynamicFieldCondition> dynamicFields) {

    public record DynamicFieldCondition(
            Long categoryId,
            String fieldCode,
            ArchiveItemFilterOperator operator,
            List<String> values) {}
}
