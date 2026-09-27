package github.luckygc.am.module.archive.authorization.service;

import java.util.List;

import github.luckygc.am.module.archive.authorization.ArchiveDataScope;
import github.luckygc.am.module.archive.authorization.ArchiveDataScopeDimension;
import github.luckygc.am.module.archive.mapper.ArchiveDataScopeSqlGroup;

public abstract class ArchiveDataScopeResolutionTypes {

    private ArchiveDataScopeResolutionTypes() {}

    public record ResolvedArchiveDataScope(
            boolean allData, boolean empty, List<ResolvedScope> scopes) {

        public static ResolvedArchiveDataScope all() {
            return new ResolvedArchiveDataScope(true, false, List.of());
        }

        public static ResolvedArchiveDataScope none() {
            return new ResolvedArchiveDataScope(false, true, List.of());
        }

        public static ResolvedArchiveDataScope conditional(List<ResolvedScope> scopes) {
            return new ResolvedArchiveDataScope(false, false, List.copyOf(scopes));
        }
    }

    public record ResolvedScope(
            ArchiveDataScope scope, List<ArchiveDataScopeDimension> dimensions) {}

    public record ArchiveDataScopeFilter(
            boolean allData, boolean empty, List<ArchiveDataScopeSqlGroup> groups) {

        public static ArchiveDataScopeFilter all() {
            return new ArchiveDataScopeFilter(true, false, List.of());
        }

        public static ArchiveDataScopeFilter none() {
            return new ArchiveDataScopeFilter(false, true, List.of());
        }

        public static ArchiveDataScopeFilter fondsCodes(List<String> fondsCodes) {
            return groups(
                    List.of(
                            new ArchiveDataScopeSqlGroup(
                                    List.copyOf(fondsCodes), List.of(), List.of())));
        }

        public static ArchiveDataScopeFilter groups(List<ArchiveDataScopeSqlGroup> groups) {
            return new ArchiveDataScopeFilter(false, false, List.copyOf(groups));
        }
    }
}
