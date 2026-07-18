package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import github.luckygc.am.common.storage.FileStorageService;
import github.luckygc.am.common.storage.StorageObjectInfo;
import github.luckygc.am.module.archive.item.ArchiveItem;
import github.luckygc.am.module.archive.item.repository.ArchiveItemAuditDataRepository;
import github.luckygc.am.module.archive.item.repository.ArchiveItemDataRepository;
import github.luckygc.am.module.archive.item.service.ArchiveItemElectronicFileService.UploadArchiveItemElectronicFileCommand;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.metadata.ArchiveManagementMode;
import github.luckygc.am.module.archive.metadata.ArchiveTableStatus;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryDto;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeDecision;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeDefinitionKind;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeBlockedException;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeExecutionService;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeExecutionService.ArchiveRuntimeExecutionResult;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeTraceService;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;
import github.luckygc.am.module.storage.StorageObject;
import github.luckygc.am.module.storage.repository.StorageObjectDataRepository;
import github.luckygc.am.module.storage.service.StorageObjectService;

@DisplayName("电子文件上传运行时策略")
class ArchiveElectronicFileRuntimePolicyTests {

    private ArchiveMapper archiveMapper;
    private FileStorageService fileStorageService;
    private ArchiveRuntimeExecutionService runtimeExecutionService;
    private ArchiveItemElectronicFileService service;

    @BeforeEach
    void setUp() throws Exception {
        archiveMapper = mock(ArchiveMapper.class);
        fileStorageService = mock(FileStorageService.class);
        runtimeExecutionService = mock(ArchiveRuntimeExecutionService.class);
        ArchiveItemDataRepository itemRepository = mock(ArchiveItemDataRepository.class);
        when(itemRepository.findById(10L)).thenReturn(Optional.of(item()));
        ArchiveMetadataService metadataService = mock(ArchiveMetadataService.class);
        when(metadataService.listEnabledFields(anyLong(), any())).thenReturn(List.of());
        when(metadataService.listEnabledFields(anyLong(), any(), any())).thenReturn(List.of());
        ArchiveCategoryService categoryService = mock(ArchiveCategoryService.class);
        when(categoryService.listCategories(null)).thenReturn(List.of(category()));
        AuthorizationPermissionService permissionService =
                mock(AuthorizationPermissionService.class);
        when(permissionService.hasPermission(anyLong(), anyString())).thenReturn(true);
        StorageObjectDataRepository storageRepository = mock(StorageObjectDataRepository.class);
        when(storageRepository.insert(any(StorageObject.class)))
                .thenAnswer(
                        invocation -> {
                            StorageObject object = invocation.getArgument(0);
                            object.setId(20L);
                            return object;
                        });
        when(fileStorageService.putObject(
                        anyString(), any(InputStream.class), eq(4L), eq("application/pdf")))
                .thenAnswer(
                        invocation -> {
                            InputStream input = invocation.getArgument(1);
                            input.transferTo(OutputStream.nullOutputStream());
                            return new StorageObjectInfo(
                                    "archive",
                                    invocation.getArgument(0),
                                    4,
                                    "application/pdf",
                                    "etag");
                        });
        StorageObjectService storageObjectService =
                new StorageObjectService(
                        storageRepository,
                        fileStorageService,
                        Clock.fixed(
                                Instant.parse("2026-07-18T02:00:00Z"), ZoneId.of("Asia/Shanghai")));
        service =
                new ArchiveItemElectronicFileService(
                        archiveMapper,
                        storageObjectService,
                        itemRepository,
                        mock(ArchiveItemAuditDataRepository.class),
                        permissionService,
                        mock(ArchiveItemReadService.class),
                        metadataService,
                        categoryService,
                        runtimeExecutionService,
                        mock(ArchiveRuntimeTraceService.class));
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("运行时阻断发生在对象存储和文件元数据之前")
    void blockedUploadDoesNotCreateObjectOrMetadata() throws Exception {
        when(runtimeExecutionService.enforce(any()))
                .thenThrow(
                        new ArchiveRuntimeBlockedException(
                                new ArchiveRuntimeExecutionResult(
                                        Map.of(),
                                        Map.of(),
                                        List.of(
                                                new ArchiveRuntimeDecision(
                                                        1L,
                                                        "pdf-only",
                                                        ArchiveRuntimeDefinitionKind.CONSTRAINT,
                                                        false,
                                                        List.of(),
                                                        "只允许 PDF",
                                                        github.luckygc.am.module.archive.rule
                                                                .ArchiveRuleDecisionSeverity.ERROR,
                                                        true,
                                                        null)),
                                        List.of(),
                                        true)));

        assertThatThrownBy(() -> service.uploadFile(10L, command(), 9L))
                .hasMessageContaining("pdf-only");

        verify(fileStorageService, never())
                .putObject(anyString(), any(InputStream.class), anyLong(), anyString());
        verify(archiveMapper, never())
                .insertArchiveItemElectronicFile(anyLong(), anyLong(), anyString(), anyInt());
    }

    @Test
    @DisplayName("对象已创建但文件元数据失败时沿用存储层回滚补偿")
    void metadataFailureDeletesUploadedObjectAfterRollback() throws Exception {
        when(runtimeExecutionService.enforce(any()))
                .thenAnswer(
                        invocation -> {
                            var request =
                                    (github.luckygc.am.module.archive.rule.service
                                                    .ArchiveRuntimeExecutionService
                                                    .ArchiveRuntimeExecutionRequest)
                                            invocation.getArgument(0);
                            return new ArchiveRuntimeExecutionResult(
                                    request.candidateFacts(),
                                    Map.of(),
                                    List.of(),
                                    List.of(),
                                    false);
                        });
        when(archiveMapper.insertArchiveItemElectronicFile(10L, 20L, "ORIGINAL", 0))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        TransactionSynchronizationManager.initSynchronization();

        assertThatThrownBy(() -> service.uploadFile(10L, command(), 9L))
                .hasMessageContaining("已存在");
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(
                synchronization ->
                        synchronization.afterCompletion(
                                TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(fileStorageService).deleteObject(eq("archive"), anyString());
    }

    private UploadArchiveItemElectronicFileCommand command() {
        return new UploadArchiveItemElectronicFileCommand(
                "demo.pdf",
                "application/pdf",
                4,
                new ByteArrayInputStream("demo".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "ORIGINAL",
                0);
    }

    private ArchiveItem item() {
        ArchiveItem item = new ArchiveItem();
        item.setId(10L);
        item.setFondsCode("F001");
        item.setFondsName("测试全宗");
        item.setCategoryCode("DOC");
        item.setCategoryName("文件档案");
        item.setArchiveNo("A-001");
        item.setArchiveYear(2026);
        item.setElectronicStatus("DRAFT");
        item.setGovernanceSchemeVersionId(11L);
        return item;
    }

    private ArchiveCategoryDto category() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 18, 10, 0);
        return new ArchiveCategoryDto(
                1L,
                1L,
                null,
                "DOC",
                "文件档案",
                ArchiveManagementMode.ITEM_ONLY,
                null,
                null,
                null,
                null,
                ArchiveTableStatus.NOT_BUILT,
                null,
                true,
                0,
                now,
                now);
    }
}
