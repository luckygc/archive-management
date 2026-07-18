package github.luckygc.am.module.archive.rule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.metadata.ArchiveFieldDataType;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeAction;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeActionType;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeFieldSource;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeTriggerPoint;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeFieldCatalogService.ArchiveRuntimeField;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeFieldCatalogService.ArchiveRuntimeFieldCatalog;

@DisplayName("运行时固定动作处理器")
class ArchiveRuntimeActionHandlerTests {

    private final ArchiveRuntimeSetFieldActionHandler handler =
            new ArchiveRuntimeSetFieldActionHandler();

    @Test
    @DisplayName("SET_FIELD 只修改内存候选值并完成类型转换")
    void setFieldMutatesOnlyCandidateFacts() {
        ArchiveRuntimeAction action = action("item.archiveYear", "2026");
        ArchiveRuntimeFieldCatalog catalog = catalog(true);
        Map<String, Object> candidate = new LinkedHashMap<>();
        var context =
                new ArchiveRuntimeActionExecutionContext(
                        "set-year", catalog, candidate, new LinkedHashMap<>());

        handler.validate(action, ArchiveRuntimeTriggerPoint.ITEM_BEFORE_CREATE, catalog);
        handler.execute(action, context);

        assertThat(candidate).containsEntry("item.archiveYear", 2026L);
        assertThat(context.assignments()).containsKey("item.archiveYear");
    }

    @Test
    @DisplayName("删除触发点和不可写字段拒绝 SET_FIELD")
    void setFieldRejectsIncompatibleTriggerAndReadOnlyField() {
        ArchiveRuntimeAction action = action("item.archiveYear", 2026);

        assertThatThrownBy(
                        () ->
                                handler.validate(
                                        action,
                                        ArchiveRuntimeTriggerPoint.ITEM_BEFORE_DELETE,
                                        catalog(true)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(
                        () ->
                                handler.validate(
                                        action,
                                        ArchiveRuntimeTriggerPoint.ITEM_BEFORE_UPDATE,
                                        catalog(false)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("同字段不同值冲突而相同值幂等")
    void assignmentsDetectConflictAndAllowIdempotency() {
        Map<String, Object> candidate = new LinkedHashMap<>();
        Map<String, ArchiveRuntimeActionExecutionContext.FieldAssignment> assignments =
                new LinkedHashMap<>();
        var first =
                new ArchiveRuntimeActionExecutionContext(
                        "first", catalog(true), candidate, assignments);
        var second =
                new ArchiveRuntimeActionExecutionContext(
                        "second", catalog(true), candidate, assignments);

        first.setField("item.archiveYear", 2026);
        second.setField("item.archiveYear", "2026");
        assertThatThrownBy(() -> second.setField("item.archiveYear", 2027))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("first")
                .hasMessageContaining("second");
    }

    private ArchiveRuntimeAction action(String field, Object value) {
        ArchiveRuntimeAction action = new ArchiveRuntimeAction();
        action.setActionType(ArchiveRuntimeActionType.SET_FIELD);
        action.setActionParams(Map.of("field", field, "value", value));
        return action;
    }

    private ArchiveRuntimeFieldCatalog catalog(boolean writable) {
        ArchiveRuntimeField field =
                new ArchiveRuntimeField(
                        "item.archiveYear",
                        "归档年度",
                        ArchiveFieldDataType.INTEGER,
                        ArchiveRuntimeFieldSource.FIXED,
                        true,
                        writable,
                        null);
        return new ArchiveRuntimeFieldCatalog(
                1L,
                null,
                ArchiveRuntimeTriggerPoint.ITEM_BEFORE_UPDATE,
                "signature",
                List.of(field));
    }
}
