package github.luckygc.am.module.archive.governance.service;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.governance.ArchiveGovernanceSchemeVersionStatus;

public final class ArchiveGovernanceVersionPolicy {

    private ArchiveGovernanceVersionPolicy() {}

    public static void requireEditable(ArchiveGovernanceSchemeVersionStatus status) {
        if (status != ArchiveGovernanceSchemeVersionStatus.DRAFT) {
            throw new BadRequestException("已发布或冻结版本不可原地修改");
        }
    }

    public static void requireUsableForNewArchive(ArchiveGovernanceSchemeVersionStatus status) {
        if (status != ArchiveGovernanceSchemeVersionStatus.PUBLISHED) {
            throw new BadRequestException("治理方案版本不可用于新建档案");
        }
    }
}
