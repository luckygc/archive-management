package github.luckygc.am.module.archive.physical.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.item.service.ArchiveItemReadService;
import github.luckygc.am.module.archive.item.service.ArchiveVolumeService;
import github.luckygc.am.module.archive.physical.ArchivePhysicalCustodyStatus;
import github.luckygc.am.module.archive.physical.ArchivePhysicalLocationHistory;
import github.luckygc.am.module.archive.physical.ArchivePhysicalObject;
import github.luckygc.am.module.archive.physical.ArchiveStorageLocation;
import github.luckygc.am.module.archive.physical.repository.ArchivePhysicalLocationHistoryDataRepository;
import github.luckygc.am.module.archive.physical.repository.ArchivePhysicalObjectDataRepository;
import github.luckygc.am.module.archive.physical.service.ArchivePhysicalObjectService.BatchAssignArchiveLocationRequest;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@DisplayName("档案实物位置变更")
class ArchivePhysicalLocationServiceTests {

    @Test
    @DisplayName("重复关联当前位置不新增历史")
    void sameLocationShouldNotCreateHistory() {
        ArchivePhysicalObjectDataRepository objectRepository =
                mock(ArchivePhysicalObjectDataRepository.class);
        ArchivePhysicalLocationHistoryDataRepository historyRepository =
                mock(ArchivePhysicalLocationHistoryDataRepository.class);
        ArchiveStorageLocationService locationService = mock(ArchiveStorageLocationService.class);
        AuthorizationPermissionService permissionService =
                mock(AuthorizationPermissionService.class);
        when(permissionService.hasPermission(9L, "archive:item:update")).thenReturn(true);
        ArchiveStorageLocation location = new ArchiveStorageLocation();
        location.setId(21L);
        when(locationService.getEnabledLocation(21L)).thenReturn(location);
        ArchivePhysicalObject object = new ArchivePhysicalObject();
        object.setId(51L);
        object.setArchiveItemId(31L);
        object.setCustodyStatus(ArchivePhysicalCustodyStatus.ARCHIVE_ROOM_CUSTODY);
        object.setCurrentLocationId(21L);
        when(objectRepository.findById(51L)).thenReturn(Optional.of(object));
        ArchivePhysicalObjectService service =
                new ArchivePhysicalObjectService(
                        objectRepository,
                        historyRepository,
                        locationService,
                        mock(ArchiveItemReadService.class),
                        mock(ArchiveVolumeService.class),
                        permissionService,
                        Clock.fixed(Instant.parse("2026-07-29T01:02:03Z"), ZoneOffset.UTC));

        var response =
                service.batchAssignLocation(
                        new BatchAssignArchiveLocationRequest(
                                List.of(51L), 21L, null, null, "复核位置"),
                        9L);

        assertThat(response.changedCount()).isZero();
        verify(objectRepository, never()).update(any());
        verify(historyRepository, never()).insert(any(ArchivePhysicalLocationHistory.class));
    }

    @Test
    @DisplayName("待接收实物不能关联库位")
    void pendingReceiptShouldNotAssignLocation() {
        ArchivePhysicalObjectDataRepository objectRepository =
                mock(ArchivePhysicalObjectDataRepository.class);
        ArchiveStorageLocationService locationService = mock(ArchiveStorageLocationService.class);
        AuthorizationPermissionService permissionService =
                mock(AuthorizationPermissionService.class);
        when(permissionService.hasPermission(9L, "archive:item:update")).thenReturn(true);
        ArchiveStorageLocation location = new ArchiveStorageLocation();
        location.setId(21L);
        when(locationService.getEnabledLocation(21L)).thenReturn(location);
        ArchivePhysicalObject object = new ArchivePhysicalObject();
        object.setId(51L);
        object.setArchiveItemId(31L);
        object.setCustodyStatus(ArchivePhysicalCustodyStatus.PENDING_RECEIPT);
        when(objectRepository.findById(51L)).thenReturn(Optional.of(object));
        ArchivePhysicalObjectService service =
                new ArchivePhysicalObjectService(
                        objectRepository,
                        mock(ArchivePhysicalLocationHistoryDataRepository.class),
                        locationService,
                        mock(ArchiveItemReadService.class),
                        mock(ArchiveVolumeService.class),
                        permissionService,
                        Clock.fixed(Instant.parse("2026-07-29T01:02:03Z"), ZoneOffset.UTC));

        assertThatThrownBy(
                        () ->
                                service.batchAssignLocation(
                                        new BatchAssignArchiveLocationRequest(
                                                List.of(51L), 21L, null, null, "提前上架"),
                                        9L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("只有档案室保管的实物");
    }
}
