package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.data.Order;
import jakarta.data.page.CursoredPage;
import jakarta.data.page.PageRequest;
import jakarta.data.restrict.Restriction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeResolutionTypes.ArchiveDataScopeFilter;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService;
import github.luckygc.am.module.archive.governance.ArchiveGovernanceSchemeVersion;
import github.luckygc.am.module.archive.governance.service.ArchiveGovernanceService;
import github.luckygc.am.module.archive.item.ArchiveVolume;
import github.luckygc.am.module.archive.item._ArchiveVolume;
import github.luckygc.am.module.archive.item.repository.ArchiveVolumeDataRepository;
import github.luckygc.am.module.archive.item.service.ArchiveVolumeService.CreateArchiveVolumeRequest;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.metadata.ArchiveManagementMode;
import github.luckygc.am.module.archive.metadata.ArchiveTableStatus;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataReferenceService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFondsDto;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@DisplayName("案卷权限")
class ArchiveVolumePermissionTests {

    private ArchiveMapper archiveMapper;
    private ArchiveVolumeDataRepository archiveVolumeRepository;
    private ArchiveMetadataService archiveMetadataService;
    private ArchiveMetadataReferenceService archiveMetadataReferenceService;
    private ArchiveCategoryService archiveCategoryService;
    private ArchiveGovernanceService governanceService;
    private ArchiveItemReadService archiveItemRoutingService;
    private AuthorizationPermissionService permissionService;
    private ArchiveDataScopeService dataScopeService;
    private ArchiveVolumeService archiveVolumeService;

    @BeforeEach
    void setUp() {
        archiveMapper = mock(ArchiveMapper.class);
        archiveVolumeRepository = mock(ArchiveVolumeDataRepository.class);
        archiveMetadataService = mock(ArchiveMetadataService.class);
        archiveMetadataReferenceService = mock(ArchiveMetadataReferenceService.class);
        archiveCategoryService = mock(ArchiveCategoryService.class);
        governanceService = mock(ArchiveGovernanceService.class);
        archiveItemRoutingService = mock(ArchiveItemReadService.class);
        permissionService = mock(AuthorizationPermissionService.class);
        dataScopeService = mock(ArchiveDataScopeService.class);
        when(dataScopeService.buildItemFilter(anyLong(), anyLong(), anyString()))
                .thenReturn(ArchiveDataScopeFilter.all());
        when(governanceService.requireDefaultVersionForNewArchive(anyString(), anyString()))
                .thenReturn(governanceVersion());
        archiveVolumeService =
                new ArchiveVolumeService(
                        archiveMapper,
                        archiveVolumeRepository,
                        archiveMetadataService,
                        archiveMetadataReferenceService,
                        archiveCategoryService,
                        governanceService,
                        archiveItemRoutingService,
                        permissionService,
                        dataScopeService);
    }

    @Test
    @DisplayName("没有档案读取权限不能查询案卷列表")
    void listVolumesShouldRequireArchiveItemReadPermission() {
        when(permissionService.hasPermission(9L, "archive:item:read")).thenReturn(false);

        assertThatThrownBy(
                        () ->
                                archiveVolumeService.listVolumes(
                                        null, null, PageRequest.ofSize(100), 9L))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception ->
                                assertThat(exception.getStatusCode())
                                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(archiveVolumeRepository, never()).find(any(), any(), any());
    }

