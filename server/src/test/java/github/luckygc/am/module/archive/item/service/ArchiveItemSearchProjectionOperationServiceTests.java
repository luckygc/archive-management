package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import jakarta.data.page.CursoredPage;
import jakarta.data.page.PageRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.item.ArchiveItemSearchProjectionRebuildJob;
import github.luckygc.am.module.archive.item.ArchiveItemSearchProjectionRebuildJobStatus;
import github.luckygc.am.module.archive.item.repository.ArchiveItemSearchProjectionRebuildJobDataRepository;

import tools.jackson.databind.json.JsonMapper;

@DisplayName("搜索投影重建操作监视")
class ArchiveItemSearchProjectionOperationServiceTests {

    private final ArchiveItemSearchProjectionRebuildJobDataRepository repository =
            mock(ArchiveItemSearchProjectionRebuildJobDataRepository.class);
    private final ArchiveItemSearchProjectionOperationService service =
            new ArchiveItemSearchProjectionOperationService(repository);

    @Test
    @DisplayName("只有发起人能查询任务，完成状态包含结果")
    void getChecksOwnerAndMapsResult() {
        ArchiveItemSearchProjectionRebuildJob job = job(17L, 9L);
        job.setStatus(ArchiveItemSearchProjectionRebuildJobStatus.SUCCEEDED);
        job.setProcessedCount(250);
        when(repository.findById(17L)).thenReturn(Optional.of(job));

        var monitor = service.get("archive-search-projection-rebuild-17", 9L);

        assertThat(monitor.id()).isEqualTo("archive-search-projection-rebuild-17");
        assertThat(monitor.status()).isEqualTo("Succeeded");
        assertThat(monitor.kind()).isEqualTo("archiveSearchProjectionRebuild");
        assertThat(monitor.result())
                .containsEntry("categoryId", 3L)
                .containsEntry("rebuiltCount", 250);
        assertThatThrownBy(() -> service.get("archive-search-projection-rebuild-17", 10L))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception ->
                                assertThat(exception.getStatusCode())
                                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("排队和失败状态遵守 Azure 监视资源格式")
    void mapsPendingAndFailedStatuses() {
        ArchiveItemSearchProjectionRebuildJob job = job(17L, 9L);
        when(repository.findById(17L)).thenReturn(Optional.of(job));

        var pending = service.get("archive-search-projection-rebuild-17", 9L);
        assertThat(pending.status()).isEqualTo("NotStarted");
        assertThat(JsonMapper.builder().build().writeValueAsString(pending))
                .doesNotContain("\"error\"", "\"result\"");

        job.setStatus(ArchiveItemSearchProjectionRebuildJobStatus.FAILED);
        job.setErrorCode("SEARCH_REBUILD_FAILED");
        job.setErrorMessage("重建失败");
        var failed = service.get("archive-search-projection-rebuild-17", 9L);
        assertThat(failed.status()).isEqualTo("Failed");
        assertThat(failed.error().code()).isEqualTo("SEARCH_REBUILD_FAILED");
        assertThat(failed.error().message()).isEqualTo("重建失败");
        assertThat(failed.result()).isNull();

        job.setErrorCode(null);
        job.setErrorMessage(null);
        var incompleteFailure = service.get("archive-search-projection-rebuild-17", 9L);
        assertThat(incompleteFailure.error().code()).isEqualTo("SEARCH_REBUILD_FAILED");
        assertThat(incompleteFailure.error().message()).isNotBlank();
    }

    @Test
    @DisplayName("任务列表按当前用户过滤且不计算总数")
    void listsOnlyCurrentUsersJobs() {
        @SuppressWarnings("unchecked")
        CursoredPage<ArchiveItemSearchProjectionRebuildJob> page = mock(CursoredPage.class);
        when(page.content()).thenReturn(List.of(job(17L, 9L)));
        when(repository.findByRequestedBy(org.mockito.ArgumentMatchers.eq(9L), any()))
                .thenReturn(page);

        var response = service.list(9L, 20, null);

        assertThat(response.items())
                .extracting(item -> item.id())
                .containsExactly("archive-search-projection-rebuild-17");
        assertThat(response.nextCursor()).isNull();
        verify(repository)
                .findByRequestedBy(
                        org.mockito.ArgumentMatchers.eq(9L),
                        org.mockito.ArgumentMatchers.argThat(
                                request -> request.size() == 20 && !request.requestTotal()));
    }

    @Test
    @DisplayName("列表游标绑定发起人和分页大小")
    void listCursorCannotBeReusedByAnotherUser() {
        @SuppressWarnings("unchecked")
        CursoredPage<ArchiveItemSearchProjectionRebuildJob> page = mock(CursoredPage.class);
        when(page.content()).thenReturn(List.of(job(17L, 9L)));
        when(page.hasNext()).thenReturn(true);
        when(page.nextPageRequest())
                .thenReturn(PageRequest.ofSize(20).afterCursor(PageRequest.Cursor.forKey(17L)));
        when(repository.findByRequestedBy(org.mockito.ArgumentMatchers.eq(9L), any()))
                .thenReturn(page);

        String cursor = service.list(9L, 20, null).nextCursor();

        assertThat(cursor).isNotBlank();
        assertThatThrownBy(() -> service.list(10L, 20, cursor))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.list(9L, 50, cursor))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("无效操作 ID、分页大小和游标被拒绝")
    void rejectsInvalidInputs() {
        assertThatThrownBy(() -> service.get("other-17", 9L))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.list(9L, 0, null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.list(9L, 101, null))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.list(9L, 20, "invalid"))
                .isInstanceOf(BadRequestException.class);
    }

    private ArchiveItemSearchProjectionRebuildJob job(Long id, Long requestedBy) {
        ArchiveItemSearchProjectionRebuildJob job = new ArchiveItemSearchProjectionRebuildJob();
        job.setId(id);
        job.setCategoryId(3L);
        job.setRequestedBy(requestedBy);
        job.setStatus(ArchiveItemSearchProjectionRebuildJobStatus.QUEUED);
        return job;
    }
}
