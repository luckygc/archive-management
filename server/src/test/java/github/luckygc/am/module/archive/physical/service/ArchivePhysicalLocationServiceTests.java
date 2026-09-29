package github.luckygc.am.module.archive.physical.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import jakarta.data.page.CursoredPage;
import jakarta.data.page.PageRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
    @DisplayName("位置历史分页不执行总数查询且先校验数据范围")
    void locationHistoryChecksScopeBeforePagedQuery() {
        ArchivePhysicalObjectDataRepository objectRepository =
                mock(ArchivePhysicalObjectDataRepository.class);
        ArchivePhysicalLocationHistoryDataRepository historyRepository =
                mock(ArchivePhysicalLocationHistoryDataRepository.class);
        ArchiveItemReadService itemReadService = mock(ArchiveItemReadService.class);
        AuthorizationPermissionService permissionService =
                mock(AuthorizationPermissionService.class);
        when(permissionService.hasPermission(9L, "archive:item:read")).thenReturn(true);
        ArchivePhysicalObject object = new ArchivePhysicalObject();
        object.setId(51L);
        object.setArchiveItemId(31L);
        when(objectRepository.findById(51L)).thenReturn(Optional.of(object));
        @SuppressWarnings("unchecked")
        CursoredPage<ArchivePhysicalLocationHistory> page = mock(CursoredPage.class);
        when(page.content()).thenReturn(List.of());
        when(historyRepository.list(eq(51L), any(PageRequest.class))).thenReturn(page);
        ArchivePhysicalObjectService service =
                new ArchivePhysicalObjectService(
                        objectRepository,
                        historyRepository,
                        mock(ArchiveStorageLocationService.class),
                        itemReadService,
                        mock(ArchiveVolumeService.class),
                        permissionService,
                        Clock.systemUTC());

        assertThat(service.listLocationHistory(51L, PageRequest.ofSize(2).withTotal(), 9L).items())
                .isEmpty();
        verify(itemReadService).assertItemInDataScope(31L, 9L);
        ArgumentCaptor<PageRequest> capturedPage = ArgumentCaptor.forClass(PageRequest.class);
        verify(historyRepository).list(eq(51L), capturedPage.capture());
        assertThat(capturedPage.getValue().size()).isEqualTo(2);
        assertThat(capturedPage.getValue().requestTotal()).isFalse();

        clearInvocations(historyRepository);
        PageRequest next =
                PageRequest.ofSize(2)
                        .afterCursor(
                                PageRequest.Cursor.forKey(
                                        LocalDateTime.parse("2026-09-29T08:00:00"), 7L));
        service.listLocationHistory(51L, next, 9L);
        verify(historyRepository).list(eq(51L), capturedPage.capture());
        assertThat(capturedPage.getValue().cursor()).isPresent();

        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(itemReadService)
                .assertItemInDataScope(31L, 9L);
        clearInvocations(historyRepository);
        assertThatThrownBy(() -> service.listLocationHistory(51L, PageRequest.ofSize(2), 9L))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(historyRepository);
    }

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
