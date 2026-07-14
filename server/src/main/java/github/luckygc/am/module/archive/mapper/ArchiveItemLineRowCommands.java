package github.luckygc.am.module.archive.mapper;

import java.util.List;

import org.jspecify.annotations.Nullable;

public final class ArchiveItemLineRowCommands {

    private ArchiveItemLineRowCommands() {}

    public record ArchiveItemLineRowPageQuery(
            String tableName,
            Long itemId,
            List<String> selectColumns,
            boolean previous,
            @Nullable Integer cursorLineOrder,
            @Nullable Long cursorId,
            int rowLimit) {}

    public record ArchiveItemLineRowLookup(
            String tableName, Long itemId, Long rowId, List<String> selectColumns) {}

    public record ArchiveItemLineRowProjectionQuery(
            String tableName, Long itemId, List<String> selectColumns) {}

    public record ArchiveItemLineRowInsertCommand(
            String tableName, Long itemId, int lineOrder, List<ArchiveSqlAssignment> assignments) {}

    public record ArchiveItemLineRowUpdateCommand(
            String tableName,
            Long itemId,
            Long rowId,
            boolean lineOrderPresent,
            @Nullable Integer lineOrder,
            List<ArchiveSqlAssignment> assignments) {}

    public record ArchiveItemLineRowDeleteCommand(
            String tableName, Long itemId, Long rowId, Long userId) {}
}
