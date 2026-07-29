package github.luckygc.am.module.archive.physical.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.item.service.ArchiveItemReadService;
import github.luckygc.am.module.archive.item.service.ArchiveVolumeService;
import github.luckygc.am.module.archive.physical.ArchivePhysicalObject;
import github.luckygc.am.module.archive.physical.repository.ArchivePhysicalLocationHistoryDataRepository;
import github.luckygc.am.module.archive.physical.repository.ArchivePhysicalObjectDataRepository;
import github.luckygc.am.module.archive.physical.service.ArchivePhysicalObjectService.CreateArchivePhysicalObjectRequest;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@DisplayName("档案实物对象服务")
class ArchivePhysicalObjectServiceTests {

    private ArchivePhysicalObjectDataRepository objectRepository;
    private ArchivePhysicalObjectService service;

    @BeforeEach
    void setUp() {
        objectRepository = mock(ArchivePhysicalObjectDataRepository.class);
        AuthorizationPermissionService permissionService =
                mock(AuthorizationPermissionService.class);
        when(permissionService.hasPermission(9L, "archive:item:update")).thenReturn(true);
        when(permissionService.hasPermission(9L, "archive:item:read")).thenReturn(true);
        service =
                new ArchivePhysicalObjectService(
                        objectRepository,
                        mock(ArchivePhysicalLocationHistoryDataRepository.class),
                        mock(ArchiveStorageLocationService.class),
                        mock(ArchiveItemReadService.class),
                        mock(ArchiveVolumeService.class),
                        permissionService,
                        Clock.fixed(Instant.parse("2026-07-29T01:02:03Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("条目和案卷所有者必须二选一")
    void createShouldRequireExactlyOneOwner() {
        CreateArchivePhysicalObjectRequest request =
                new CreateArchivePhysicalObjectRequest(
                        31L, 41L, null, null, BigDecimal.ONE, "卷", null, null);

        assertThatThrownBy(() -> service.create(request, 9L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("只能选择一个");
    }

    @Test
    @DisplayName("存在实物对象即表示档案存在实物")
    void createShouldReturnPhysicalObjectId() {
        when(objectRepository.insert(any(ArchivePhysicalObject.class)))
                .thenAnswer(
                        invocation -> {
                            ArchivePhysicalObject entity = invocation.getArgument(0);
                            entity.setId(51L);
                            return entity;
                        });

        var response =
                service.create(
                        new CreateArchivePhysicalObjectRequest(
                                31L, null, "B001", "PAPER", BigDecimal.ONE, "卷", "完好", null),
                        9L);

        assertThat(response.id()).isEqualTo(51L);
        assertThat(response.archiveItemId()).isEqualTo(31L);
        assertThat(response.archiveVolumeId()).isNull();
    }
}
