package github.luckygc.am.module.archive.item.service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.api.CollectionResponse;
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.common.storage.FileStorageResource;
import github.luckygc.am.module.archive.item.ArchiveItem;
import github.luckygc.am.module.archive.item.ArchiveItemAudit;
import github.luckygc.am.module.archive.item.repository.ArchiveItemAuditDataRepository;
import github.luckygc.am.module.archive.item.repository.ArchiveItemDataRepository;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;
import github.luckygc.am.module.storage.service.StorageObjectService;
import github.luckygc.am.module.storage.service.StorageObjectService.StorageObjectDownload;
import github.luckygc.am.module.storage.service.StorageObjectService.StorageObjectDto;

@Service
public class ArchiveItemElectronicFileService {

    private static final String DEFAULT_USAGE_TYPE = "DEFAULT";
    private static final String AUDIT_OPERATION_DOWNLOAD = "DOWNLOAD";

    private final ArchiveMapper archiveMapper;
    private final StorageObjectService storageObjectService;
    private final ArchiveItemDataRepository archiveItemRepository;
    private final ArchiveItemAuditDataRepository archiveItemAuditRepository;
    private final AuthorizationPermissionService permissionService;
    private final ArchiveItemRoutingService archiveItemRoutingService;

    public ArchiveItemElectronicFileService(
            ArchiveMapper archiveMapper,
            StorageObjectService storageObjectService,
            ArchiveItemDataRepository archiveItemRepository,
            ArchiveItemAuditDataRepository archiveItemAuditRepository,
            AuthorizationPermissionService permissionService,
            ArchiveItemRoutingService archiveItemRoutingService) {
        this.archiveMapper = archiveMapper;
        this.storageObjectService = storageObjectService;
        this.archiveItemRepository = archiveItemRepository;
        this.archiveItemAuditRepository = archiveItemAuditRepository;
        this.permissionService = permissionService;
        this.archiveItemRoutingService = archiveItemRoutingService;
    }

    @Transactional
    public ArchiveItemElectronicFileResponse bindFile(
            Long archiveItemId, @Nullable ArchiveItemElectronicFileRequest request, Long userId) {
        requirePermission(userId, "archive:file:bind");
        if (request == null) {
            throw new BadRequestException("请求体不能为空");
        }
        if (request.storageObjectId() == null || request.storageObjectId() <= 0) {
            throw new BadRequestException("文件记录不能为空", "storageObjectId", "文件记录不能为空");
        }
        archiveItemRoutingService.assertItemInDataScope(archiveItemId, userId);
        ensureArchiveItemExists(archiveItemId);
        StorageObjectDto storageObject =
                storageObjectService.getActiveObject(request.storageObjectId());
        String usageType = usageType(request.usageType());
        int displayOrder = request.displayOrder() == null ? 0 : request.displayOrder();
        Long electronicFileId;
        try {
            electronicFileId =
                    archiveMapper.insertArchiveItemElectronicFile(
                            archiveItemId, storageObject.id(), usageType, displayOrder, userId);
        } catch (DataIntegrityViolationException exception) {
            throw new BadRequestException("文件已绑定");
        }
        return new ArchiveItemElectronicFileResponse(
                electronicFileId,
                archiveItemId,
                storageObject.id(),
                usageType,
                displayOrder,
                storageObject.originalFilename(),
                storageObject.fileSize(),
                storageObject.contentType(),
                storageObject.checksumSha256(),
                LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public CollectionResponse<ArchiveItemElectronicFileResponse> listFiles(
            Long archiveItemId, Long userId) {
        requirePermission(userId, "archive:item:read");
        archiveItemRoutingService.assertItemInDataScope(archiveItemId, userId);
        ensureArchiveItemExists(archiveItemId);
        return CollectionResponse.of(
                archiveMapper.listArchiveItemElectronicFiles(archiveItemId).stream()
                        .map(this::toResponse)
                        .toList());
    }

    @Transactional
    public void unbindFile(Long archiveItemId, Long electronicFileId, Long userId) {
        requirePermission(userId, "archive:file:bind");
        archiveItemRoutingService.assertItemInDataScope(archiveItemId, userId);
        int deleted =
                archiveMapper.deleteArchiveItemElectronicFile(archiveItemId, electronicFileId);
        if (deleted == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件绑定不存在");
        }
    }

    @Transactional
    public ArchiveItemFileDownload downloadFile(
            Long archiveItemId, Long electronicFileId, Long userId) {
        requirePermission(userId, "archive:file:download");
        archiveItemRoutingService.assertItemInDataScope(archiveItemId, userId);
        Long storageObjectId =
                archiveMapper.getArchiveItemElectronicFileStorageObjectId(
                        archiveItemId, electronicFileId);
        if (storageObjectId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件绑定不存在");
        }
        ArchiveItem archiveItem = loadArchiveItem(archiveItemId);
        StorageObjectDownload download = storageObjectService.openObject(storageObjectId);
        insertDownloadAudit(archiveItem, electronicFileId, storageObjectId, userId);
        return new ArchiveItemFileDownload(download.originalFilename(), download.resource());
    }

    private void insertDownloadAudit(
            ArchiveItem archiveItem, Long electronicFileId, Long storageObjectId, Long userId) {
        ArchiveItemAudit audit = new ArchiveItemAudit();
        audit.setSourceTableName("am_archive_item");
        audit.setSourceRecordId(archiveItem.getId());
        audit.setArchiveItemId(archiveItem.getId());
        audit.setFondsCode(archiveItem.getFondsCode());
        audit.setCategoryCode(archiveItem.getCategoryCode());
        audit.setOperationType(AUDIT_OPERATION_DOWNLOAD);
        audit.setOperationReason(
                "electronicFileId=" + electronicFileId + ", storageObjectId=" + storageObjectId);
        audit.setOperatedBy(userId);
        archiveItemAuditRepository.insert(audit);
    }

    private ArchiveItem loadArchiveItem(Long archiveItemId) {
        return archiveItemRepository
                .findById(archiveItemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "档案条目不存在"));
    }

    private void requirePermission(Long userId, String permissionCode) {
        userId = AuthenticatedUsers.requireResolvedUserId(userId);
        if (!permissionService.hasPermission(userId, permissionCode)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "权限不足");
        }
    }

