package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.apache.fesod.sheet.FesodSheet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeResolutionTypes.ArchiveDataScopeFilter;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService;
import github.luckygc.am.module.archive.item.ArchiveItem;
import github.luckygc.am.module.archive.item.ArchiveItemAudit;
import github.luckygc.am.module.archive.item.repository.ArchiveItemAuditDataRepository;
import github.luckygc.am.module.archive.item.repository.ArchiveItemDataRepository;
import github.luckygc.am.module.archive.item.service.ArchiveItemImportExportService.ArchiveImportResult;
import github.luckygc.am.module.archive.item.service.ArchiveItemQueryService.ArchiveItemListDto;
import github.luckygc.am.module.archive.item.service.ArchiveItemQueryService.SearchArchiveItemsRequest;
import github.luckygc.am.module.archive.metadata.ArchiveFieldControl;
import github.luckygc.am.module.archive.metadata.ArchiveFieldScope;
import github.luckygc.am.module.archive.metadata.ArchiveFieldType;
import github.luckygc.am.module.archive.metadata.ArchiveManagementMode;
import github.luckygc.am.module.archive.metadata.ArchiveTableStatus;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataReferenceService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFieldDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFondsDto;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;
import github.luckygc.am.module.storage.FileLink;
import github.luckygc.am.module.storage.FileLinkTargetType;
import github.luckygc.am.module.storage.repository.FileLinkDataRepository;
import github.luckygc.am.module.storage.service.FileLinkCodeGenerator;
import github.luckygc.am.module.storage.service.FileLinkService;
import github.luckygc.am.module.storage.service.StorageObjectService;
import github.luckygc.am.module.storage.service.StorageObjectService.StorageObjectDto;

@DisplayName("档案 Excel 导入导出")
class ArchiveItemImportExportServiceTests {

    private ArchiveMetadataService archiveMetadataService;
    private ArchiveMetadataReferenceService archiveMetadataReferenceService;
    private ArchiveCategoryService archiveCategoryService;
    private ArchiveItemCommandService archiveItemRoutingService;
    private ArchiveItemQueryService archiveItemQueryService;
    private AuthorizationPermissionService permissionService;
    private ArchiveDataScopeService dataScopeService;
    private ArchiveItemDataRepository archiveItemRepository;
    private ArchiveItemAuditDataRepository auditRepository;
    private StorageObjectService storageObjectService;
    private FileLinkService fileLinkService;
    private Clock clock;
    private ArchiveItemImportExportService importExportService;

    @BeforeEach
    void setUp() {
        archiveMetadataService = mock(ArchiveMetadataService.class);
        archiveMetadataReferenceService = mock(ArchiveMetadataReferenceService.class);
        archiveCategoryService = mock(ArchiveCategoryService.class);
        archiveItemRoutingService = mock(ArchiveItemCommandService.class);
        archiveItemQueryService = mock(ArchiveItemQueryService.class);
        permissionService = mock(AuthorizationPermissionService.class);
        dataScopeService = mock(ArchiveDataScopeService.class);
        archiveItemRepository = mock(ArchiveItemDataRepository.class);
        auditRepository = mock(ArchiveItemAuditDataRepository.class);
        storageObjectService = mock(StorageObjectService.class);
        fileLinkService = mock(FileLinkService.class);
        clock = Clock.fixed(Instant.parse("2026-07-15T10:00:00Z"), ZoneOffset.UTC);
        importExportService =
                new ArchiveItemImportExportService(
                        archiveMetadataService,
                        archiveMetadataReferenceService,
                        archiveCategoryService,
                        archiveItemRoutingService,
                        archiveItemQueryService,
                        permissionService,
                        dataScopeService,
                        archiveItemRepository,
                        auditRepository,
                        storageObjectService,
                        fileLinkService,
                        clock);
    }