    @Test
    @DisplayName("案卷列表使用数据范围 Restriction 和稳定排序")
    void listVolumesShouldUseRestrictionAndStableOrder() {
        when(permissionService.hasPermission(8L, "archive:item:read")).thenReturn(true);
        when(dataScopeService.resolveUserDataScope(8L))
                .thenReturn(
                        github.luckygc.am.module.archive.authorization.service
                                .ArchiveDataScopeResolutionTypes.ResolvedArchiveDataScope.all());
        @SuppressWarnings("unchecked")
        CursoredPage<ArchiveVolume> page = mock(CursoredPage.class);
        when(page.content()).thenReturn(List.of());
        when(page.numberOfElements()).thenReturn(0);
        when(page.hasTotals()).thenReturn(false);
        when(archiveVolumeRepository.find(any(), any(), any())).thenReturn(page);
        PageRequest request = PageRequest.ofSize(100);

        archiveVolumeService.listVolumes("F001", "ACCOUNTING", request, 8L);

        verify(archiveVolumeRepository)
                .find(
                        any(Restriction.class),
                        eq(request),
                        eq(Order.by(_ArchiveVolume.createdAt.desc(), _ArchiveVolume.id.desc())));
        verify(dataScopeService, never()).matchesItemFilter(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("数据范围为空时直接返回空 cursor 页且不访问 Repository")
    void listVolumesShouldReturnEmptyCursorPageForEmptyDataScope() {
        when(permissionService.hasPermission(8L, "archive:item:read")).thenReturn(true);
        when(dataScopeService.resolveUserDataScope(8L))
                .thenReturn(
                        github.luckygc.am.module.archive.authorization.service
                                .ArchiveDataScopeResolutionTypes.ResolvedArchiveDataScope.none());

        var response = archiveVolumeService.listVolumes(null, null, PageRequest.ofSize(100), 8L);

        assertThat(response.items()).isEmpty();
        assertThat(response.next()).isNull();
        assertThat(response.prev()).isNull();
        verify(archiveVolumeRepository, never()).find(any(), any(), any());
    }

    @Test
    @DisplayName("创建案卷必须有档案创建权限且满足数据范围")
    void createVolumeShouldRequireCreatePermissionAndDataScope() {
        when(permissionService.hasPermission(9L, "archive:item:create")).thenReturn(true);
        when(archiveCategoryService.getCategory(1L)).thenReturn(volumeCategory());
        when(archiveCategoryService.listCategories(null))
                .thenReturn(java.util.List.of(volumeCategory()));
        when(archiveMetadataReferenceService.getEnabledFondsByCode("F001"))
                .thenReturn(activeFonds());
        when(archiveMapper.countArchiveVolumesByArchiveNo("contract", "V-001", null)).thenReturn(0);
        when(dataScopeService.buildItemFilter(9L, 1L, "F001"))
                .thenReturn(ArchiveDataScopeFilter.none());

        assertThatThrownBy(
                        () ->
                                archiveVolumeService.createVolume(
                                        new CreateArchiveVolumeRequest(
                                                1L, "F001", "V-001", 2026, "DRAFT"),
                                        9L))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception ->
                                assertThat(exception.getStatusCode())
                                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(archiveMapper, never())
                .insertArchiveVolume(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        anyString(),
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("创建案卷时写入默认治理方案版本")
    void createVolumeShouldSaveDefaultGovernanceVersion() {
        when(permissionService.hasPermission(9L, "archive:item:create")).thenReturn(true);
        when(archiveCategoryService.getCategory(1L)).thenReturn(volumeCategory());
        when(archiveCategoryService.listCategories(null))
                .thenReturn(java.util.List.of(volumeCategory()));
        when(archiveMetadataReferenceService.getEnabledFondsByCode("F001"))
                .thenReturn(activeFonds());
        when(archiveMapper.countArchiveVolumesByArchiveNo("contract", "V-001", null)).thenReturn(0);
        when(dataScopeService.buildItemFilter(9L, 1L, "F001"))
                .thenReturn(ArchiveDataScopeFilter.all());
        when(archiveMapper.insertArchiveVolume(
                        eq("F001"),
                        eq("启用全宗"),
                        eq("contract"),
                        eq("合同档案"),
                        eq("V-001"),
                        eq("DRAFT"),
                        eq(2026),
                        eq(77L),
                        eq(9L)))
                .thenReturn(31L);
        when(archiveMapper.getArchiveVolume(31L)).thenReturn(volumeRow());

        ArchiveVolumeService.ArchiveVolumeDto volume =
                archiveVolumeService.createVolume(
                        new CreateArchiveVolumeRequest(1L, "F001", "V-001", 2026, "DRAFT"), 9L);

        assertThat(volume.id()).isEqualTo(31L);
        verify(governanceService).requireDefaultVersionForNewArchive("F001", "contract");
    }

    private ArchiveFondsDto activeFonds() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 30, 10, 0);
        return new ArchiveFondsDto(1L, "F001", "启用全宗", true, 0, now, now);
    }

    private ArchiveGovernanceSchemeVersion governanceVersion() {
        ArchiveGovernanceSchemeVersion version = new ArchiveGovernanceSchemeVersion();
        version.setId(77L);
        return version;
    }

    private Map<String, Object> volumeRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 31L);
        row.put("fondsCode", "F001");
        row.put("fondsName", "启用全宗");
        row.put("categoryCode", "contract");
        row.put("categoryName", "合同档案");
        row.put("archiveNo", "V-001");
        row.put("electronicStatus", "DRAFT");
        row.put("archiveYear", 2026);
        row.put("lockedFlag", false);
        return row;
    }

    private ArchiveCategoryDto volumeCategory() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 30, 10, 0);
        return new ArchiveCategoryDto(
                1L,
                1L,
                null,
                "contract",
                "合同档案",
                ArchiveManagementMode.VOLUME_ITEM,
                "am_archive_volume_contract",
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
}
