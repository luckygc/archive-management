package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.LongStream;

import org.apache.fesod.sheet.FesodSheet;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;

import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService;
import github.luckygc.am.module.archive.item.ArchiveItem;
import github.luckygc.am.module.archive.item.repository.ArchiveItemAuditDataRepository;
import github.luckygc.am.module.archive.item.repository.ArchiveItemDataRepository;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataReferenceService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeExecutionService;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeExecutionService.ArchiveRuntimeExecutionRequest;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeTraceService;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;
import github.luckygc.am.module.storage.FileLinkTargetType;
import github.luckygc.am.module.storage.service.FileLinkService;
import github.luckygc.am.module.storage.service.StorageObjectService;
import github.luckygc.am.module.storage.service.StorageObjectService.StorageObjectDto;

@DisplayName("档案导出策略批量读取")
class ArchiveExportBatchPolicyTests {

    private static final long USER_ID = 9L;

    private final Map<Long, ArchiveItem> storedItems = new LinkedHashMap<>();
    private final List<List<Long>> repositoryReads = new ArrayList<>();
    private ArchiveItemSearchService searchService;
    private ArchiveItemAuditDataRepository auditRepository;
    private StorageObjectService storageObjectService;
    private FileLinkService fileLinkService;
    private ArchiveRuntimeExecutionService runtimeExecutionService;
    private ArchiveRuntimeTraceService runtimeTraceService;
    private ArchiveItemImportExportService importExportService;

    @BeforeEach
    void setUp() {
        searchService = mock(ArchiveItemSearchService.class);
        auditRepository = mock(ArchiveItemAuditDataRepository.class);
        storageObjectService = mock(StorageObjectService.class);
        fileLinkService = mock(FileLinkService.class);
        runtimeExecutionService = ArchiveRuntimeTestSupport.passthroughExecutionService();
        runtimeTraceService = ArchiveRuntimeTestSupport.traceService();
        AuthorizationPermissionService permissionService =
                mock(AuthorizationPermissionService.class);
        when(permissionService.hasPermission(USER_ID, "archive:export")).thenReturn(true);
        ArchiveItemDataRepository itemRepository =
                mock(
                        ArchiveItemDataRepository.class,
                        invocation -> {
                            if (invocation.getMethod().getName().equals("findById")) {
                                Long id = invocation.getArgument(0);
                                repositoryReads.add(List.of(id));
                                return Optional.ofNullable(storedItems.get(id));
                            }
                            if (invocation.getMethod().getName().equals("findByIdIn")) {
                                List<Long> ids = invocation.getArgument(0);
                                repositoryReads.add(List.copyOf(ids));
                                List<ArchiveItem> result = new ArrayList<>();
                                for (Long id : ids) {
                                    ArchiveItem item = storedItems.get(id);
                                    if (item != null) {
                                        result.add(item);
                                    }
                                }
                                Collections.reverse(result);
                                return result;
                            }
                            return Answers.RETURNS_DEFAULTS.answer(invocation);
                        });
        importExportService =
                new ArchiveItemImportExportService(
                        mock(ArchiveMetadataService.class),
                        new ArchiveItemFieldValueConverter(),
                        mock(ArchiveMetadataReferenceService.class),
                        mock(ArchiveCategoryService.class),
                        mock(ArchiveItemService.class),
                        searchService,
                        permissionService,
                        mock(ArchiveDataScopeService.class),
                        itemRepository,
                        auditRepository,
                        storageObjectService,
                        fileLinkService,
                        Clock.fixed(Instant.parse("2026-07-15T10:00:00Z"), ZoneOffset.UTC),
                        runtimeExecutionService,
                        runtimeTraceService);
    }

