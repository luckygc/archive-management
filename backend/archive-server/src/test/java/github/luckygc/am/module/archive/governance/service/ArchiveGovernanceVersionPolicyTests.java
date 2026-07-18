package github.luckygc.am.module.archive.governance.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.governance.ArchiveGovernanceSchemeVersionStatus;

@DisplayName("档案治理方案版本策略")
class ArchiveGovernanceVersionPolicyTests {

    @Test
    @DisplayName("拒绝修改已发布治理方案版本")
    void requireEditableShouldRejectPublishedVersion() {
        assertThatThrownBy(
                        () ->
                                ArchiveGovernanceVersionPolicy.requireEditable(
                                        ArchiveGovernanceSchemeVersionStatus.PUBLISHED))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("已发布或冻结版本不可原地修改");
    }

    @Test
    @DisplayName("拒绝将冻结版本作为新档案默认版本")
    void requireUsableForNewArchiveShouldRejectFrozenVersion() {
        assertThatThrownBy(
                        () ->
                                ArchiveGovernanceVersionPolicy.requireUsableForNewArchive(
                                        ArchiveGovernanceSchemeVersionStatus.FROZEN))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("治理方案版本不可用于新建档案");
    }
}
