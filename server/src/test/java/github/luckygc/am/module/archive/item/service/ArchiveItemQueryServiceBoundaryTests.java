package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("档案查询职责边界")
class ArchiveItemQueryServiceBoundaryTests {

    @Test
    @DisplayName("查询入口不自行编译动态筛选条件")
    void queryEntryShouldDelegateCriteriaCompilation() {
        assertThat(
                        Arrays.stream(ArchiveItemQueryService.class.getDeclaredMethods())
                                .map(method -> method.getName()))
                .doesNotContain("buildSearchConditions", "buildRelatedGroups", "toSqlCondition");
    }

    @Test
    @DisplayName("查询入口不自行装配动态游标分页")
    void queryEntryShouldDelegateCursorPageAssembly() {
        assertThat(
                        Arrays.stream(ArchiveItemQueryService.class.getDeclaredMethods())
                                .map(method -> method.getName()))
                .doesNotContain(
                        "queryDynamicItemPage",
                        "cursorPredicates",
                        "cursorRowValues",
                        "normalizeDynamicFieldValues");
    }
}
