package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.module.archive.item.ArchiveItemSearchProjectionRebuildJob;
import github.luckygc.am.module.archive.item.ArchiveItemSearchProjectionRebuildJobStatus;
import github.luckygc.am.module.archive.item.repository.ArchiveItemSearchProjectionRebuildJobDataRepository;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.metadata.ArchiveManagementMode;
import github.luckygc.am.module.archive.metadata.ArchiveTableStatus;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryDto;

@DisplayName("搜索投影重建任务服务")
class ArchiveItemSearchProjectionRebuildServiceTests {

    private final ArchiveCategoryService categoryService = mock(ArchiveCategoryService.class);
    private final ArchiveMapper archiveMapper = mock(ArchiveMapper.class);
    private final ArchiveItemSearchProjectionRebuildJobDataRepository repository =
            mock(ArchiveItemSearchProjectionRebuildJobDataRepository.class);
    private final ArchiveItemSearchProjectionRebuildService service =
            new ArchiveItemSearchProjectionRebuildService(
                    categoryService, archiveMapper, repository);

    @Test
    @DisplayName("启动任务冻结待重建记录上界并返回任务资源")
    void startCreatesQueuedJob() {
        when(categoryService.getCategory(3L)).thenReturn(category());
        when(archiveMapper.tableExists("am_archive_item_contract")).thenReturn(1);
        when(archiveMapper.getMaxItemIdForSearchRebuild("am_archive_item_contract"))
                .thenReturn(250L);
        when(archiveMapper.countItemsForSearchRebuild("am_archive_item_contract", 250L))
                .thenReturn(250);
        when(repository.insert(any()))
                .thenAnswer(
                        invocation -> {
                            ArchiveItemSearchProjectionRebuildJob job = invocation.getArgument(0);
                            job.setId(17L);
                            return job;
                        });

        var response = service.start(3L, 9L);

        assertThat(response.jobId()).isEqualTo(17L);
        assertThat(response.status()).isEqualTo("queued");
        assertThat(response.operationLocation())
                .isEqualTo("/api/v1/archive-search-projection-rebuild-jobs/17");
        verify(repository)
                .insert(
                        org.mockito.ArgumentMatchers.argThat(
                                job ->
                                        job.getCategoryId().equals(3L)
                                                && job.getRequestedBy().equals(9L)
                                                && job.getStatus()
                                                        == ArchiveItemSearchProjectionRebuildJobStatus
                                                                .QUEUED
                                                && job.getTotalCount() == 250
                                                && job.getMaxItemId().equals(250L)));
    }

    @Test
    @DisplayName("成功任务返回进度和结果摘要")
    void getReturnsSucceededJobStatus() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 9, 12, 0);
        ArchiveItemSearchProjectionRebuildJob job = new ArchiveItemSearchProjectionRebuildJob();
        job.setId(17L);
        job.setCategoryId(3L);
        job.setStatus(ArchiveItemSearchProjectionRebuildJobStatus.SUCCEEDED);
        job.setTotalCount(250);
        job.setProcessedCount(250);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        when(repository.findById(17L)).thenReturn(Optional.of(job));

        var response = service.get(17L);

        assertThat(response.status()).isEqualTo("succeeded");
        assertThat(response.progress()).isEqualTo(100);
        assertThat(response.result())
                .containsEntry("categoryId", 3L)
                .containsEntry("rebuiltCount", 250);
        assertThat(response.errorCode()).isNull();
    }

    private static ArchiveCategoryDto category() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 9, 12, 0);
        return new ArchiveCategoryDto(
                3L,
                null,
                "contract",
                "合同档案",
                ArchiveManagementMode.ITEM_ONLY,
                null,
                "am_archive_item_contract",
                null,
                null,
                ArchiveTableStatus.BUILT,
                now,
                true,
                0,
                now,
                now);
    }
}
