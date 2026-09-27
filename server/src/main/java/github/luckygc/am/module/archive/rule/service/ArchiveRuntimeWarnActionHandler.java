package github.luckygc.am.module.archive.rule.service;

import java.util.Collections;
import java.util.LinkedHashMap;

import org.springframework.stereotype.Component;

import github.luckygc.am.module.archive.rule.ArchiveRuntimeAction;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeActionDecision;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeActionType;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeTriggerPoint;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeFieldCatalogService.ArchiveRuntimeFieldCatalog;

@Component
public class ArchiveRuntimeWarnActionHandler implements ArchiveRuntimeActionHandler {

    @Override
    public ArchiveRuntimeActionType actionType() {
        return ArchiveRuntimeActionType.WARN;
    }

    @Override
    public void validate(
            ArchiveRuntimeAction action,
            ArchiveRuntimeTriggerPoint triggerPoint,
            ArchiveRuntimeFieldCatalog fieldCatalog) {
        ArchiveRuntimeRejectActionHandler.requireMessage(action);
    }

    @Override
    public ArchiveRuntimeActionDecision execute(
            ArchiveRuntimeAction action, ArchiveRuntimeActionExecutionContext context) {
        return new ArchiveRuntimeActionDecision(
                ArchiveRuntimeActionType.WARN,
                Collections.unmodifiableMap(new LinkedHashMap<>(action.getActionParams())));
    }
}
