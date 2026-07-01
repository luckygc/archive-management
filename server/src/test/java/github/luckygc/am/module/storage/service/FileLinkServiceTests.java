package github.luckygc.am.module.storage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.module.storage.FileLink;
import github.luckygc.am.module.storage.FileLinkTargetType;
import github.luckygc.am.module.storage.repository.FileLinkDataRepository;

@DisplayName("文件短链服务")
class FileLinkServiceTests {

    private FileLinkDataRepository fileLinkRepository;
    private FileLinkCodeGenerator codeGenerator;
    private FileLinkService fileLinkService;

    @BeforeEach
    void setUp() {
        fileLinkRepository = mock(FileLinkDataRepository.class);
        codeGenerator = mock(FileLinkCodeGenerator.class);
        fileLinkService =
                new FileLinkService(
                        fileLinkRepository,
                        codeGenerator,
                        Clock.fixed(
                                Instant.parse("2026-07-01T02:00:00Z"), ZoneId.of("Asia/Shanghai")));
    }

    @Test
    @DisplayName("创建用户绑定短链时写入当前用户和过期时间")
    void createUserLinkShouldBindCurrentUserAndExpiresAt() {
        when(codeGenerator.generate()).thenReturn("AbCdEfGhIjKlMnOpQrStUv");
        when(fileLinkRepository.insert(any(FileLink.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<FileLink> linkCaptor = ArgumentCaptor.forClass(FileLink.class);

        FileLinkService.FileLinkCreated created =
                fileLinkService.createUserLink(
                        FileLinkTargetType.ARCHIVE_ITEM_ELECTRONIC_FILE,
                        10L,
                        30L,
                        Duration.ofMinutes(5),
                        9L);

        assertThat(created.code()).isEqualTo("AbCdEfGhIjKlMnOpQrStUv");
        assertThat(created.expiresAt()).isEqualTo(LocalDateTime.of(2026, 7, 1, 10, 5));
        org.mockito.Mockito.verify(fileLinkRepository).insert(linkCaptor.capture());
        FileLink link = linkCaptor.getValue();
        assertThat(link.getAllowedUserId()).isEqualTo(9L);
        assertThat(link.getTargetType()).isEqualTo(FileLinkTargetType.ARCHIVE_ITEM_ELECTRONIC_FILE);
        assertThat(link.getTargetParentId()).isEqualTo(10L);
        assertThat(link.getTargetId()).isEqualTo(30L);
        assertThat(link.getExpiresAt()).isEqualTo(LocalDateTime.of(2026, 7, 1, 10, 5));
    }

    @Test
    @DisplayName("内部入口允许绑定用户访问自己的短链")
    void resolveInternalShouldAllowBoundUser() {
        FileLink link = activeLink();
        link.setAllowedUserId(9L);
        when(fileLinkRepository.findByCode("abc")).thenReturn(Optional.of(link));

        FileLinkService.FileLinkTarget target = fileLinkService.resolveInternal("abc", 9L);

        assertThat(target.targetType()).isEqualTo(FileLinkTargetType.ARCHIVE_ITEM_ELECTRONIC_FILE);
        assertThat(target.targetParentId()).isEqualTo(10L);
        assertThat(target.targetId()).isEqualTo(30L);
    }

    @Test
    @DisplayName("内部入口拒绝其他用户访问用户绑定短链")
    void resolveInternalShouldRejectOtherUser() {
        FileLink link = activeLink();
        link.setAllowedUserId(9L);
        when(fileLinkRepository.findByCode("abc")).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> fileLinkService.resolveInternal("abc", 8L))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception ->
                                assertThat(exception.getStatusCode())
                                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("公开入口拒绝用户绑定短链")
    void resolvePublicShouldRejectUserBoundLink() {
        FileLink link = activeLink();
        link.setAllowedUserId(9L);
        when(fileLinkRepository.findByCode("abc")).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> fileLinkService.resolvePublic("abc"))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception ->
                                assertThat(exception.getStatusCode())
                                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("过期短链不可访问")
    void resolveInternalShouldRejectExpiredLink() {
        FileLink link = activeLink();
        link.setExpiresAt(LocalDateTime.of(2026, 7, 1, 9, 59));
        when(fileLinkRepository.findByCode("abc")).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> fileLinkService.resolveInternal("abc", 9L))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception ->
                                assertThat(exception.getStatusCode())
                                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    private static FileLink activeLink() {
        FileLink link = new FileLink();
        link.setCode("abc");
        link.setTargetType(FileLinkTargetType.ARCHIVE_ITEM_ELECTRONIC_FILE);
        link.setTargetParentId(10L);
        link.setTargetId(30L);
        link.setExpiresAt(LocalDateTime.of(2026, 7, 1, 10, 5));
        return link;
    }
}