    private void ensureArchiveItemExists(Long archiveItemId) {
        if (archiveItemId == null
                || archiveItemId <= 0
                || archiveMapper.getArchiveItem(archiveItemId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "档案条目不存在");
        }
    }

    private String usageType(@Nullable String usageType) {
        return StringUtils.defaultIfBlank(StringUtils.trimToNull(usageType), DEFAULT_USAGE_TYPE)
                .toUpperCase(java.util.Locale.ROOT);
    }

    private ArchiveItemElectronicFileResponse toResponse(Map<String, Object> row) {
        return new ArchiveItemElectronicFileResponse(
                number(row, "id").longValue(),
                number(row, "archiveItemId").longValue(),
                number(row, "storageObjectId").longValue(),
                string(row, "usageType"),
                number(row, "displayOrder").intValue(),
                string(row, "originalFilename"),
                number(row, "fileSize").longValue(),
                stringOrNull(row, "contentType"),
                stringOrNull(row, "checksumSha256"),
                dateTime(row, "createdAt"));
    }

    private Number number(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value instanceof Number number) {
            return number;
        }
        throw new IllegalStateException("缺少数值字段：" + key);
    }

    private String string(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value instanceof String string) {
            return string;
        }
        throw new IllegalStateException("缺少文本字段：" + key);
    }

    private @Nullable String stringOrNull(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value instanceof String string ? string : null;
    }

    private LocalDateTime dateTime(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        throw new IllegalStateException("缺少时间字段：" + key);
    }

    private @Nullable Object value(Map<String, Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        return row.get(JdbcUtils.convertPropertyNameToUnderscoreName(key));
    }

    public record ArchiveItemElectronicFileRequest(
            @Nullable Long storageObjectId,
            @Nullable String usageType,
            @Nullable Integer displayOrder) {}

    public record ArchiveItemElectronicFileResponse(
            Long id,
            Long archiveItemId,
            Long storageObjectId,
            String usageType,
            int displayOrder,
            String originalFilename,
            long fileSize,
            @Nullable String contentType,
            @Nullable String checksumSha256,
            LocalDateTime createdAt) {}

    public record ArchiveItemFileDownload(String originalFilename, FileStorageResource resource) {}
}
