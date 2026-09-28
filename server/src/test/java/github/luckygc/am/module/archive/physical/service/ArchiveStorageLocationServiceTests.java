package github.luckygc.am.module.archive.physical.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.physical.ArchiveStorageLocation;
import github.luckygc.am.module.archive.physical.ArchiveWarehouse;
import github.luckygc.am.module.archive.physical.repository.ArchivePhysicalObjectDataRepository;
import github.luckygc.am.module.archive.physical.repository.ArchiveStorageLocationDataRepository;
import github.luckygc.am.module.archive.physical.repository.ArchiveWarehouseDataRepository;
import github.luckygc.am.module.archive.physical.service.ArchiveStorageLocationService.CreateArchiveStorageLocationRequest;
import github.luckygc.am.module.archive.physical.service.ArchiveStorageLocationService.UpdateArchiveStorageLocationRequest;
import github.luckygc.am.module.archive.physical.service.ArchiveStorageLocationService.UpdateArchiveWarehouseRequest;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@DisplayName("真实库房与存放位置服务")
class ArchiveStorageLocationServiceTests {

    private ArchiveWarehouseDataRepository warehouseRepository;
    private ArchiveStorageLocationDataRepository locationRepository;
    private ArchiveStorageLocationService service;

    @BeforeEach
    void setUp() {
        warehouseRepository = mock(ArchiveWarehouseDataRepository.class);
        locationRepository = mock(ArchiveStorageLocationDataRepository.class);
        AuthorizationPermissionService permissionService =
                mock(AuthorizationPermissionService.class);
        service =
                new ArchiveStorageLocationService(
                        warehouseRepository,
                        locationRepository,
                        mock(ArchivePhysicalObjectDataRepository.class),
                        permissionService);
        when(permissionService.hasPermission(9L, "archive:metadata:manage")).thenReturn(true);
    }

    @Test
    @DisplayName("位置归属真实库房而不是档案业务库")
    void createLocationShouldReferenceWarehouse() {
        when(warehouseRepository.findById(11L)).thenReturn(Optional.of(warehouse(11L)));
        when(locationRepository.insert(any(ArchiveStorageLocation.class)))
                .thenAnswer(
                        invocation -> {
                            ArchiveStorageLocation entity = invocation.getArgument(0);
                            entity.setId(21L);
                            return entity;
                        });

        var response =
                service.createLocation(
                        new CreateArchiveStorageLocationRequest(
                                11L, null, "R1", "一号库房", "ROOM", true, 0),
                        9L);

        assertThat(response.warehouseId()).isEqualTo(11L);
        assertThat(response.parentId()).isNull();
    }

    @Test
    @DisplayName("父位置不能跨真实库房")
    void createLocationShouldRejectParentFromAnotherWarehouse() {
        when(warehouseRepository.findById(11L)).thenReturn(Optional.of(warehouse(11L)));
        ArchiveStorageLocation parent = new ArchiveStorageLocation();
        parent.setId(20L);
        parent.setWarehouseId(12L);
        when(locationRepository.findById(20L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(
                        () ->
                                service.createLocation(
                                        new CreateArchiveStorageLocationRequest(
                                                11L, 20L, "S1", "一号架", "RACK", true, 0),
                                        9L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("同一真实库房");
    }

    @Test
    @DisplayName("库房无变化补丁不写入数据库")
    void unchangedWarehouseShouldNotWrite() {
        ArchiveWarehouse entity = warehouse(11L);
        when(warehouseRepository.findById(11L)).thenReturn(Optional.of(entity));

        var response =
                service.updateWarehouse(
                        11L, new UpdateArchiveWarehouseRequest(null, null, null, null), 9L);

        assertThat(response.warehouseName()).isEqualTo("库房11");
        verify(warehouseRepository, never()).update(any(ArchiveWarehouse.class));
    }

    @Test
    @DisplayName("移除父位置后保存根位置，再次提交相同补丁不重复写入")
    void removeParentAndAvoidDuplicateWrite() {
        ArchiveStorageLocation entity = new ArchiveStorageLocation();
        entity.setId(21L);
        entity.setWarehouseId(11L);
        entity.setParentId(20L);
        entity.setLocationCode("R1");
        entity.setLocationName("一号架");
        entity.setLocationType("RACK");
        entity.setEnabled(true);
        when(locationRepository.findById(21L)).thenReturn(Optional.of(entity));
        when(warehouseRepository.findById(11L)).thenReturn(Optional.of(warehouse(11L)));
        when(locationRepository.update(entity)).thenReturn(entity);
        UpdateArchiveStorageLocationRequest patch =
                new UpdateArchiveStorageLocationRequest(
                        null, null, true, null, null, null, null, null);

        var first = service.updateLocation(21L, patch, 9L);
        var second = service.updateLocation(21L, patch, 9L);

        assertThat(first.parentId()).isNull();
        assertThat(second.parentId()).isNull();
        verify(locationRepository).update(entity);
    }

    private ArchiveWarehouse warehouse(Long id) {
        ArchiveWarehouse entity = new ArchiveWarehouse();
        entity.setId(id);
        entity.setWarehouseCode("W" + id);
        entity.setWarehouseName("库房" + id);
        entity.setEnabled(true);
        return entity;
    }
}
