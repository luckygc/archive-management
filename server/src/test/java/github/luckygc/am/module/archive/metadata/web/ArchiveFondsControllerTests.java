package github.luckygc.am.module.archive.metadata.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;

import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.module.archive.metadata.ArchiveFondsEventType;
import github.luckygc.am.module.archive.metadata.ArchiveFondsStatus;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveFondsService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataReferenceService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFondsDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFondsEventDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.AssignArchiveFondsNumberRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.CloseArchiveFondsRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ReopenArchiveFondsRequest;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@DisplayName("全宗生命周期 HTTP 入口")
class ArchiveFondsControllerTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 11, 9, 30);
    private final ArchiveFondsService fondsService = mock(ArchiveFondsService.class);
    private final AuthorizationPermissionService permissionService =
            mock(AuthorizationPermissionService.class);
    private final ArchiveMetadataController controller =
            new ArchiveMetadataController(
                    mock(ArchiveMetadataService.class),
                    mock(ArchiveMetadataReferenceService.class),
                    mock(ArchiveCategoryService.class),
                    fondsService,
                    permissionService);

    @Test
    @DisplayName("编号、封闭和重新开放动作均转发认证用户")
    void lifecycleActionsForwardAuthenticatedUser() {
        AssignArchiveFondsNumberRequest numberRequest =
                new AssignArchiveFondsNumberRequest("HD-001", null, "完成登记", null);
        CloseArchiveFondsRequest closeRequest = new CloseArchiveFondsRequest("机构撤并", null);
        ReopenArchiveFondsRequest reopenRequest = new ReopenArchiveFondsRequest("恢复独立立档", null);
        when(fondsService.assignNumber(1L, numberRequest, 9L)).thenReturn(fonds());
        when(fondsService.closeFonds(1L, closeRequest, 9L)).thenReturn(fonds());
        when(fondsService.reopenFonds(1L, reopenRequest, 9L)).thenReturn(fonds());

        controller.assignFondsNumber(1L, numberRequest, auth(9L));
        controller.closeFonds(1L, closeRequest, auth(9L));
        controller.reopenFonds(1L, reopenRequest, auth(9L));

        verify(permissionService, org.mockito.Mockito.times(3))
                .requirePermission(9L, AuthorizationPermissionCode.ARCHIVE_METADATA_MANAGE);
        verify(fondsService).assignNumber(1L, numberRequest, 9L);
        verify(fondsService).closeFonds(1L, closeRequest, 9L);
        verify(fondsService).reopenFonds(1L, reopenRequest, 9L);
    }

    @Test
    @DisplayName("事件列表是独立只读子资源")
    void eventsAreExposedAsReadOnlySubresource() {
        ArchiveFondsEventDto event =
                new ArchiveFondsEventDto(
                        11L,
                        "SYS-HD",
                        ArchiveFondsEventType.CLOSED,
                        "ACTIVE",
                        "CLOSED",
                        "机构撤并",
                        NOW,
                        9L,
                        NOW);
        when(fondsService.listEvents(1L)).thenReturn(List.of(event));

        var response = controller.listFondsEvents(1L);

        assertThat(response.items()).containsExactly(event);
        verify(fondsService).listEvents(1L);
    }

    private ArchiveFondsDto fonds() {
        return new ArchiveFondsDto(
                1L,
                "SYS-HD",
                "HD-001",
                "华东公司",
                ArchiveFondsStatus.ACTIVE,
                null,
                NOW,
                null,
                null,
                null,
                null,
                null,
                10,
                NOW,
                NOW);
    }

    private TestingAuthenticationToken auth(Long userId) {
        return new TestingAuthenticationToken(
                new AuthenticatedUser() {
                    @Override
                    public Long id() {
                        return userId;
                    }

                    @Override
                    public String displayName() {
                        return "档案管理员";
                    }
                },
                null);
    }
}
