package github.luckygc.am.module.archive.library.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import jakarta.data.Limit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.item.ArchiveItem;
import github.luckygc.am.module.archive.item.repository.ArchiveItemDataRepository;
import github.luckygc.am.module.archive.item.repository.ArchiveVolumeDataRepository;
import github.luckygc.am.module.archive.library.ArchiveRepository;
import github.luckygc.am.module.archive.library.ArchiveRepositoryRole;
import github.luckygc.am.module.archive.library.repository.ArchiveRepositoryChangeHistoryDataRepository;
import github.luckygc.am.module.archive.library.repository.ArchiveRepositoryDataRepository;
import github.luckygc.am.module.archive.library.service.ArchiveRepositoryService.CreateArchiveRepositoryRequest;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@DisplayName("档案虚拟业务库服务")
class ArchiveRepositoryServiceTests {

    private ArchiveRepositoryDataRepository repository;
    private ArchiveRepositoryChangeHistoryDataRepository historyRepository;
    private ArchiveItemDataRepository itemRepository;
    private ArchiveVolumeDataRepository volumeRepository;
    private AuthorizationPermissionService permissionService;
    private ArchiveRepositoryService service;

    @BeforeEach
    void setUp() {
        repository = mock(ArchiveRepositoryDataRepository.class);
        historyRepository = mock(ArchiveRepositoryChangeHistoryDataRepository.class);
        itemRepository = mock(ArchiveItemDataRepository.class);
        volumeRepository = mock(ArchiveVolumeDataRepository.class);
        permissionService = mock(AuthorizationPermissionService.class);
        service =
                new ArchiveRepositoryService(
                        repository,
                        historyRepository,
                        itemRepository,
                        volumeRepository,
                        permissionService);
        when(permissionService.hasPermission(9L, "archive:metadata:manage")).thenReturn(true);
    }

    @Test
    @DisplayName("创建虚拟业务库不携带真实库房信息")
    void createShouldPersistVirtualRepositoryOnly() {
        when(repository.insert(any(ArchiveRepository.class)))
                .thenAnswer(
                        invocation -> {
                            ArchiveRepository entity = invocation.getArgument(0);
                            entity.setId(20L);
                            return entity;
                        });

        var response =
                service.create(
                        new CreateArchiveRepositoryRequest(
                                "INTAKE_WEST", "西区预归档库", ArchiveRepositoryRole.INTAKE, true, 10),
                        9L);

        assertThat(response.id()).isEqualTo(20L);
        assertThat(response.repositoryRole()).isEqualTo(ArchiveRepositoryRole.INTAKE);
    }

    @Test
    @DisplayName("仍被档案引用的业务库不能删除")
    void deleteShouldRejectReferencedRepository() {
        ArchiveRepository entity = repository(20L, false, true);
        when(repository.findById(20L)).thenReturn(Optional.of(entity));
        when(itemRepository.findByRepositoryId(20L, Limit.of(1)))
                .thenReturn(List.of(new ArchiveItem()));

        assertThatThrownBy(() -> service.delete(20L, 9L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("仍在使用");

        verify(repository, never()).delete(any());
    }

    @Test
    @DisplayName("系统内置业务库不能删除")
    void deleteShouldRejectSystemRepository() {
        when(repository.findById(2L)).thenReturn(Optional.of(repository(2L, true, true)));

        assertThatThrownBy(() -> service.delete(2L, 9L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("系统内置");
    }

    private ArchiveRepository repository(Long id, boolean systemFlag, boolean enabled) {
        ArchiveRepository entity = new ArchiveRepository();
        entity.setId(id);
        entity.setRepositoryCode("R" + id);
        entity.setRepositoryName("业务库" + id);
        entity.setRepositoryRole(ArchiveRepositoryRole.HOLDING);
        entity.setSystemFlag(systemFlag);
        entity.setEnabled(enabled);
        return entity;
    }
}
