package github.luckygc.am.module.archive.item.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import jakarta.data.page.PageRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ResponseStatus;

import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.module.archive.item.service.ArchiveVolumeService;
import github.luckygc.am.module.archive.item.service.ArchiveVolumeService.AddItemToVolumeRequest;
import github.luckygc.am.module.archive.item.service.ArchiveVolumeService.ArchiveVolumeResponse;

@DisplayName("案卷 HTTP 入口")
class ArchiveVolumeControllerTests {

    private final ArchiveVolumeService service = mock(ArchiveVolumeService.class);
    private final ArchiveVolumeController controller = new ArchiveVolumeController(service);

    @Test
    @DisplayName("案卷列表只返回项目 cursor 分页合同并传递 URL 分页参数")
    void listVolumesUsesProjectCursorPageAndBindsFilters() {
        PageRequest pageRequest = PageRequest.ofSize(100);
        @SuppressWarnings("unchecked")
        CursorPageResponse<ArchiveVolumeResponse> page = mock(CursorPageResponse.class);
        when(service.listVolumes("F001", "ACCOUNTING", pageRequest, 8L)).thenReturn(page);

        CursorPageResponse<ArchiveVolumeResponse> response =
                controller.listVolumes("F001", "ACCOUNTING", pageRequest, authentication(8L));

        assertThat(response).isSameAs(page);
        verify(service).listVolumes("F001", "ACCOUNTING", pageRequest, 8L);
    }

    @Test
    @DisplayName("加入档案动作成功返回 204")
    void addItemActionReturnsNoContent() throws Exception {
        Method method =
                ArchiveVolumeController.class.getDeclaredMethod(
                        "addItemToVolume",
                        Long.class,
                        AddItemToVolumeRequest.class,
                        Authentication.class);

        assertThat(method.getAnnotation(ResponseStatus.class).value())
                .isEqualTo(HttpStatus.NO_CONTENT);
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
