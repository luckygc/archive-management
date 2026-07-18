package github.luckygc.am.module.archive.item.service;

import org.springframework.stereotype.Service;

import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@Service
public class ArchiveWorkspaceService {

    private final ArchiveCategoryService categoryService;
    private final ArchiveItemQueryService queryService;
    private final AuthorizationPermissionService permissionService;

    public ArchiveWorkspaceService(
            ArchiveCategoryService categoryService,
            ArchiveItemQueryService queryService,
            AuthorizationPermissionService permissionService) {
        this.categoryService = categoryService;
        this.queryService = queryService;
        this.permissionService = permissionService;
    }

    public ArchiveWorkspaceSummary getSummary(Long userId) {
        userId = AuthenticatedUsers.requireResolvedUserId(userId);
        if (!permissionService.hasPermission(
                userId, AuthorizationPermissionCode.ARCHIVE_ITEM_READ.code())) {
            return ArchiveWorkspaceSummary.empty();
        }
        ArchiveWorkspaceSummary total = ArchiveWorkspaceSummary.empty();
        for (var category : categoryService.listCategories(true)) {
            total = total.plus(queryService.summarizeCategoryForWorkspace(category.id(), userId));
        }
        return total;
    }

    public record ArchiveWorkspaceSummary(
            long archiveItemCount, long draftCount, long lockedCount, long electronicFileCount) {

        public static ArchiveWorkspaceSummary empty() {
            return new ArchiveWorkspaceSummary(0, 0, 0, 0);
        }

        private ArchiveWorkspaceSummary plus(ArchiveWorkspaceCategorySummary summary) {
            try {
                return new ArchiveWorkspaceSummary(
                        Math.addExact(archiveItemCount, summary.archiveItemCount()),
                        Math.addExact(draftCount, summary.draftCount()),
                        Math.addExact(lockedCount, summary.lockedCount()),
                        Math.addExact(electronicFileCount, summary.electronicFileCount()));
            } catch (ArithmeticException exception) {
                throw new IllegalStateException("工作台摘要计数溢出", exception);
            }
        }
    }
}
