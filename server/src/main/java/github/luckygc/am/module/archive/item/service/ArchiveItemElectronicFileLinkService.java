package github.luckygc.am.module.archive.item.service;

import java.time.Duration;
import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.module.archive.item.service.ArchiveItemElectronicFileService.ArchiveItemFileDownload;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;
import github.luckygc.am.module.storage.FileLinkTargetType;
import github.luckygc.am.module.storage.service.FileLinkService;
import github.luckygc.am.module.storage.service.FileLinkService.FileLinkTarget;
import github.luckygc.am.module.storage.service.FileLinkTargetResolver;
import github.luckygc.am.module.storage.service.StorageObjectService.StorageObjectDownload;

@Service
public class ArchiveItemElectronicFileLinkService implements FileLinkTargetResolver {

    private static final Duration DOWNLOAD_LINK_TTL = Duration.ofMinutes(10);
    private static final String PERMISSION_DOWNLOAD = "archive:item:download-electronic-file";

    private final ArchiveMapper archiveMapper;
    private final AuthorizationPermissionService permissionService;
    private final ArchiveItemRoutingService archiveItemRoutingService;
    private final FileLinkService fileLinkService;
    private final ArchiveItemElectronicFileService electronicFileService;

    public ArchiveItemElectronicFileLinkService(
            ArchiveMapper archiveMapper,
            AuthorizationPermissionService permissionService,
            ArchiveItemRoutingService archiveItemRoutingService,
            FileLinkService fileLinkService,
            ArchiveItemElectronicFileService electronicFileService) {
        this.archiveMapper = archiveMapper;
        this.permissionService = permissionService;
        this.archiveItemRoutingService = archiveItemRoutingService;
        this.fileLinkService = fileLinkService;
        this.electronicFileService = electronicFileService;
    }

    @Transactional
    public DownloadLinkCreated createDownloadLink(
            Long archiveItemId, Long electronicFileId, Long userId) {
        requirePermission(userId, PERMISSION_DOWNLOAD);
        archiveItemRoutingService.assertItemInDataScope(archiveItemId, userId);
        Long storageObjectId =
                archiveMapper.getArchiveItemElectronicFileStorageObjectId(
                        archiveItemId, electronicFileId);
        if (storageObjectId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "档案电子文件不存在");
        }
        FileLinkService.FileLinkCreated created =
                fileLinkService.createUserLink(
                        FileLinkTargetType.ARCHIVE_ITEM_ELECTRONIC_FILE,
                        archiveItemId,
                        electronicFileId,
                        DOWNLOAD_LINK_TTL,
                        userId);
        return new DownloadLinkCreated(created.code(), created.expiresAt());
    }

    @Override
    public FileLinkTargetType targetType() {
        return FileLinkTargetType.ARCHIVE_ITEM_ELECTRONIC_FILE;
    }

    @Override
    public StorageObjectDownload open(FileLinkTarget target, @Nullable Long userId) {
        if (target.targetParentId() == null || userId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件短链目标不存在");
        }
        ArchiveItemFileDownload download =
                electronicFileService.downloadFile(
                        target.targetParentId(), target.targetId(), userId);
        return new StorageObjectDownload(download.originalFilename(), download.resource());
    }

    private void requirePermission(Long userId, String permissionCode) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        if (!permissionService.hasPermission(userId, permissionCode)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "权限不足");
        }
    }

    public record DownloadLinkCreated(String code, LocalDateTime expiresAt) {}
}
