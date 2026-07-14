package github.luckygc.am.module.archive.item.service;

import java.util.List;

import org.jspecify.annotations.Nullable;

import github.luckygc.am.module.archive.item.ArchiveItem;
import github.luckygc.am.module.archive.item.service.ArchiveItemImportExportService.ArchiveImportRowError;

final class ArchiveImportRow {

    private final int rowNumber;
    private final ArchiveItemCommandService.CreateArchiveItemRequest createRequest;
    private final List<ArchiveImportRowError> parseErrors;
    private @Nullable ArchiveItem existingItem;

    ArchiveImportRow(
            int rowNumber,
            ArchiveItemCommandService.CreateArchiveItemRequest createRequest,
            @Nullable ArchiveItem existingItem,
            List<ArchiveImportRowError> parseErrors) {
        this.rowNumber = rowNumber;
        this.createRequest = createRequest;
        this.existingItem = existingItem;
        this.parseErrors = parseErrors;
    }

    int rowNumber() {
        return rowNumber;
    }

    ArchiveItemCommandService.CreateArchiveItemRequest createRequest() {
        return createRequest;
    }

    ArchiveItemCommandService.UpdateArchiveItemRequest updateRequest() {
        return new ArchiveItemCommandService.UpdateArchiveItemRequest(
                createRequest.volumeId(),
                createRequest.fondsCode(),
                createRequest.archiveNo(),
                createRequest.archiveYear(),
                createRequest.electronicStatus(),
                createRequest.securityLevelId(),
                createRequest.retentionPeriodId(),
                createRequest.physicalFields(),
                createRequest.dynamicFields());
    }

    @Nullable ArchiveItem existingItem() {
        return existingItem;
    }

    List<ArchiveImportRowError> parseErrors() {
        return parseErrors;
    }

    void bindExistingItem(@Nullable ArchiveItem existingItem) {
        this.existingItem = existingItem;
    }
}
