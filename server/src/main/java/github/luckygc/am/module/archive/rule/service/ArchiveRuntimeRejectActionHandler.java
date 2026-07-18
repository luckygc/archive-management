package github.luckygc.am.module.archive.rule.service;

import java.util.Collections;
import java.util.LinkedHashMap;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeAction;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeActionDecision;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeActionType;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeTriggerPoint;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeFieldCatalogService.ArchiveRuntimeFieldCatalog;

@Component
public class ArchiveRuntimeRejectActionHandler implements ArchiveRuntimeActionHandler {

    @Override
    public ArchiveRuntimeActionType actionType() {
        return ArchiveRuntimeActionType.REJECT;
    }

    @Override
    public void validate(
            ArchiveRuntimeAction action,
            ArchiveRuntimeTriggerPoint triggerPoint,
            ArchiveRuntimeFieldCatalog fieldCatalog) {
        requireMessage(action);
    }

    @Override
    public ArchiveRuntimeActionDecision execute(
            ArchiveRuntimeAction action, ArchiveRuntimeActionExecutionContext context) {
        return new ArchiveRuntimeActionDecision(
                ArchiveRuntimeActionType.REJECT,
                Collections.unmodifiableMap(new LinkedHashMap<>(action.getActionParams())));
    }

    static String requireMessage(ArchiveRuntimeAction action) {
        Object raw = action.getActionParams().get("message");
        String message = raw instanceof String text ? StringUtils.trimToNull(text) : null;
        if (message == null || message.length() > 1000) {
            throw new BadRequestException(
                    "REJECT/WARN 动作必须提供不超过 1000 字的消息",
                    "actions.params.message",
                    "消息不能为空且长度不能超过 1000");
        }
        if (action.getActionParams().size() != 1) {
            throw new BadRequestException("REJECT/WARN 动作包含未知参数");
        }
        return message;
    }
}
