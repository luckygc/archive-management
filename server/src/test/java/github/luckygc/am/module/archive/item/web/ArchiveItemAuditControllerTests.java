package github.luckygc.am.module.archive.item.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.module.archive.item.service.ArchiveItemAuditQueryService;

@DisplayName("档案条目操作审计 HTTP 入口")
class ArchiveItemAuditControllerTests {

    private final ArchiveItemAuditQueryService auditQueryService =
            mock(ArchiveItemAuditQueryService.class);
    private final ArchiveItemAuditController controller =
            new ArchiveItemAuditController(auditQueryService);

    @Test
    @DisplayName("未认证用户不能查询审计")
    void listAuditsShouldRequireAuthentication() {
        assertThatThrownBy(
                        () ->
                                controller.listAudits(
                                        null, null, null, null, null, null, null, null, null))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception -> {
                            assertThat(exception.getStatusCode())
                                    .isEqualTo(HttpStatus.UNAUTHORIZED);
                            assertThat(exception.getReason()).isEqualTo("请先登录");
                        });

        verifyNoInteractions(auditQueryService);
    }
}
