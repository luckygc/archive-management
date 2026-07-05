package github.luckygc.am.module.storage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.storage.FileStorageResource;
import github.luckygc.am.common.storage.FileStorageService;
import github.luckygc.am.common.storage.StorageObjectInfo;
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
                new StorageObjectService(
                        storageObjectRepository,
                        fileStorageService,
                        Clock.fixed(
                                Instant.parse("2026-07-01T02:00:00Z"), ZoneId.of("Asia/Shanghai")));
    }

    @Test
    @DisplayName("存储上传文件时写入对象存储并登记存储对象记录")
    void storeObjectShouldPutObjectAndInsertStorageObjectRecord() throws Exception {
        when(fileStorageService.putObject(
                        any(String.class), any(InputStream.class), eq(4L), eq("application/pdf")))
                .thenAnswer(
                        invocation -> {
                            String objectKey = invocation.getArgument(0);
                            InputStream inputStream = invocation.getArgument(1);
                            inputStream.transferTo(OutputStream.nullOutputStream());
                            return new StorageObjectInfo(
                                    StorageType.LOCAL,
                                    "archive",
                                    objectKey,
                                    4,
                                    "application/pdf",
                                    Instant.parse("2026-07-01T02:00:00Z"),
                                    "etag-1");
                        });
        when(storageObjectRepository.insert(any(StorageObject.class)))
                .thenAnswer(
                        invocation -> {
                            StorageObject object = invocation.getArgument(0);
                            object.setId(20L);
                            return object;
                        });

        StorageObjectService.StorageObjectDto dto =
                storageObjectService.storeObject(
                        new StorageObjectService.StoreStorageObjectCommand(
                                "合同.pdf",
                                "application/pdf",
                                4,
                                new ByteArrayInputStream(
                                        "demo".getBytes(java.nio.charset.StandardCharsets.UTF_8))),
                        9L);

        assertThat(dto.id()).isEqualTo(20L);
        assertThat(dto.objectKey())
                .matches(
                        "2026/07/01/[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\.pdf");
        assertThat(dto.originalFilename()).isEqualTo("合同.pdf");
        assertThat(dto.fileSize()).isEqualTo(4);
        assertThat(dto.contentType()).isEqualTo("application/pdf");
        assertThat(dto.checksumSha256()).isEqualTo(DigestUtils.sha256Hex("demo"));
        assertThat(dto.createdBy()).isEqualTo(9L);
        verify(fileStorageService)
                .putObject(
                        eq(dto.objectKey()), any(InputStream.class), eq(4L), eq("application/pdf"));
        ArgumentCaptor<StorageObject> storageObjectCaptor =
                ArgumentCaptor.forClass(StorageObject.class);
        verify(storageObjectRepository).insert(storageObjectCaptor.capture());
        assertThat(storageObjectCaptor.getValue().getChecksumSha256())
                .isEqualTo(DigestUtils.sha256Hex("demo"));
    }

    @Test
    @DisplayName("过期文件记录不再视为有效文件")
    void getActiveObjectShouldRejectExpiredObject() {
        StorageObject object = storageObject();
        object.setExpiresAt(LocalDateTime.of(2026, 7, 1, 9, 59));
        when(storageObjectRepository.findById(20L)).thenReturn(Optional.of(object));

        assertThatThrownBy(() -> storageObjectService.getActiveObject(20L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("文件记录不存在");
    }

    @Test
    @DisplayName("只允许创建人继续使用临时存储对象")
    void getActiveObjectForOwnerShouldRejectOtherUserObject() {
        StorageObject object = storageObject();
        object.setCreatedBy(8L);
        when(storageObjectRepository.findById(20L)).thenReturn(Optional.of(object));

        assertThatThrownBy(() -> storageObjectService.getActiveObjectForOwner(20L, 9L))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception ->
                                assertThat(exception.getStatusCode())
                                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("创建人可以继续使用自己的临时存储对象")
    void getActiveObjectForOwnerShouldReturnOwnerObject() {
        StorageObject object = storageObject();
        object.setCreatedBy(9L);
        when(storageObjectRepository.findById(20L)).thenReturn(Optional.of(object));

        StorageObjectService.StorageObjectDto dto =
                storageObjectService.getActiveObjectForOwner(20L, 9L);

        assertThat(dto.id()).isEqualTo(20L);
        assertThat(dto.createdBy()).isEqualTo(9L);
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