    @Test
    @DisplayName("跨页导出使用有界读取且数据库返回顺序不改变导出行和规则范围顺序")
    void multiPageExportUsesBoundedReadsAndPreservesSourceOrder() {
        List<Map<String, @Nullable Object>> rows =
                LongStream.rangeClosed(1, 2501).mapToObj(this::storeExportRow).toList();
        stubPages(rows);
        stubStoredDownload();

        var result = importExportService.createExportDownloadLink(null, USER_ID);

        assertThat(result.code()).isEqualTo("export-code");
        assertThat(repositoryReads).hasSize(3);
        assertThat(repositoryReads).allSatisfy(ids -> assertThat(ids.size()).isBetween(1, 1000));
        assertThat(repositoryReads.stream().flatMap(List::stream).toList())
                .containsExactlyElementsOf(LongStream.rangeClosed(1, 2501).boxed().toList());
        var requests = ArgumentCaptor.forClass(ArchiveRuntimeExecutionRequest.class);
        verify(runtimeExecutionService, org.mockito.Mockito.times(2)).enforce(requests.capture());
        assertThat(requests.getAllValues())
                .extracting(ArchiveRuntimeExecutionRequest::fondsCode)
                .containsExactly("F001", "F002");
        assertThat(requests.getAllValues())
                .allSatisfy(
                        request ->
                                assertThat(request.candidateFacts())
                                        .containsEntry("export.itemCount", 2501));
        var file = ArgumentCaptor.forClass(StorageObjectService.StoreStorageObjectRequest.class);
        verify(storageObjectService).storeObject(file.capture(), eq(USER_ID));
        List<Map<Integer, String>> exportedRows =
                FesodSheet.read(file.getValue().inputStream())
                        .headRowNumber(1)
                        .sheet()
                        .doReadSync();
        assertThat(exportedRows).hasSize(2501);
        assertThat(exportedRows)
                .extracting(row -> row.get(5))
                .containsExactlyElementsOf(
                        LongStream.rangeClosed(1, 2501).mapToObj(id -> "A-" + id).toList());
    }

    @Test
    @DisplayName("导出记录缺失按原行顺序报告第一项且不产生导出副作用")
    void missingItemReportsFirstSourceIdBeforeAnyExportSideEffects() {
        Map<String, @Nullable Object> first = storeExportRow(9);
        Map<String, @Nullable Object> second = storeExportRow(8);
        storedItems.clear();
        stubPages(List.of(first, second));

        assertThatThrownBy(() -> importExportService.createExportDownloadLink(null, USER_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("导出档案条目不存在：9");

        verifyNoInteractions(
                runtimeExecutionService,
                runtimeTraceService,
                auditRepository,
                storageObjectService,
                fileLinkService);
    }

    @Test
    @DisplayName("前一行记录缺失时不被后一行非法 ID 的错误覆盖")
    void earlierMissingItemTakesPrecedenceOverLaterMalformedId() {
        Map<String, @Nullable Object> missing = storeExportRow(9);
        storedItems.clear();
        Map<String, @Nullable Object> malformed = Map.of("id", "invalid");
        stubPages(List.of(missing, malformed));

        assertThatThrownBy(() -> importExportService.createExportDownloadLink(null, USER_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("导出档案条目不存在：9");

        verifyNoInteractions(
                runtimeExecutionService,
                runtimeTraceService,
                auditRepository,
                storageObjectService,
                fileLinkService);
    }

    private Map<String, @Nullable Object> storeExportRow(long id) {
        String fondsCode = id % 2 == 1 ? "F001" : "F002";
        ArchiveItem item = new ArchiveItem();
        item.setId(id);
        item.setFondsCode(fondsCode);
        item.setCategoryCode("CONTRACT");
        storedItems.put(id, item);
        return Map.of(
                "id",
                id,
                "fondsCode",
                fondsCode,
                "fondsName",
                "全宗",
                "categoryCode",
                "CONTRACT",
                "categoryName",
                "合同",
                "archiveNo",
                "A-" + id,
                "archiveYear",
                2026);
    }

    private void stubPages(List<Map<String, @Nullable Object>> rows) {
        List<ArchiveItemSearchService.ArchiveItemListDto> pages = new ArrayList<>();
        for (int start = 0; start < rows.size(); start += 1000) {
            int end = Math.min(start + 1000, rows.size());
            List<?> next = end < rows.size() ? List.of((long) end) : null;
            pages.add(
                    new ArchiveItemSearchService.ArchiveItemListDto(
                            null,
                            List.of(),
                            CursorPageResponse.withCursorValues(
                                    rows.subList(start, end), 1000, null, null, next, null, null)));
        }
        var pageIndex = new java.util.concurrent.atomic.AtomicInteger();
        when(searchService.searchItems(any(), eq(USER_ID)))
                .thenAnswer(invocation -> pages.get(pageIndex.getAndIncrement()));
    }

    private void stubStoredDownload() {
        when(storageObjectService.storeObject(any(), eq(USER_ID)))
                .thenReturn(
                        new StorageObjectDto(
                                20L, "archive", "key", "export.xlsx", 3, null, null, USER_ID));
        when(fileLinkService.createUserLinkUntil(
                        FileLinkTargetType.STORAGE_OBJECT,
                        null,
                        20L,
                        LocalDateTime.of(2026, 7, 15, 10, 10),
                        USER_ID))
                .thenReturn(
                        new FileLinkService.FileLinkCreated(
                                "export-code", LocalDateTime.of(2026, 7, 15, 10, 10)));
    }
}
