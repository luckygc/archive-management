package github.luckygc.am.module.archive.rule.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeAction;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeActionDecision;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeActionType;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeTriggerPoint;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeFieldCatalogService.ArchiveRuntimeField;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeFieldCatalogService.ArchiveRuntimeFieldCatalog;

@Component
public class ArchiveRuntimeSetFieldActionHandler implements ArchiveRuntimeActionHandler {

    @Override
    public ArchiveRuntimeActionType actionType() {
        return ArchiveRuntimeActionType.SET_FIELD;
    }

    @Override
    public void validate(
            ArchiveRuntimeAction action,
            ArchiveRuntimeTriggerPoint triggerPoint,
            ArchiveRuntimeFieldCatalog fieldCatalog) {
        if (!triggerPoint.fieldAssignmentAllowed()) {
            throw new BadRequestException("该触发点不允许 SET_FIELD 动作");
        }
        String fieldCode = fieldCode(action);
        ArchiveRuntimeField field = fieldCatalog.fieldsByCode().get(fieldCode);
        if (field == null || !field.writable()) {
            throw new BadRequestException(
                    "SET_FIELD 目标字段不存在或不可写：" + fieldCode, "actions.params.field", "目标字段不存在或不可写");
        }
        if (!action.getActionParams().containsKey("value")
                || action.getActionParams().size() != 2) {
            throw new BadRequestException("SET_FIELD 只允许 field 和 value 参数");
        }
        new ArchiveRuntimeActionExecutionContext(
                        "publish-validation",
                        fieldCatalog,
                        ArchiveRuntimeActionExecutionContext.mutableFacts(Map.of()),
                        new java.util.LinkedHashMap<>())
                .setField(fieldCode, action.getActionParams().get("value"));
    }

    @Override
    public ArchiveRuntimeActionDecision execute(
            ArchiveRuntimeAction action, ArchiveRuntimeActionExecutionContext context) {
        context.setField(fieldCode(action), action.getActionParams().get("value"));
        return new ArchiveRuntimeActionDecision(
                ArchiveRuntimeActionType.SET_FIELD,
                Collections.unmodifiableMap(new LinkedHashMap<>(action.getActionParams())));
    }

    private String fieldCode(ArchiveRuntimeAction action) {
        Object raw = action.getActionParams().get("field");
        String fieldCode = raw instanceof String text ? StringUtils.trimToNull(text) : null;
        if (fieldCode == null) {
            throw new BadRequestException("SET_FIELD 必须提供目标字段", "actions.params.field", "目标字段不能为空");
        }
        return fieldCode;
    }
}
