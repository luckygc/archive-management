package github.luckygc.am.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("对象存储 key 生成与归一化")
class ObjectKeysTests {

    @Test
    @DisplayName("对象 key 使用日期分区和 UUID v7")
    void generateObjectKeyWithDatePartitionAndUuidV7() {
        String objectKey = ObjectKeys.generate(LocalDate.of(2026, 6, 6), "原始文件.PDF");

        assertThat(objectKey)
                .matches(
                        "2026/06/06/[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\.pdf");
    }

    @Test
    @DisplayName("不安全扩展名会被忽略")
    void ignoreUnsafeExtension() {
        String objectKey = ObjectKeys.generate(LocalDate.of(2026, 6, 6), "demo.bad extension");

        assertThat(objectKey)
                .matches(
                        "2026/06/06/[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    }

    @Test
    @DisplayName("归一化时拒绝路径穿越")
    void normalizeRejectTraversal() {
        assertThatThrownBy(() -> ObjectKeys.normalize("../demo.txt"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Windows 分隔符归一化为对象 key 分隔符")
    void normalizeWindowsSeparatorsToObjectKeySeparators() {
        assertThat(ObjectKeys.normalize("fonds\\2026\\demo.txt")).isEqualTo("fonds/2026/demo.txt");
    }
}
