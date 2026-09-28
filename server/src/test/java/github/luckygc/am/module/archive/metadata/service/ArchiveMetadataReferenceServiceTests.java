package github.luckygc.am.module.archive.metadata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.metadata.ArchiveRetentionPeriod;
import github.luckygc.am.module.archive.metadata.ArchiveSecurityLevel;
import github.luckygc.am.module.archive.metadata.repository.ArchiveFondsDataRepository;
import github.luckygc.am.module.archive.metadata.repository.ArchiveRetentionPeriodDataRepository;
import github.luckygc.am.module.archive.metadata.repository.ArchiveSecurityLevelDataRepository;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.UpdateArchiveRetentionPeriodRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.UpdateArchiveSecurityLevelRequest;

@DisplayName("档案元数据参照值服务")
class ArchiveMetadataReferenceServiceTests {

    private final ArchiveSecurityLevelDataRepository securityRepository =
            mock(ArchiveSecurityLevelDataRepository.class);
    private final ArchiveRetentionPeriodDataRepository retentionRepository =
            mock(ArchiveRetentionPeriodDataRepository.class);
    private final ArchiveMetadataReferenceService service =
            new ArchiveMetadataReferenceService(
                    mock(ArchiveFondsDataRepository.class),
                    securityRepository,
                    retentionRepository);

    @Test
    @DisplayName("密级名称空补丁不写库，提交名称后仅更改名称")
    void securityLevelPatchPreservesAndUpdatesName() {
        ArchiveSecurityLevel level = new ArchiveSecurityLevel();
        level.setId(3L);
        level.setLevelName("内部");
        level.setEnabled(true);
        when(securityRepository.findById(3L)).thenReturn(Optional.of(level));
        when(securityRepository.update(level)).thenReturn(level);

        assertThat(
                        service.updateSecurityLevel(3L, new UpdateArchiveSecurityLevelRequest(null))
                                .levelName())
                .isEqualTo("内部");
        verify(securityRepository, never()).update(any());

        assertThat(
                        service.updateSecurityLevel(
                                        3L, new UpdateArchiveSecurityLevelRequest(" 秘密 "))
                                .levelName())
                .isEqualTo("秘密");
        assertThat(level.isEnabled()).isTrue();
        verify(securityRepository).update(level);
    }

    @Test
    @DisplayName("保管期限空补丁不写库，空白名称拒绝更新")
    void retentionPeriodPatchPreservesNameAndRejectsBlank() {
        ArchiveRetentionPeriod period = new ArchiveRetentionPeriod();
        period.setId(4L);
        period.setPeriodName("十年");
        when(retentionRepository.findById(4L)).thenReturn(Optional.of(period));

        assertThat(
                        service.updateRetentionPeriod(
                                        4L, new UpdateArchiveRetentionPeriodRequest(null))
                                .periodName())
                .isEqualTo("十年");
        assertThatThrownBy(
                        () ->
                                service.updateRetentionPeriod(
                                        4L, new UpdateArchiveRetentionPeriodRequest("   ")))
                .isInstanceOf(BadRequestException.class);
        verify(retentionRepository, never()).update(any());
    }
}
