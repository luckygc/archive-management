package github.luckygc.am.module.storage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.storage.FileStorageResource;
import github.luckygc.am.common.storage.FileStorageService;
import github.luckygc.am.common.storage.StorageType;
import github.luckygc.am.module.storage.StorageObject;
import github.luckygc.am.module.storage.repository.StorageObjectDataRepository;

@DisplayName("存储对象记录服务")
class StorageObjectServiceTests {

    private StorageObjectDataRepository storageObjectRepository;
    private FileStorageService fileStorageService;
    private StorageObjectService storageObjectService;

    @BeforeEach
    void setUp() {
        storageObjectRepository = mock(StorageObjectDataRepository.class);
        fileStorageService = mock(FileStorageService.class);
        storageObjectService =
                new StorageObjectService(storageObjectRepository, fileStorageService);
    }

    @Test
    @DisplayName("过期文件记录不再视为有效文件")
    void getActiveObjectShouldRejectExpiredObject() {
        StorageObject object = storageObject();
        object.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(storageObjectRepository.findById(20L)).thenReturn(Optional.of(object));

        assertThatThrownBy(() -> storageObjectService.getActiveObject(20L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("文件记录不存在");
    }

    @Test
    @DisplayName("打开文件时按存储对象记录路由")
    void openObjectShouldRouteByStorageObjectRecord() throws Exception {
        FileStorageResource resource =
                new FileStorageResource(
                        StorageType.LOCAL,
                        "archive",
                        "2026/06/demo.pdf",
                        new ByteArrayInputStream(
                                "demo".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        4,
                        "application/pdf");
        when(storageObjectRepository.findById(20L)).thenReturn(Optional.of(storageObject()));
        when(fileStorageService.getObject(StorageType.LOCAL, "archive", "2026/06/demo.pdf"))
                .thenReturn(resource);

        StorageObjectService.StorageObjectDownload download = storageObjectService.openObject(20L);

        assertThat(download.originalFilename()).isEqualTo("demo.pdf");
        assertThat(download.resource()).isSameAs(resource);
        verify(fileStorageService).getObject(StorageType.LOCAL, "archive", "2026/06/demo.pdf");
    }

    private StorageObject storageObject() {
        StorageObject object = new StorageObject();
        object.setId(20L);
        object.setStorageType(StorageType.LOCAL);
        object.setBucketName("archive");
        object.setObjectKey("2026/06/demo.pdf");
        object.setOriginalFilename("demo.pdf");
        object.setFileSize(1024L);
        object.setContentType("application/pdf");
        object.setChecksumSha256("abc");
        object.setCreatedAt(LocalDateTime.of(2026, 6, 30, 10, 0));
        return object;
    }
}
