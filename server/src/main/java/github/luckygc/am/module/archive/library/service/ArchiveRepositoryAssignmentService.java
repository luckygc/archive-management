package github.luckygc.am.module.archive.library.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.ArchiveObjectType;
import github.luckygc.am.module.archive.item.ArchiveItem;
import github.luckygc.am.module.archive.item.ArchiveVolume;
import github.luckygc.am.module.archive.item.repository.ArchiveItemDataRepository;
import github.luckygc.am.module.archive.item.repository.ArchiveVolumeDataRepository;
import github.luckygc.am.module.archive.item.service.ArchiveItemReadService;
import github.luckygc.am.module.archive.item.service.ArchiveVolumeService;
import github.luckygc.am.module.archive.library.ArchiveRepository;
import github.luckygc.am.module.archive.library.ArchiveRepositoryChangeHistory;
import github.luckygc.am.module.archive.library.ArchiveRepositoryRole;
import github.luckygc.am.module.archive.library.repository.ArchiveRepositoryChangeHistoryDataRepository;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@Service
public class ArchiveRepositoryAssignmentService {

    private final ArchiveRepositoryService archiveRepositoryService;
    private final ArchiveRepositoryChangeHistoryDataRepository historyRepository;
    private final ArchiveItemDataRepository itemRepository;
    private final ArchiveVolumeDataRepository volumeRepository;
    private final ArchiveItemReadService itemReadService;
    private final ArchiveVolumeService volumeService;
    private final AuthorizationPermissionService permissionService;
    private final Clock clock;

    public ArchiveRepositoryAssignmentService(
            ArchiveRepositoryService archiveRepositoryService,
            ArchiveRepositoryChangeHistoryDataRepository historyRepository,
            ArchiveItemDataRepository itemRepository,
            ArchiveVolumeDataRepository volumeRepository,
            ArchiveItemReadService itemReadService,
            ArchiveVolumeService volumeService,
            AuthorizationPermissionService permissionService,
            Clock clock) {
        this.archiveRepositoryService = archiveRepositoryService;
        this.historyRepository = historyRepository;
        this.itemRepository = itemRepository;
        this.volumeRepository = volumeRepository;
        this.itemReadService = itemReadService;
        this.volumeService = volumeService;
        this.permissionService = permissionService;
        this.clock = clock;
    }

    @Transactional
    public ChangeArchiveRepositoryResponse changeItemRepository(
            Long itemId, ChangeArchiveRepositoryRequest request, Long userId) {
        requireUpdatePermission(userId);
        itemReadService.assertItemInDataScope(itemId, userId);
        ArchiveItem item =
                itemRepository
                        .findById(itemId)
                        .orElseThrow(
                                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "档案条目不存在"));
        Long fromRepositoryId = item.getRepositoryId();
        ArchiveRepository target = target(request);
        if (!fromRepositoryId.equals(target.getId())) {
            assertTransition(archiveRepositoryService.getRequired(fromRepositoryId), target);
            item.setRepositoryId(target.getId());
            itemRepository.update(item);
            insertHistory(
                    ArchiveObjectType.ITEM,
                    itemId,
                    fromRepositoryId,
                    target.getId(),
                    request,
                    userId);
        }
        return new ChangeArchiveRepositoryResponse(
                ArchiveObjectType.ITEM, itemId, fromRepositoryId, target.getId());
    }

    @Transactional
    public ChangeArchiveRepositoryResponse changeVolumeRepository(
            Long volumeId, ChangeArchiveRepositoryRequest request, Long userId) {
        requireUpdatePermission(userId);
        volumeService.assertVolumeInDataScope(volumeId, userId);
        ArchiveVolume volume =
                volumeRepository
                        .findById(volumeId)
                        .orElseThrow(
                                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "案卷不存在"));
        Long fromRepositoryId = volume.getRepositoryId();
        ArchiveRepository target = target(request);
        if (!fromRepositoryId.equals(target.getId())) {
            assertTransition(archiveRepositoryService.getRequired(fromRepositoryId), target);
            volume.setRepositoryId(target.getId());
            volumeRepository.update(volume);
            insertHistory(
                    ArchiveObjectType.VOLUME,
                    volumeId,
                    fromRepositoryId,
                    target.getId(),
                    request,
                    userId);
        }
        return new ChangeArchiveRepositoryResponse(
                ArchiveObjectType.VOLUME, volumeId, fromRepositoryId, target.getId());
    }

    private ArchiveRepository target(ChangeArchiveRepositoryRequest request) {
        if (request == null || request.targetRepositoryId() == null) {
            throw new BadRequestException("目标业务库不能为空");
        }
        return archiveRepositoryService.getEnabled(request.targetRepositoryId());
    }

    private void assertTransition(ArchiveRepository source, ArchiveRepository target) {
        if (source.getRepositoryRole() == ArchiveRepositoryRole.HOLDING
                && target.getRepositoryRole() == ArchiveRepositoryRole.INTAKE) {
            throw new BadRequestException("正式档案不能退回预归档库");
        }
    }

    private void insertHistory(
            ArchiveObjectType archiveType,
            Long archiveId,
            Long fromRepositoryId,
            Long toRepositoryId,
            ChangeArchiveRepositoryRequest request,
            Long userId) {
        ArchiveRepositoryChangeHistory history = new ArchiveRepositoryChangeHistory();
        history.setArchiveType(archiveType);
        history.setArchiveId(archiveId);
        history.setFromRepositoryId(fromRepositoryId);
        history.setToRepositoryId(toRepositoryId);
        history.setBusinessType(StringUtils.trimToNull(request.businessType()));
        history.setBusinessId(request.businessId());
        history.setReason(StringUtils.trimToNull(request.reason()));
        history.setOperatedBy(userId);
        history.setOperatedAt(LocalDateTime.now(clock));
        historyRepository.insert(history);
    }

    private void requireUpdatePermission(Long userId) {
        userId = AuthenticatedUsers.requireResolvedUserId(userId);
        if (!permissionService.hasPermission(
                userId, AuthorizationPermissionCode.ARCHIVE_ITEM_UPDATE.code())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "权限不足");
        }
    }

    public record ChangeArchiveRepositoryRequest(
            @Nullable Long targetRepositoryId,
            @Nullable String businessType,
            @Nullable Long businessId,
            @Nullable String reason) {}

    public record ChangeArchiveRepositoryResponse(
            ArchiveObjectType archiveType,
            Long archiveId,
            Long fromRepositoryId,
            Long toRepositoryId) {}
}
