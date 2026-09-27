package github.luckygc.am.module.archive.library.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.module.archive.ArchiveObjectType;
import github.luckygc.am.module.archive.item.ArchiveItem;
import github.luckygc.am.module.archive.item.repository.ArchiveItemDataRepository;
import github.luckygc.am.module.archive.item.repository.ArchiveVolumeDataRepository;
import github.luckygc.am.module.archive.item.service.ArchiveItemReadService;
import github.luckygc.am.module.archive.item.service.ArchiveVolumeService;
import github.luckygc.am.module.archive.library.ArchiveRepository;
import github.luckygc.am.module.archive.library.ArchiveRepositoryChangeHistory;
import github.luckygc.am.module.archive.library.ArchiveRepositoryRole;
import github.luckygc.am.module.archive.library.repository.ArchiveRepositoryChangeHistoryDataRepository;
import github.luckygc.am.module.archive.library.service.ArchiveRepositoryAssignmentService.ChangeArchiveRepositoryRequest;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@DisplayName("档案虚拟业务库归属")
class ArchiveRepositoryAssignmentServiceTests {

    private ArchiveRepositoryService repositoryService;
    private ArchiveRepositoryChangeHistoryDataRepository historyRepository;
    private ArchiveItemDataRepository itemRepository;
    private ArchiveItemReadService itemReadService;
    private AuthorizationPermissionService permissionService;
    private ArchiveRepositoryAssignmentService service;

    @BeforeEach
    void setUp() {
        repositoryService = mock(ArchiveRepositoryService.class);
        historyRepository = mock(ArchiveRepositoryChangeHistoryDataRepository.class);
        itemRepository = mock(ArchiveItemDataRepository.class);
        itemReadService = mock(ArchiveItemReadService.class);
        permissionService = mock(AuthorizationPermissionService.class);
        service =
                new ArchiveRepositoryAssignmentService(
                        repositoryService,
                        historyRepository,
                        itemRepository,
                        mock(ArchiveVolumeDataRepository.class),
                        itemReadService,
                        mock(ArchiveVolumeService.class),
                        permissionService,
                        Clock.fixed(Instant.parse("2026-07-29T01:02:03Z"), ZoneOffset.UTC));
        when(permissionService.hasPermission(9L, "archive:item:update")).thenReturn(true);
    }

    @Test
    @DisplayName("预归档完成后进入室藏库并记录历史")
    void changeItemRepositoryShouldMoveIntakeToHolding() {
        ArchiveItem item = new ArchiveItem();
        item.setId(31L);
        item.setRepositoryId(1L);
        when(itemRepository.findById(31L)).thenReturn(Optional.of(item));
        ArchiveRepository intake = repository(1L, ArchiveRepositoryRole.INTAKE);
        ArchiveRepository holding = repository(2L, ArchiveRepositoryRole.HOLDING);
        when(repositoryService.getRequired(1L)).thenReturn(intake);
        when(repositoryService.getEnabled(2L)).thenReturn(holding);

        var response =
                service.changeItemRepository(
                        31L, new ChangeArchiveRepositoryRequest(2L, "FILING", 88L, "完成归档"), 9L);

        assertThat(response.archiveType()).isEqualTo(ArchiveObjectType.ITEM);
        assertThat(item.getRepositoryId()).isEqualTo(2L);
        verify(itemRepository).update(item);
        verify(historyRepository).insert(any(ArchiveRepositoryChangeHistory.class));
    }

    @Test
    @DisplayName("正式档案不能退回预归档库")
    void changeItemRepositoryShouldRejectHoldingToIntake() {
        ArchiveItem item = new ArchiveItem();
        item.setId(31L);
        item.setRepositoryId(2L);
        when(itemRepository.findById(31L)).thenReturn(Optional.of(item));
        when(repositoryService.getRequired(2L))
                .thenReturn(repository(2L, ArchiveRepositoryRole.HOLDING));
        when(repositoryService.getEnabled(1L))
                .thenReturn(repository(1L, ArchiveRepositoryRole.INTAKE));

        assertThatThrownBy(
                        () ->
                                service.changeItemRepository(
                                        31L,
                                        new ChangeArchiveRepositoryRequest(1L, null, null, "错误退回"),
                                        9L))
                .hasMessageContaining("不能退回预归档库");

        assertThat(item.getRepositoryId()).isEqualTo(2L);
        verify(itemRepository, never()).update(any());
        verifyNoInteractions(historyRepository);
    }

    @Test
    @DisplayName("目标业务库未变化时不重复写历史")
    void sameRepositoryShouldNotWriteHistory() {
        ArchiveItem item = new ArchiveItem();
        item.setId(31L);
        item.setRepositoryId(2L);
        when(itemRepository.findById(31L)).thenReturn(Optional.of(item));
        ArchiveRepository holding = repository(2L, ArchiveRepositoryRole.HOLDING);
        when(repositoryService.getEnabled(2L)).thenReturn(holding);

        service.changeItemRepository(
                31L, new ChangeArchiveRepositoryRequest(2L, null, null, null), 9L);

        verifyNoInteractions(historyRepository);
    }

    private ArchiveRepository repository(Long id, ArchiveRepositoryRole role) {
        ArchiveRepository repository = new ArchiveRepository();
        repository.setId(id);
        repository.setRepositoryRole(role);
        repository.setEnabled(true);
        return repository;
    }
}
