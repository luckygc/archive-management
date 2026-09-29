package github.luckygc.am.module.archive.item.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.data.page.PageRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.module.archive.item.service.ArchiveVolumeService;
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
