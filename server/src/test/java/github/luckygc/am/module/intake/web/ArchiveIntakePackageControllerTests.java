package github.luckygc.am.module.intake.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.module.intake.service.ArchiveIntakePackageParser;
import github.luckygc.am.module.intake.service.ArchiveIntakePackageProcessingService.AcceptanceReview;
import github.luckygc.am.module.intake.service.ArchiveIntakePackageService;
import github.luckygc.am.module.intake.web.ArchiveIntakePackageController.RejectArchiveIntakePackageRequest;

@DisplayName("档案信息包 API")
class ArchiveIntakePackageControllerTests {

    private final ArchiveIntakePackageService service = mock(ArchiveIntakePackageService.class);
    private final ArchiveIntakePackageController controller =
            new ArchiveIntakePackageController(service);

    @Test
    @DisplayName("multipart 文件转交包资源服务")
    void createShouldPassMultipartFile() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("package.zip");
        when(file.getContentType()).thenReturn("application/zip");
        when(file.getSize()).thenReturn(3L);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3}));

        controller.create(file, authentication(9L));

        verify(service)
                .receive(
                        eq("package.zip"),
                        eq("application/zip"),
                        org.mockito.ArgumentMatchers.any(Path.class),
                        eq(3L),
                        eq(9L));
    }

    @Test
    @DisplayName("读取内容前拒绝超过 50 MiB 的文件")
    void createShouldRejectOversizedFileBeforeReading() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn((long) ArchiveIntakePackageParser.MAX_COMPRESSED_BYTES + 1);

        assertThatThrownBy(() -> controller.create(file, authentication(9L)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("50 MiB");
    }

    @Test
    @DisplayName("人工验收、退回和原包下载均传递当前用户")
    void reviewActionsShouldPassCurrentUser() {
        AcceptanceReview review = new AcceptanceReview(true, true, true, true, true, "交接完成");

        controller.accept(10L, review, authentication(9L));
        controller.reject(
                10L, new RejectArchiveIntakePackageRequest("移交清单不一致"), authentication(9L));
        controller.createDownloadLink(10L, authentication(9L));

        verify(service).accept(10L, review, 9L);
        verify(service).reject(10L, "移交清单不一致", 9L);
        verify(service).createDownloadLink(10L, 9L);
    }

    private Authentication authentication(Long userId) {
        Authentication authentication = mock(Authentication.class);
        AuthenticatedUser user =
                new AuthenticatedUser() {
                    @Override
                    public Long id() {
                        return userId;
                    }

                    @Override
                    public String displayName() {
                        return "测试用户";
                    }
                };
        when(authentication.getPrincipal()).thenReturn(user);
        return authentication;
    }
}
