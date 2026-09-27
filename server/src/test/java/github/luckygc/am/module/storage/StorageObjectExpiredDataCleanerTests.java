package github.luckygc.am.module.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.data.Limit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import github.luckygc.am.common.cleanup.ExpiredDataCleanupResult;
import github.luckygc.am.common.storage.FileStorageService;
import github.luckygc.am.module.storage.repository.StorageObjectDataRepository;

@DisplayName("过期存储对象清理器")
class StorageObjectExpiredDataCleanerTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 15, 10, 20);

    private StorageObjectDataRepository repository;
    private FileStorageService fileStorageService;
    private StorageObjectExpiredDataCleaner cleaner;

    @BeforeEach
    void setUp() {
        repository = mock(StorageObjectDataRepository.class);
        fileStorageService = mock(FileStorageService.class);
        cleaner = new StorageObjectExpiredDataCleaner(repository, fileStorageService);
    }

    @Test
    @DisplayName("清理器先删除 S3 对象再删除本地记录")
    void cleanerShouldDeleteRecordOnlyAfterObject() throws IOException {
        StorageObject expired = expiredObject(31L, "archive", "tmp/export.xlsx");
        when(repository.findExpired(NOW, Limit.of(100))).thenReturn(List.of(expired));

        ExpiredDataCleanupResult result = cleaner.cleanupExpired(NOW);

        InOrder order = inOrder(fileStorageService, repository);
        order.verify(fileStorageService).deleteObject("archive", "tmp/export.xlsx");
        order.verify(repository).delete(expired);
        assertThat(result.cleanerName()).isEqualTo("storage_object");
        assertThat(result.deletedCount()).isEqualTo(1);
        verify(repository).findExpired(NOW, Limit.of(100));
    }

    @Test
    @DisplayName("S3 删除失败时保留本地记录供下次重试")
    void cleanerShouldKeepRecordWhenObjectDeletionFails() throws IOException {
        StorageObject expired = expiredObject(32L, "archive", "tmp/failed.xlsx");
        when(repository.findExpired(NOW, Limit.of(100))).thenReturn(List.of(expired));
        doThrow(new IOException("S3 unavailable"))
                .when(fileStorageService)
                .deleteObject("archive", "tmp/failed.xlsx");

        ExpiredDataCleanupResult result = cleaner.cleanupExpired(NOW);

        verify(repository, never()).delete(expired);
        assertThat(result.deletedCount()).isZero();
    }

    @Test
    @DisplayName("单个对象清理失败不阻塞同批其他对象")
    void cleanerShouldContinueAfterObjectDeletionFailure() throws IOException {
        StorageObject failed = expiredObject(32L, "archive", "tmp/failed.xlsx");
        StorageObject succeeded = expiredObject(33L, "archive", "tmp/succeeded.xlsx");
        when(repository.findExpired(NOW, Limit.of(100))).thenReturn(List.of(failed, succeeded));
        doThrow(new IOException("S3 unavailable"))
                .when(fileStorageService)
                .deleteObject("archive", "tmp/failed.xlsx");

        ExpiredDataCleanupResult result = cleaner.cleanupExpired(NOW);

        verify(repository, never()).delete(failed);
        verify(repository).delete(succeeded);
        assertThat(result.deletedCount()).isEqualTo(1);
    }

    private StorageObject expiredObject(Long id, String bucketName, String objectKey) {
        StorageObject object = new StorageObject();
        object.setId(id);
        object.setBucketName(bucketName);
        object.setObjectKey(objectKey);
        object.setOriginalFilename("export.xlsx");
        object.setFileSize(3);
        object.setExpiresAt(NOW.minusMinutes(1));
        return object;
    }
}
