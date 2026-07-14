package github.luckygc.am.module.storage.service;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.common.storage.FileStorageResource;
import github.luckygc.am.common.storage.FileStorageService;
import github.luckygc.am.common.storage.ObjectKeys;
import github.luckygc.am.common.storage.StorageObjectInfo;
import github.luckygc.am.module.storage.StorageObject;
import github.luckygc.am.module.storage.repository.StorageObjectDataRepository;

@Service
public class StorageObjectService {

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

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

    @Transactional
    public StorageObjectDto storeObject(StoreStorageObjectCommand command, Long userId) {
        userId = AuthenticatedUsers.requireResolvedUserId(userId);
        if (command == null) {
            throw new BadRequestException("文件不能为空");
        }
        if (command.contentLength() <= 0) {
            throw new BadRequestException("文件不能为空", "file", "文件不能为空");
        }
        if (command.inputStream() == null) {
            throw new BadRequestException("文件内容不能为空", "file", "文件内容不能为空");
        }
        String originalFilename = normalizeOriginalFilename(command.originalFilename());
        String contentType =
                StringUtils.defaultIfBlank(
                        StringUtils.trimToNull(command.contentType()), DEFAULT_CONTENT_TYPE);
        String objectKey = ObjectKeys.generate(LocalDate.now(clock), originalFilename);
        MessageDigest digest = DigestUtils.getSha256Digest();
        DigestInputStream inputStream = new DigestInputStream(command.inputStream(), digest);
        try {
            StorageObjectInfo objectInfo =
                    fileStorageService.putObject(
                            objectKey, inputStream, command.contentLength(), contentType);
            String checksumSha256 = Hex.encodeHexString(digest.digest());
            StorageObject storageObject = new StorageObject();
            storageObject.setBucketName(objectInfo.bucketName());
            storageObject.setObjectKey(objectInfo.objectKey());
            storageObject.setOriginalFilename(originalFilename);
            storageObject.setFileSize(objectInfo.contentLength());
            storageObject.setContentType(
                    StringUtils.defaultIfBlank(objectInfo.contentType(), contentType));
            storageObject.setFileExtension(fileExtension(originalFilename));
            storageObject.setChecksumSha256(checksumSha256);
            storageObject.setEtag(objectInfo.eTag());
            storageObject.setCreatedBy(userId);
            return toDto(storageObjectRepository.insert(storageObject));
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "文件保存失败", exception);
        }
    }

    @Transactional(readOnly = true)
    public StorageObjectDto getActiveObject(Long storageObjectId) {
        return loadActiveObject(storageObjectId);
    }

    private StorageObjectDto loadActiveObject(Long storageObjectId) {
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
        StorageObjectDto storageObject = loadActiveObject(storageObjectId);
        try {
            FileStorageResource resource =
                    fileStorageService.getObject(
                            storageObject.bucketName(), storageObject.objectKey());
            return new StorageObjectDownload(storageObject.originalFilename(), resource);
        } catch (java.io.IOException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件内容不存在", exception);
        }
    }

    private StorageObjectDto toDto(StorageObject storageObject) {
        return new StorageObjectDto(
                storageObject.getId(),
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

    private String normalizeOriginalFilename(@Nullable String originalFilename) {
        String filename =
                StringUtils.defaultIfBlank(StringUtils.trimToNull(originalFilename), "未命名文件");
        filename = filename.replace('\\', '/');
        int separatorIndex = filename.lastIndexOf('/');
        if (separatorIndex >= 0) {
            filename = filename.substring(separatorIndex + 1);
        }
        return StringUtils.defaultIfBlank(StringUtils.trimToNull(filename), "未命名文件");
    }

    private @Nullable String fileExtension(String originalFilename) {
        String extension = StringUtils.trimToNull(FilenameUtils.getExtension(originalFilename));
        if (extension == null) {
            return null;
        }
        extension = StringUtils.lowerCase(extension, Locale.ROOT);
        return extension.length() > 50 ? null : extension;
    }

    public record StoreStorageObjectCommand(
            String originalFilename,
            @Nullable String contentType,
            long contentLength,
            InputStream inputStream) {}

    public record StorageObjectDto(
            Long id,
            String bucketName,
            String objectKey,
            String originalFilename,
            long fileSize,
            @Nullable String contentType,
            @Nullable String checksumSha256,
            @Nullable Long createdBy) {}

    public record StorageObjectDownload(String originalFilename, FileStorageResource resource) {}
}
