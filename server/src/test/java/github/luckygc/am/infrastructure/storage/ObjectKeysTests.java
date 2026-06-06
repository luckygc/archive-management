package github.luckygc.am.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class ObjectKeysTests {

    @Test
    void generateObjectKeyWithDatePartitionAndUuidV7() {
        String objectKey = ObjectKeys.generate(LocalDate.of(2026, 6, 6), "原始文件.PDF");

        assertThat(objectKey)
                .matches("2026/06/06/[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\.pdf");
    }

    @Test
    void ignoreUnsafeExtension() {
        String objectKey = ObjectKeys.generate(LocalDate.of(2026, 6, 6), "demo.bad extension");

        assertThat(objectKey)
                .matches("2026/06/06/[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    }

    @Test
    void normalizeRejectTraversal() {
        assertThatThrownBy(() -> ObjectKeys.normalize("../demo.txt"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizeWindowsSeparatorsToObjectKeySeparators() {
        assertThat(ObjectKeys.normalize("fonds\\2026\\demo.txt"))
                .isEqualTo("fonds/2026/demo.txt");
    }
}
