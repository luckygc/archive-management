package github.luckygc.am.module.archive.authorization;

import java.util.List;

import github.luckygc.am.module.archive.item.ArchiveItemQueryOperator;

public record ArchiveDataScopeDynamicCondition(List<DynamicFieldCondition> dynamicFields) {

    public record DynamicFieldCondition(
            Long categoryId,
            String fieldCode,
            ArchiveItemQueryOperator operator,
            List<String> values) {}
}
