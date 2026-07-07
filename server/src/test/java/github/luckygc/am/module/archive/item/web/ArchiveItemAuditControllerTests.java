package github.luckygc.am.module.archive.item.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.common.api.CursorPageRequest;
import github.luckygc.am.common.api.CursorPageTokenContext;
import github.luckygc.am.common.security.UnauthenticatedException;
import github.luckygc.am.module.archive.item.service.ArchiveItemAuditSearchService;

@DisplayName("档案条目操作审计 HTTP 入口")
class ArchiveItemAuditControllerTests {

    private final ArchiveItemAuditSearchService auditSearchService =
            mock(ArchiveItemAuditSearchService.class);
    private final ArchiveItemAuditController controller =
            new ArchiveItemAuditController(auditSearchService);

    @Test
    @DisplayName("未认证用户不能查询审计")
    void listAuditsShouldRequireAuthentication() {
        assertThatThrownBy(
                        () ->
                                controller.listAudits(
                                        null, null, null, null, null, null, firstPage(), null))
                .isInstanceOfSatisfying(
                        UnauthenticatedException.class,
                        exception -> assertThat(exception).hasMessage("请先登录"));

        verifyNoInteractions(auditSearchService);
    }

    private CursorPageRequest firstPage() {
        return CursorPageRequest.of(100, null, false, new CursorPageTokenContext("fingerprint"));
    }
}
