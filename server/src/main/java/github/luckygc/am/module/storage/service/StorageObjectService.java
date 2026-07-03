package github.luckygc.am.module.storage.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.storage.FileStorageResource;
import github.luckygc.am.common.storage.FileStorageService;
import github.luckygc.am.common.storage.StorageType;
import github.luckygc.am.module.storage.StorageObject;
import github.luckygc.am.module.storage.repository.StorageObjectDataRepository;

@Service
public class StorageObjectService {

    private final StorageObjectDataRepository storageObjectRepository;
    private final FileStorageService fileStorageService;
    private final Clock clock;

    public StorageObjectService(
            StorageObjectDataRepository storageObjectRepository,
            FileStorageService fileStorageService,
            Clock clock) {
        this.storageObjectRepository = storageObjectRepository;
        this.fileStorageService = fileStorageService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public StorageObjectDto getActiveObject(Long storageObjectId) {
        StorageObject storageObject =
                storageObjectRepository
                        .findById(storageObjectId)
                        .orElseThrow(
                                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文件记录不存在"));
        if (storageObject.getDeletedAt() != null || isExpired(storageObject)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件记录不存在");
        }
        return toDto(storageObject);
    }

    @Transactional(readOnly = true)
    public StorageObjectDto getActiveObjectForOwner(Long storageObjectId, Long ownerUserId) {
        StorageObject storageObject =
                storageObjectRepository
                        .findById(storageObjectId)
                        .orElseThrow(
                                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文件记录不存在"));
        if (storageObject.getDeletedAt() != null
                || isExpired(storageObject)
                || storageObject.getCreatedBy() == null
                || !storageObject.getCreatedBy().equals(ownerUserId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件记录不存在");
        }
        return toDto(storageObject);
    }

    @Transactional(readOnly = true)
    public StorageObjectDownload openObject(Long storageObjectId) {
        StorageObjectDto storageObject = getActiveObject(storageObjectId);
        try {
            FileStorageResource resource =
                    fileStorageService.getObject(
                            storageObject.storageType(),
                            storageObject.bucketName(),
                            storageObject.objectKey());
            return new StorageObjectDownload(storageObject.originalFilename(), resource);
        } catch (java.io.IOException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件内容不存在", exception);
        }
    }

    private StorageObjectDto toDto(StorageObject storageObject) {
        return new StorageObjectDto(
                storageObject.getId(),
                storageObject.getStorageType(),
                storageObject.getBucketName(),
                storageObject.getObjectKey(),
                storageObject.getOriginalFilename(),
                storageObject.getFileSize(),
                storageObject.getContentType(),
                storageObject.getChecksumSha256(),
                storageObject.getCreatedBy());
    }

    private boolean isExpired(StorageObject storageObject) {
        LocalDateTime expiresAt = storageObject.getExpiresAt();
        return expiresAt != null && !expiresAt.isAfter(LocalDateTime.now(clock));
    }

    public record StorageObjectDto(
            Long id,
            StorageType storageType,
            String bucketName,
            String objectKey,
            String originalFilename,
            long fileSize,
            @Nullable String contentType,
            @Nullable String checksumSha256,
            @Nullable Long createdBy) {}

    public record StorageObjectDownload(String originalFilename, FileStorageResource resource) {}
}
