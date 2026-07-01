package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.data.page.CursoredPage;
import jakarta.data.page.PageRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.item.ArchiveItemAudit;
import github.luckygc.am.module.archive.item.repository.ArchiveItemAuditDataRepository;
import github.luckygc.am.module.archive.item.service.ArchiveItemAuditSearchService.ArchiveItemAuditResponse;
import github.luckygc.am.module.archive.item.service.ArchiveItemAuditSearchService.ListArchiveItemAuditsRequest;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@DisplayName("档案条目操作审计查询")
class ArchiveItemAuditSearchServiceTests {

    private final ArchiveItemAuditDataRepository auditRepository =
            mock(ArchiveItemAuditDataRepository.class);
    private final AuthorizationPermissionService permissionService =
            mock(AuthorizationPermissionService.class);
    private final ArchiveItemAuditSearchService auditSearchService =
            new ArchiveItemAuditSearchService(auditRepository, permissionService);

    @Test
    @DisplayName("按档案、分类、全宗、操作类型和操作时间分页查询审计")
    void listAuditsShouldApplyFiltersAndMapPage() {
        LocalDateTime operatedAt = LocalDateTime.of(2026, 6, 30, 10, 0);
        CursoredPage<ArchiveItemAudit> repositoryPage =
                page(List.of(audit(99L, operatedAt)), 1L, false, true);
        when(permissionService.isSuperAdmin(9L)).thenReturn(true);
        when(auditRepository.find(any(), any())).thenReturn(repositoryPage);

        CursorPageResponse<ArchiveItemAuditResponse> page =
                auditSearchService.listAudits(
                        new ListArchiveItemAuditsRequest(
                                10L,
                                " F001 ",
                                " contract ",
                                " UPDATE ",
                                LocalDateTime.of(2026, 6, 1, 0, 0),
                                LocalDateTime.of(2026, 7, 1, 0, 0),
                                20,
                                null,
                                true),
                        9L);

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.next()).isNull();
        assertThat(page.items())
                .containsExactly(
                        new ArchiveItemAuditResponse(
                                99L,
                                "am_archive_item",
                                10L,
                                10L,
                                "F001",
                                "contract",
                                "UPDATE",
                                "修正题名",
                                9L,
                                operatedAt));
        verify(auditRepository).find(any(), any());
    }

    @Test
    @DisplayName("后续游标翻页不请求 total")
    void listAuditsShouldNotRequestTotalWhenCursorProvided() {
        LocalDateTime operatedAt = LocalDateTime.of(2026, 6, 30, 10, 0);
        CursoredPage<ArchiveItemAudit> firstRepositoryPage =
                page(List.of(audit(99L, operatedAt)), null, true, false);
        when(permissionService.isSuperAdmin(9L)).thenReturn(true);
        when(auditRepository.find(any(), any())).thenReturn(firstRepositoryPage);
        CursorPageResponse<ArchiveItemAuditResponse> firstPage =
                auditSearchService.listAudits(
                        new ListArchiveItemAuditsRequest(
                                null, null, null, null, null, null, 1, null, true),
                        9L);

        CursoredPage<ArchiveItemAudit> nextRepositoryPage =
                page(List.of(audit(98L, operatedAt.minusMinutes(1))), null, false, false);
        when(auditRepository.find(any(), any())).thenReturn(nextRepositoryPage);

        CursorPageResponse<ArchiveItemAuditResponse> nextPage =
                auditSearchService.listAudits(
                        new ListArchiveItemAuditsRequest(
                                null, null, null, null, null, null, 1, firstPage.next(), true),
                        9L);

        assertThat(nextPage.total()).isNull();
    }

    @Test
    @DisplayName("非超级管理员不能查询审计")
    void listAuditsShouldRejectNonSuperAdmin() {
        when(permissionService.isSuperAdmin(9L)).thenReturn(false);

        assertThatThrownBy(
                        () ->
                                auditSearchService.listAudits(
                                        new ListArchiveItemAuditsRequest(
                                                null, null, null, null, null, null, 20, null,
                                                false),
                                        9L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(auditRepository, never()).find(any(), any());
    }

    @Test
    @DisplayName("未登录不能查询审计")
    void listAuditsShouldRejectAnonymousUser() {
        assertThatThrownBy(
                        () ->
                                auditSearchService.listAudits(
                                        new ListArchiveItemAuditsRequest(
                                                null, null, null, null, null, null, 20, null,
                                                false),
                                        null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(permissionService, never()).isSuperAdmin(any());
        verify(auditRepository, never()).find(any(), any());
    }

    @Test
    @DisplayName("拒绝超过上限的 page size")
    void listAuditsShouldRejectLimitOverMaximum() {
        when(permissionService.isSuperAdmin(9L)).thenReturn(true);

        assertThatThrownBy(
                        () ->
                                auditSearchService.listAudits(
                                        new ListArchiveItemAuditsRequest(
                                                null, null, null, null, null, null, 2000, null,
                                                false),
                                        9L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("分页参数不合法");
    }

    private static CursoredPage<ArchiveItemAudit> page(
            List<ArchiveItemAudit> content, Long total, boolean hasNext, boolean hasTotals) {
        CursoredPage<ArchiveItemAudit> page = mock(CursoredPage.class);
        when(page.content()).thenReturn(content);
        when(page.numberOfElements()).thenReturn(content.size());
        when(page.hasPrevious()).thenReturn(false);
        when(page.hasNext()).thenReturn(hasNext);
        when(page.hasTotals()).thenReturn(hasTotals);
        if (hasTotals) {
            when(page.totalElements()).thenReturn(total);
        }
        if (!content.isEmpty()) {
            ArchiveItemAudit item = content.getFirst();
            PageRequest.Cursor cursor =
                    PageRequest.Cursor.forKey(item.getOperatedAt(), item.getId());
            when(page.cursor(0)).thenReturn(cursor);
            when(page.cursor(content.size() - 1)).thenReturn(cursor);
            when(page.nextPageRequest()).thenReturn(PageRequest.ofSize(1).afterCursor(cursor));
            when(page.previousPageRequest()).thenReturn(PageRequest.ofSize(1).beforeCursor(cursor));
        }
        return page;
    }

    private static ArchiveItemAudit audit(Long id, LocalDateTime operatedAt) {
        return audit(id, 10L, operatedAt);
    }

    private static ArchiveItemAudit audit(Long id, Long archiveItemId, LocalDateTime operatedAt) {
        ArchiveItemAudit audit = new ArchiveItemAudit();
        audit.setId(id);
        audit.setSourceTableName("am_archive_item");
        audit.setSourceRecordId(archiveItemId);
        audit.setArchiveItemId(archiveItemId);
        audit.setFondsCode("F001");
        audit.setCategoryCode("contract");
        audit.setOperationType("UPDATE");
        audit.setOperationReason("修正题名");
        audit.setOperatedBy(9L);
        audit.setOperatedAt(operatedAt);
        return audit;
    }
}
