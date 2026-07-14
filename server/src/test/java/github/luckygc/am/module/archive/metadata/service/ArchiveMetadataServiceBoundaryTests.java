package github.luckygc.am.module.archive.metadata.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("档案元数据职责边界")
class ArchiveMetadataServiceBoundaryTests {

    @Test
    @DisplayName("字段元数据入口不承担门类与全宗范围用例")
    void fieldMetadataEntryShouldNotOwnCategoryUseCases() {
        assertThat(
                        Arrays.stream(ArchiveMetadataService.class.getDeclaredMethods())
                                .map(method -> method.getName()))
                .doesNotContain(
                        "listCategories",
                        "listCategoriesForFonds",
                        "createCategory",
                        "updateCategory",
                        "deleteCategory",
                        "getCategory",
                        "listFondsCategoryScopes",
                        "saveFondsCategoryScopes");
    }
}
