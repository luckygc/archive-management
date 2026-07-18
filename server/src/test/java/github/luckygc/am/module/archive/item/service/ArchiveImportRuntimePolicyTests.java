package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.apache.fesod.sheet.FesodSheet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeResolutionTypes.ArchiveDataScopeFilter;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService;
import github.luckygc.am.module.archive.item.repository.ArchiveItemAuditDataRepository;
import github.luckygc.am.module.archive.item.repository.ArchiveItemDataRepository;
import github.luckygc.am.module.archive.metadata.ArchiveManagementMode;
import github.luckygc.am.module.archive.metadata.ArchiveTableStatus;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataReferenceService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFondsDto;
import github.luckygc.am.module.archive.rule.ArchiveRuleDecisionSeverity;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeDecision;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeDefinitionKind;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeBlockedException;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeExecutionService.ArchiveRuntimeExecutionResult;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;
import github.luckygc.am.module.storage.service.FileLinkService;
import github.luckygc.am.module.storage.service.StorageObjectService;

@DisplayName("档案导入运行时策略")
class ArchiveImportRuntimePolicyTests {

    @Test
    @DisplayName("导入复用条目创建命令且运行时阻断后不产生下载副作用")
    void importShouldReuseCreateCommandAndPropagateRuntimeBlock() throws IOException {
        ArchiveMetadataService metadataService = mock(ArchiveMetadataService.class);
        ArchiveMetadataReferenceService referenceService =
                mock(ArchiveMetadataReferenceService.class);
        ArchiveCategoryService categoryService = mock(ArchiveCategoryService.class);
        ArchiveItemCommandService commandService = mock(ArchiveItemCommandService.class);
        ArchiveItemQueryService queryService = mock(ArchiveItemQueryService.class);
        AuthorizationPermissionService permissionService =
                mock(AuthorizationPermissionService.class);
        ArchiveDataScopeService dataScopeService = mock(ArchiveDataScopeService.class);
        ArchiveItemDataRepository itemRepository = mock(ArchiveItemDataRepository.class);
        ArchiveItemAuditDataRepository auditRepository = mock(ArchiveItemAuditDataRepository.class);
        StorageObjectService storageObjectService = mock(StorageObjectService.class);
        FileLinkService fileLinkService = mock(FileLinkService.class);
        ArchiveItemImportExportService service =
                new ArchiveItemImportExportService(
                        metadataService,
                        referenceService,
                        categoryService,
                        commandService,
                        queryService,
                        permissionService,
                        dataScopeService,
                        itemRepository,
                        auditRepository,
                        storageObjectService,
                        fileLinkService,
                        Clock.fixed(Instant.parse("2026-07-18T10:00:00Z"), ZoneOffset.UTC),
                        ArchiveRuntimeTestSupport.passthroughExecutionService(),
                        ArchiveRuntimeTestSupport.traceService());

        when(categoryService.getCategory(1L)).thenReturn(category());
        when(metadataService.listEnabledFields(1L, ArchiveLevel.ITEM)).thenReturn(List.of());
        when(referenceService.getEnabledFondsByCode("F001")).thenReturn(fonds());
        when(permissionService.hasPermission(9L, "archive:item:create")).thenReturn(true);
        when(dataScopeService.buildItemFilter(9L, 1L, null))
                .thenReturn(ArchiveDataScopeFilter.all());
        when(dataScopeService.buildItemFilter(9L, 1L, "F001"))
                .thenReturn(ArchiveDataScopeFilter.all());
        when(commandService.createItem(any(), eq(9L)))
                .thenThrow(new ArchiveRuntimeBlockedException(blockingResult()));

        assertThatThrownBy(
                        () ->
                                service.importItems(
                                        1L, new ByteArrayInputStream(workbookBytes()), 9L))
                .isInstanceOf(ArchiveRuntimeBlockedException.class)
                .hasMessageContaining("import-create-block");

        verify(commandService).createItem(any(), eq(9L));
        verify(storageObjectService, never()).storeObject(any(), eq(9L));
        verify(fileLinkService, never()).createUserLinkUntil(any(), any(), any(), any(), any());
        verify(auditRepository, never()).insert(any());
    }

    private static ArchiveRuntimeExecutionResult blockingResult() {
        ArchiveRuntimeDecision decision =
                new ArchiveRuntimeDecision(
                        1L,
                        "import-create-block",
                        ArchiveRuntimeDefinitionKind.CONSTRAINT,
                        false,
                        List.of(),
                        "导入数据不满足约束",
                        ArchiveRuleDecisionSeverity.ERROR,
                        true,
                        "约束断言未满足");
        return new ArchiveRuntimeExecutionResult(
                Map.of(), Map.of(), List.of(decision), List.of(), true);
    }

    private static byte[] workbookBytes() throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            FesodSheet.write(outputStream)
                    .head(List.of(List.of("全宗编码"), List.of("档号"), List.of("年度"), List.of("电子状态")))
                    .sheet("导入")
                    .doWrite(List.of(List.of("F001", "A-001", 2026, "DRAFT")));
            return outputStream.toByteArray();
        }
    }

    private static ArchiveCategoryDto category() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 18, 10, 0);
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

    private static ArchiveFondsDto fonds() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 18, 10, 0);
        return new ArchiveFondsDto(1L, "F001", "全宗", true, 0, now, now);
    }
}
