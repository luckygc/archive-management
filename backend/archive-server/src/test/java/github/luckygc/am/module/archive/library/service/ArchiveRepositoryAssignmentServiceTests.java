package github.luckygc.am.module.archive.library.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
    @DisplayName("进入移交库只改变虚拟业务库并记录历史")
    void changeItemRepositoryShouldNotTouchPhysicalLocation() {
        ArchiveItem item = new ArchiveItem();
        item.setId(31L);
        item.setRepositoryId(2L);
        when(itemRepository.findById(31L)).thenReturn(Optional.of(item));
        ArchiveRepository transfer = new ArchiveRepository();
        transfer.setId(3L);
        transfer.setEnabled(true);
        when(repositoryService.getEnabled(3L)).thenReturn(transfer);

        var response =
                service.changeItemRepository(
                        31L, new ChangeArchiveRepositoryRequest(3L, "TRANSFER", 88L, "发起移交"), 9L);

        assertThat(response.archiveType()).isEqualTo(ArchiveObjectType.ITEM);
        assertThat(item.getRepositoryId()).isEqualTo(3L);
        verify(itemRepository).update(item);
        verify(historyRepository).insert(any(ArchiveRepositoryChangeHistory.class));
    }

    @Test
    @DisplayName("目标业务库未变化时不重复写历史")
    void sameRepositoryShouldNotWriteHistory() {
        ArchiveItem item = new ArchiveItem();
        item.setId(31L);
        item.setRepositoryId(3L);
        when(itemRepository.findById(31L)).thenReturn(Optional.of(item));
        ArchiveRepository transfer = new ArchiveRepository();
        transfer.setId(3L);
        transfer.setEnabled(true);
        when(repositoryService.getEnabled(3L)).thenReturn(transfer);

        service.changeItemRepository(
                31L, new ChangeArchiveRepositoryRequest(3L, null, null, null), 9L);

        verifyNoInteractions(historyRepository);
    }
}
