package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.module.archive.metadata.ArchiveManagementMode;
import github.luckygc.am.module.archive.metadata.ArchiveTableStatus;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryDto;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@DisplayName("档案工作台摘要服务")
class ArchiveWorkspaceServiceTests {

    private final ArchiveCategoryService categoryService = mock(ArchiveCategoryService.class);
    private final ArchiveItemQueryService queryService = mock(ArchiveItemQueryService.class);
    private final AuthorizationPermissionService permissionService =
            mock(AuthorizationPermissionService.class);
    private final ArchiveWorkspaceService service =
            new ArchiveWorkspaceService(categoryService, queryService, permissionService);

    @Test
    @DisplayName("按启用分类累加当前用户可见摘要")
    void summaryUsesResolvedItemDataScopeAcrossEnabledCategories() {
        when(permissionService.hasPermission(8L, "archive:item:read")).thenReturn(true);
        when(categoryService.listCategories(true)).thenReturn(List.of(category(1L), category(2L)));
        when(queryService.summarizeCategoryForWorkspace(1L, 8L))
                .thenReturn(new ArchiveWorkspaceCategorySummary(7, 2, 1, 4));
        when(queryService.summarizeCategoryForWorkspace(2L, 8L))
                .thenReturn(new ArchiveWorkspaceCategorySummary(5, 1, 1, 3));

        assertThat(service.getSummary(8L))
                .isEqualTo(new ArchiveWorkspaceService.ArchiveWorkspaceSummary(12, 3, 2, 7));
    }

    @Test
    @DisplayName("没有档案读取权限的已认证用户获得全零且不查询分类")
    void userWithoutReadPermissionGetsZeroWithoutReadingCategories() {
        when(permissionService.hasPermission(8L, "archive:item:read")).thenReturn(false);

        assertThat(service.getSummary(8L))
                .isEqualTo(ArchiveWorkspaceService.ArchiveWorkspaceSummary.empty());

        verify(categoryService, never()).listCategories(true);
        verify(queryService, never()).summarizeCategoryForWorkspace(1L, 8L);
    }

    @Test
    @DisplayName("分类汇总相加溢出时显式失败")
    void summaryRejectsLongOverflow() {
        when(permissionService.hasPermission(8L, "archive:item:read")).thenReturn(true);
        when(categoryService.listCategories(true)).thenReturn(List.of(category(1L), category(2L)));
        when(queryService.summarizeCategoryForWorkspace(1L, 8L))
                .thenReturn(new ArchiveWorkspaceCategorySummary(Long.MAX_VALUE, 0, 0, 0));
        when(queryService.summarizeCategoryForWorkspace(2L, 8L))
                .thenReturn(new ArchiveWorkspaceCategorySummary(1, 0, 0, 0));

        assertThatThrownBy(() -> service.getSummary(8L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("溢出");
    }

    private ArchiveCategoryDto category(long id) {
        return new ArchiveCategoryDto(
                id,
                1L,
                null,
                "category_" + id,
                "分类" + id,
                ArchiveManagementMode.ITEM_ONLY,
                null,
                "am_archive_item_data_category_" + id,
                null,
                null,
                ArchiveTableStatus.BUILT,
                LocalDateTime.of(2026, 7, 15, 9, 0),
                true,
                (int) id,
                LocalDateTime.of(2026, 7, 15, 9, 0),
                LocalDateTime.of(2026, 7, 15, 9, 0));
    }
}
