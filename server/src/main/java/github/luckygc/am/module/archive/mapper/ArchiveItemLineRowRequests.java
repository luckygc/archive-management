package github.luckygc.am.module.archive.mapper;

import java.util.List;

import org.jspecify.annotations.Nullable;

public final class ArchiveItemLineRowRequests {

    private ArchiveItemLineRowRequests() {}

    public record ArchiveItemLineRowPageRequest(
            String tableName,
            Long itemId,
            List<String> selectColumns,
            boolean previous,
            @Nullable Integer cursorLineOrder,
            @Nullable Long cursorId,
            boolean requestTotal,
            int rowLimit) {}

    public record ArchiveItemLineRowLookupRequest(
            String tableName, Long itemId, Long rowId, List<String> selectColumns) {}

    public record ArchiveItemLineRowProjectionRequest(
            String tableName, Long itemId, List<String> selectColumns) {}

    public record ArchiveItemLineRowInsertRequest(
            String tableName, Long itemId, int lineOrder, List<ArchiveSqlAssignment> assignments) {}

    public record ArchiveItemLineRowUpdateRequest(
            String tableName,
            Long itemId,
            Long rowId,
            boolean lineOrderPresent,
            @Nullable Integer lineOrder,
            List<ArchiveSqlAssignment> assignments) {}

    public record ArchiveItemLineRowDeleteRequest(
            String tableName, Long itemId, Long rowId, Long userId) {}
}
