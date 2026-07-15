package github.luckygc.am.module.storage;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.data.Limit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import github.luckygc.am.common.cleanup.ExpiredDataCleaner;
import github.luckygc.am.common.cleanup.ExpiredDataCleanupResult;
import github.luckygc.am.common.storage.FileStorageService;
import github.luckygc.am.module.storage.repository.StorageObjectDataRepository;

@Component
public class StorageObjectExpiredDataCleaner implements ExpiredDataCleaner {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(StorageObjectExpiredDataCleaner.class);
    private static final String CLEANER_NAME = "storage_object";
    private static final Limit CLEANUP_LIMIT = Limit.of(100);

    private final StorageObjectDataRepository repository;
    private final FileStorageService fileStorageService;

    public StorageObjectExpiredDataCleaner(
            StorageObjectDataRepository repository, FileStorageService fileStorageService) {
        this.repository = repository;
        this.fileStorageService = fileStorageService;
    }

    @Override
    public String name() {
        return CLEANER_NAME;
    }

    @Override
    public ExpiredDataCleanupResult cleanupExpired(LocalDateTime now) {
        List<StorageObject> expiredObjects = repository.findExpired(now, CLEANUP_LIMIT);
        int deletedCount = 0;
        for (StorageObject storageObject : expiredObjects) {
            try {
                fileStorageService.deleteObject(
                        storageObject.getBucketName(), storageObject.getObjectKey());
                repository.delete(storageObject);
                deletedCount++;
            } catch (IOException | RuntimeException exception) {
                LOGGER.error("清理过期存储对象失败: storageObjectId={}", storageObject.getId(), exception);
            }
        }
        return new ExpiredDataCleanupResult(CLEANER_NAME, deletedCount);
    }
}
