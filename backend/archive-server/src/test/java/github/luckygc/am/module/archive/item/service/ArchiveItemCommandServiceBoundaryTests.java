package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("档案写入职责边界")
class ArchiveItemCommandServiceBoundaryTests {

    @Test
    @DisplayName("写入入口不承担读取模型和数据范围读取校验")
    void commandEntryShouldDelegateReadModel() {
        assertThat(
                        Arrays.stream(ArchiveItemCommandService.class.getDeclaredMethods())
                                .map(method -> method.getName()))
                .doesNotContain(
                        "getItem",
                        "getItemDetail",
                        "loadItem",
                        "loadItemDetail",
                        "assertItemInDataScope",
                        "rebuildSearchProjection",
                        "convertDynamicFields",
                        "convertValue");
    }
}
