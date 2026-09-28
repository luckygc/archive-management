package github.luckygc.am.module.archive.item.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import jakarta.data.page.CursoredPage;
import jakarta.data.page.PageRequest;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.annotation.JsonInclude;

import github.luckygc.am.common.api.CursorPageTokenCodec;
import github.luckygc.am.common.api.CursorPageTokenContext;
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.item.ArchiveItemSearchProjectionRebuildJob;
import github.luckygc.am.module.archive.item.ArchiveItemSearchProjectionRebuildJobStatus;
import github.luckygc.am.module.archive.item.repository.ArchiveItemSearchProjectionRebuildJobDataRepository;

@Service
public class ArchiveItemSearchProjectionOperationService {

    private static final String OPERATION_PREFIX = "archive-search-projection-rebuild-";
    private static final String OPERATION_KIND = "archiveSearchProjectionRebuild";
    private static final int MAX_PAGE_SIZE = 100;

    private final ArchiveItemSearchProjectionRebuildJobDataRepository repository;

    public ArchiveItemSearchProjectionOperationService(
            ArchiveItemSearchProjectionRebuildJobDataRepository repository) {
        this.repository = repository;
    }

    public static String operationId(Long jobId) {
        return OPERATION_PREFIX + jobId;
    }

    @Transactional(readOnly = true)
    public OperationMonitor get(String operationId, Long userId) {
        Long jobId = jobId(operationId);
        ArchiveItemSearchProjectionRebuildJob job =
                repository
                        .findById(jobId)
                        .orElseThrow(ArchiveItemSearchProjectionOperationService::notFound);
        if (!userId.equals(job.getRequestedBy())) {
            throw notFound();
        }
        return toMonitor(job);
    }

    @Transactional(readOnly = true)
    public OperationPage list(Long userId, int limit, @Nullable String cursor) {
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new BadRequestException("limit 必须在 1 到 100 之间", "limit", "limit 必须在 1 到 100 之间");
        }
        CursorPageTokenContext context = cursorContext(userId);
        PageRequest pageRequest = CursorPageTokenCodec.pageRequest(limit, cursor, false, context);
        CursoredPage<ArchiveItemSearchProjectionRebuildJob> page =
                repository.findByRequestedBy(userId, pageRequest);
        return new OperationPage(
                page.content().stream().map(this::toMonitor).toList(),
                page.hasPrevious()
                        ? CursorPageTokenCodec.encode(
                                "prev",
                                page.previousPageRequest().cursor().orElseThrow(),
                                limit,
                                context)
                        : null,
                page.hasNext()
                        ? CursorPageTokenCodec.encode(
                                "next",
                                page.nextPageRequest().cursor().orElseThrow(),
                                limit,
                                context)
                        : null);
    }

    private CursorPageTokenContext cursorContext(Long userId) {
        return new CursorPageTokenContext("operations:requestedBy=" + userId);
    }

    private Long jobId(String operationId) {
        if (!operationId.startsWith(OPERATION_PREFIX)) {
            throw notFound();
        }
        try {
            Long id = Long.valueOf(operationId.substring(OPERATION_PREFIX.length()));
            if (id <= 0) {
                throw notFound();
            }
            return id;
        } catch (NumberFormatException exception) {
            throw notFound();
        }
    }

    private OperationMonitor toMonitor(ArchiveItemSearchProjectionRebuildJob job) {
        return new OperationMonitor(
                operationId(job.getId()),
                status(job.getStatus()),
                OPERATION_KIND,
                job.getStatus() == ArchiveItemSearchProjectionRebuildJobStatus.FAILED
                        ? new OperationError(
                                Objects.requireNonNullElse(
                                        job.getErrorCode(), "SEARCH_REBUILD_FAILED"),
                                Objects.requireNonNullElse(job.getErrorMessage(), "搜索投影重建失败"))
                        : null,
                job.getStatus() == ArchiveItemSearchProjectionRebuildJobStatus.SUCCEEDED
                        ? Map.of(
                                "categoryId",
                                job.getCategoryId(),
                                "rebuiltCount",
                                job.getProcessedCount())
                        : null);
    }

    private String status(ArchiveItemSearchProjectionRebuildJobStatus status) {
        return switch (status) {
            case QUEUED -> "NotStarted";
            case RUNNING -> "Running";
            case SUCCEEDED -> "Succeeded";
            case FAILED -> "Failed";
            case CANCELLED -> "Canceled";
        };
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "操作不存在");
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OperationMonitor(
            String id,
            String status,
            String kind,
            @Nullable OperationError error,
            @Nullable Map<String, Object> result) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OperationError(String code, String message) {}

    public record OperationPage(
            List<OperationMonitor> items,
            @Nullable String prevCursor,
            @Nullable String nextCursor) {}
}
