package github.luckygc.am.module.archive.rule.service;

import github.luckygc.am.module.archive.rule.ArchiveRuntimeAction;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeActionDecision;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeActionType;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeTriggerPoint;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeFieldCatalogService.ArchiveRuntimeFieldCatalog;

public interface ArchiveRuntimeActionHandler {

    ArchiveRuntimeActionType actionType();

    void validate(
            ArchiveRuntimeAction action,
            ArchiveRuntimeTriggerPoint triggerPoint,
            ArchiveRuntimeFieldCatalog fieldCatalog);

    ArchiveRuntimeActionDecision execute(
            ArchiveRuntimeAction action, ArchiveRuntimeActionExecutionContext context);
}