    @Test
    @DisplayName("导入模板不需要独立导入权限，有创建或编辑权限即可生成")
    void createImportTemplateDownloadLinkShouldStoreTemporaryObjectAndCreateUserLink() {
        when(permissionService.hasPermission(9L, "archive:item:create")).thenReturn(false);
        when(permissionService.hasPermission(9L, "archive:item:update")).thenReturn(true);
        when(dataScopeService.buildItemFilter(9L, 1L, null))
                .thenReturn(ArchiveDataScopeFilter.all());
        when(archiveCategoryService.getCategory(1L)).thenReturn(category());
        when(archiveMetadataService.listEnabledFields(1L, ArchiveLevel.ITEM))
                .thenReturn(List.of(textField()));

        when(storageObjectService.storeObject(any(), eq(9L)))
                .thenReturn(
                        new StorageObjectDto(
                                20L, "archive", "key", "template.xlsx", 3, null, null, 9L));
        when(fileLinkService.createUserLinkUntil(
                        FileLinkTargetType.STORAGE_OBJECT,
                        null,
                        20L,
                        LocalDateTime.of(2026, 7, 15, 10, 10),
                        9L))
                .thenReturn(
                        new FileLinkService.FileLinkCreated(
                                "template-code", LocalDateTime.of(2026, 7, 15, 10, 10)));

        var result = importExportService.createImportTemplateDownloadLink(1L, 9L);

        assertThat(result.code()).isEqualTo("template-code");
        assertThat(result.expiresAt()).isEqualTo(LocalDateTime.of(2026, 7, 15, 10, 10));
        var commandCaptor =
                org.mockito.ArgumentCaptor.forClass(
                        StorageObjectService.StoreStorageObjectCommand.class);
        verify(storageObjectService).storeObject(commandCaptor.capture(), eq(9L));
        assertThat(commandCaptor.getValue().originalFilename())
                .contains("archive-import-template-contract");
        assertThat(commandCaptor.getValue().contentLength()).isPositive();
        assertThat(commandCaptor.getValue().inputStream()).isInstanceOf(ByteArrayInputStream.class);
        assertThat(commandCaptor.getValue().expiresAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 15, 10, 10));
        verify(fileLinkService)
                .createUserLinkUntil(
                        FileLinkTargetType.STORAGE_OBJECT,
                        null,
                        20L,
                        LocalDateTime.of(2026, 7, 15, 10, 10),
                        9L);
    }

    @Test
    @DisplayName("导入时按档号是否存在分别走创建和更新")
    void importItemsShouldCreateNewRowsAndUpdateExistingRows() throws IOException {
        when(permissionService.hasPermission(anyLong(), eq("archive:item:create")))
                .thenReturn(true);
        when(permissionService.hasPermission(anyLong(), eq("archive:item:update")))
                .thenReturn(true);
        when(dataScopeService.buildItemFilter(9L, 1L, null))
                .thenReturn(ArchiveDataScopeFilter.all());
        when(dataScopeService.buildItemFilter(9L, 1L, "F001"))
                .thenReturn(ArchiveDataScopeFilter.all());
        when(archiveCategoryService.getCategory(1L)).thenReturn(category());
        when(archiveMetadataService.listEnabledFields(1L, ArchiveLevel.ITEM))
                .thenReturn(List.of(textField()));
        when(archiveMetadataReferenceService.getEnabledFondsByCode("F001")).thenReturn(fonds());
        when(archiveItemRepository.findByArchiveNo("contract", "A-002")).thenReturn(existingItem());

        ArchiveImportResult result =
                importExportService.importItems(
                        1L,
                        new ByteArrayInputStream(
                                workbookBytes(
                                        List.of(
                                                List.of("F001", "A-001", 2026, "DRAFT", "新题名"),
                                                List.of("F001", "A-002", 2026, "DRAFT", "旧题名")))),
                        9L);

        assertThat(result.errors()).isEmpty();
        assertThat(result.importedCount()).isEqualTo(2);
        verify(archiveItemRoutingService).createItem(any(), eq(9L));
        verify(archiveItemRoutingService).updateItem(eq(20L), any(), eq(9L));
    }

    @Test
    @DisplayName("导入新行缺少创建权限时返回逐行错误且不写入")
    void importItemsShouldReturnRowErrorWhenCreatePermissionMissing() throws IOException {
        when(permissionService.hasPermission(9L, "archive:item:create")).thenReturn(false);
        when(permissionService.hasPermission(9L, "archive:item:update")).thenReturn(true);
        when(dataScopeService.buildItemFilter(9L, 1L, null))
                .thenReturn(ArchiveDataScopeFilter.all());
        when(dataScopeService.buildItemFilter(9L, 1L, "F001"))
                .thenReturn(ArchiveDataScopeFilter.all());
        when(archiveCategoryService.getCategory(1L)).thenReturn(category());
        when(archiveMetadataService.listEnabledFields(1L, ArchiveLevel.ITEM))
                .thenReturn(List.of(textField()));
        when(archiveMetadataReferenceService.getEnabledFondsByCode("F001")).thenReturn(fonds());

        ArchiveImportResult result =
                importExportService.importItems(
                        1L,
                        new ByteArrayInputStream(
                                workbookBytes(
                                        List.of(List.of("F001", "A-001", 2026, "DRAFT", "题名")))),
                        9L);

        assertThat(result.importedCount()).isZero();
        assertThat(result.errors())
                .extracting(ArchiveItemImportExportService.ArchiveImportRowError::message)
                .contains("缺少创建权限");
    }

    @Test
    @DisplayName("导出使用当前查询并写入导出审计")
    void createExportDownloadLinkShouldUseSearchWriteAuditAndCreateUserLink() {
        when(permissionService.hasPermission(9L, "archive:export")).thenReturn(true);
        SearchArchiveItemsRequest request =
                new SearchArchiveItemsRequest(1L, "F001", null, null, null, 100, null, null);
        when(archiveItemQueryService.searchItems(any(), eq(9L)))
                .thenReturn(
                        new ArchiveItemListDto(
                                category(),
                                List.of(textField()),
                                CursorPageResponse.withCursorValues(
                                        List.of(
                                                Map.of(
                                                        "id", 10L,
                                                        "fondsCode", "F001",
                                                        "fondsName", "全宗",
                                                        "categoryCode", "contract",
                                                        "categoryName", "合同",
                                                        "archiveNo", "A-001",
                                                        "archiveYear", 2026,
                                                        "title", "题名")),
                                        0,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null)));
        when(archiveCategoryService.getCategory(1L)).thenReturn(category());

        stubStoredDownload("export-code");

        var result = importExportService.createExportDownloadLink(request, 9L);

        assertThat(result.code()).isEqualTo("export-code");
        var commandCaptor =
                org.mockito.ArgumentCaptor.forClass(
                        StorageObjectService.StoreStorageObjectCommand.class);
        verify(storageObjectService).storeObject(commandCaptor.capture(), eq(9L));
        assertThat(commandCaptor.getValue().originalFilename()).isEqualTo("archive-export.xlsx");
        assertThat(commandCaptor.getValue().inputStream()).isInstanceOf(ByteArrayInputStream.class);
        assertThat(commandCaptor.getValue().expiresAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 15, 10, 10));
        verify(fileLinkService)
                .createUserLinkUntil(
                        FileLinkTargetType.STORAGE_OBJECT,
                        null,
                        20L,
                        LocalDateTime.of(2026, 7, 15, 10, 10),
                        9L);
        verify(auditRepository).insert(any(ArchiveItemAudit.class));
    }

    @Test
    @DisplayName("临时对象与短链使用同一个绝对过期时间")
    void createExportDownloadLinkShouldShareOneAbsoluteExpiresAt() {
        AdvancingClock advancingClock =
                new AdvancingClock(
                        Instant.parse("2026-07-15T10:00:00Z"),
                        ZoneOffset.UTC,
                        Duration.ofSeconds(30));
        FileLinkDataRepository fileLinkRepository = mock(FileLinkDataRepository.class);
        FileLinkCodeGenerator codeGenerator = mock(FileLinkCodeGenerator.class);
        when(codeGenerator.generate()).thenReturn("export-code");
        when(fileLinkRepository.insert(any(FileLink.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        FileLinkService realFileLinkService =
                new FileLinkService(fileLinkRepository, codeGenerator, advancingClock);
        importExportService =
                new ArchiveItemImportExportService(
                        archiveMetadataService,
                        archiveMetadataReferenceService,
                        archiveCategoryService,
                        archiveItemRoutingService,
                        archiveItemQueryService,
                        permissionService,
                        dataScopeService,
                        archiveItemRepository,
                        auditRepository,
                        storageObjectService,
                        realFileLinkService,
                        advancingClock);
        when(permissionService.hasPermission(9L, "archive:export")).thenReturn(true);
        when(archiveItemQueryService.searchItems(any(), eq(9L)))
                .thenReturn(
                        new ArchiveItemListDto(
                                null,
                                List.of(),
                                CursorPageResponse.withCursorValues(
                                        List.of(), 0, null, null, null, null, null)));
        when(storageObjectService.storeObject(any(), eq(9L)))
                .thenReturn(
                        new StorageObjectDto(
                                20L, "archive", "key", "export.xlsx", 3, null, null, 9L));
        var commandCaptor =
                org.mockito.ArgumentCaptor.forClass(
                        StorageObjectService.StoreStorageObjectCommand.class);

        var result = importExportService.createExportDownloadLink(null, 9L);

        verify(storageObjectService).storeObject(commandCaptor.capture(), eq(9L));
        assertThat(result.expiresAt()).isEqualTo(commandCaptor.getValue().expiresAt());
    }

    @Test
    @DisplayName("导出使用可写事务以记录操作审计")
    void createExportDownloadLinkShouldUseWritableTransaction() throws NoSuchMethodException {
        Transactional transactional =
                ArchiveItemImportExportService.class
                        .getMethod(
                                "createExportDownloadLink",
                                ArchiveItemQueryService.SearchArchiveItemsRequest.class,
                                Long.class)
                        .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
    }

    @Test
    @DisplayName("导出沿用查询结果，范围外空结果不会额外读取数据")
    void exportItemsShouldReturnEmptyWorkbookWhenSearchResultIsEmpty() {
        when(permissionService.hasPermission(9L, "archive:export")).thenReturn(true);
        when(archiveItemQueryService.searchItems(any(), eq(9L)))
                .thenReturn(
                        new ArchiveItemListDto(
                                category(),
                                List.of(textField()),
                                CursorPageResponse.withCursorValues(
                                        List.of(), 0, null, null, null, null, null)));
        when(archiveCategoryService.getCategory(1L)).thenReturn(category());

        stubStoredDownload("empty-export");
        var result =
                importExportService.createExportDownloadLink(
                        new SearchArchiveItemsRequest(
                                1L, "F001", null, null, null, 100, null, null),
                        9L);

        assertThat(result.code()).isEqualTo("empty-export");
        verify(archiveItemQueryService).searchItems(any(), eq(9L));
        verify(auditRepository).insert(any(ArchiveItemAudit.class));
    }

    @Test
    @DisplayName("缺少导出权限时拒绝导出")
    void exportItemsShouldRejectMissingExportPermission() {
        when(permissionService.hasPermission(9L, "archive:export")).thenReturn(false);

        assertThatThrownBy(() -> importExportService.createExportDownloadLink(null, 9L))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception ->
                                assertThat(exception.getStatusCode())
                                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    private void stubStoredDownload(String code) {
        when(storageObjectService.storeObject(any(), eq(9L)))
                .thenReturn(
                        new StorageObjectDto(
                                20L, "archive", "key", "export.xlsx", 3, null, null, 9L));
        when(fileLinkService.createUserLinkUntil(
                        FileLinkTargetType.STORAGE_OBJECT,
                        null,
                        20L,
                        LocalDateTime.of(2026, 7, 15, 10, 10),
                        9L))
                .thenReturn(
                        new FileLinkService.FileLinkCreated(
                                code, LocalDateTime.of(2026, 7, 15, 10, 10)));
    }

    private static final class AdvancingClock extends Clock {
        private Instant current;
        private final ZoneId zone;
        private final Duration step;

        private AdvancingClock(Instant current, ZoneId zone, Duration step) {
            this.current = current;
            this.zone = zone;
            this.step = step;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId newZone) {
            return new AdvancingClock(current, newZone, step);
        }

        @Override
        public Instant instant() {
            Instant result = current;
            current = current.plus(step);
            return result;
        }
    }

    private static byte[] workbookBytes(List<List<Object>> rows) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            FesodSheet.write(outputStream)
                    .head(
                            List.of(
                                    List.of("全宗编码"),
                                    List.of("档号"),
                                    List.of("年度"),
                                    List.of("电子状态"),
                                    List.of("题名")))
                    .sheet("导入")
                    .doWrite(rows);
            return outputStream.toByteArray();
        }
    }

    private static ArchiveCategoryDto category() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 30, 10, 0);
        return new ArchiveCategoryDto(
                1L,
                1L,
                null,
                "contract",
                "合同",
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

    private static ArchiveFieldDto textField() {
        return new ArchiveFieldDto(
                1L,
                1L,
                ArchiveLevel.ITEM,
                ArchiveFieldScope.METADATA,
                "title",
                "题名",
                ArchiveFieldType.TEXT,
                "title",
                100,
                null,
                null,
                ArchiveFieldControl.INPUT,
                true,
                null,
                0,
                true,
                1,
                0,
                true,
                1,
                0,
                true,
                true,
                true,
                0,
                null,
                null,
                null);
    }

    private static ArchiveFondsDto fonds() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 30, 10, 0);
        return new ArchiveFondsDto(1L, "F001", "全宗", true, 0, now, now);
    }

    private static ArchiveItem existingItem() {
        ArchiveItem item = new ArchiveItem();
        item.setId(20L);
        item.setFondsCode("F001");
        item.setFondsName("全宗");
        item.setCategoryCode("contract");
        item.setCategoryName("合同");
        item.setArchiveNo("A-002");
        item.setArchiveYear(2026);
        item.setElectronicStatus("DRAFT");
        return item;
    }
}
