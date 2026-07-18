package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService;
import github.luckygc.am.module.archive.governance.service.ArchiveGovernanceService;
import github.luckygc.am.module.archive.item.repository.ArchiveItemAuditDataRepository;
import github.luckygc.am.module.archive.item.service.ArchiveItemCommandService.CreateArchiveItemRequest;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
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

@DisplayName("档案写入字段错误路径")
class ArchiveItemCommandFieldPathTests {

    @Test
    @DisplayName("创建档案时实物字段错误返回 physicalFields 路径")
    void createItemShouldKeepPhysicalFieldPath() {
        ArchiveMetadataService metadataService = mock(ArchiveMetadataService.class);
        ArchiveMetadataReferenceService referenceService =
                mock(ArchiveMetadataReferenceService.class);
        ArchiveCategoryService categoryService = mock(ArchiveCategoryService.class);
        ArchiveMapper archiveMapper = mock(ArchiveMapper.class);
        AuthorizationPermissionService permissionService =
                mock(AuthorizationPermissionService.class);
        ArchiveFieldDto dynamicField =
                ArchiveItemFieldValueConverterTests.field("title", "题名", ArchiveFieldType.TEXT, 20);
        ArchiveFieldDto physicalField =
                ArchiveItemFieldValueConverterTests.field(
                        "box_no", "盒号", ArchiveFieldType.TEXT, 3, ArchiveFieldScope.PHYSICAL);
        when(permissionService.hasPermission(anyLong(), anyString())).thenReturn(true);
        when(categoryService.getCategory(1L)).thenReturn(category());
        when(archiveMapper.tableExists("am_archive_item_contract")).thenReturn(1);
        when(referenceService.getEnabledFondsByCode("F001")).thenReturn(fonds());
        when(metadataService.listEnabledFields(1L, ArchiveLevel.ITEM))
                .thenReturn(List.of(dynamicField));
        when(metadataService.listEnabledFields(1L, ArchiveLevel.ITEM, ArchiveFieldScope.PHYSICAL))
                .thenReturn(List.of(physicalField));
        ArchiveItemCommandService service =
                new ArchiveItemCommandService(
                        metadataService,
                        referenceService,
                        categoryService,
                        mock(ArchiveGovernanceService.class),
                        archiveMapper,
                        mock(ArchiveItemSearchProjectionService.class),
                        mock(ArchiveDataScopeService.class),
                        permissionService,
                        mock(ArchiveItemAuditDataRepository.class),
                        mock(ArchiveItemReadService.class),
                        new ArchiveItemFieldValueConverter(),
                        ArchiveRuntimeTestSupport.passthroughExecutionService(),
                        ArchiveRuntimeTestSupport.traceService());

        assertThatThrownBy(
                        () ->
                                service.createItem(
                                        new CreateArchiveItemRequest(
                                                1L,
                                                null,
                                                "F001",
                                                "A-001",
                                                2026,
                                                "DRAFT",
                                                null,
                                                null,
                                                Map.of("box_no", "BOX-001"),
                                                Map.of("title", "合同")),
                                        9L))
                .isInstanceOfSatisfying(
                        BadRequestException.class,
                        exception ->
                                assertThat(exception.fieldViolations())
                                        .extracting(violation -> violation.field())
                                        .containsExactly("physicalFields.box_no"));
    }

    private static ArchiveCategoryDto category() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 15, 10, 0);
        return new ArchiveCategoryDto(
                1L,
                1L,
                null,
                "contract",
                "合同档案",
                ArchiveManagementMode.ITEM_ONLY,
                null,
                "am_archive_item_contract",
                null,
                "am_archive_item_contract_physical",
                ArchiveTableStatus.BUILT,
                now,
                true,
                0,
                now,
                now);
    }

    private static ArchiveFondsDto fonds() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 15, 10, 0);
        return new ArchiveFondsDto(1L, "F001", "默认全宗", true, 0, now, now);
    }
}
