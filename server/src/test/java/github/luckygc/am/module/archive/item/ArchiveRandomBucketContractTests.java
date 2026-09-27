package github.luckygc.am.module.archive.item;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class ArchiveRandomBucketContractTests {

    @Test
    void shouldKeepRandomBucketOnArchiveMainTables() throws Exception {
        String migration =
                new String(
                        new ClassPathResource(
                                        "db/migration/V20260622_0100__create_archive_tables.sql")
                                .getInputStream()
                                .readAllBytes());

        assertThat(migration).contains("random_bucket");
        assertThat(migration).contains("smallint     not null");
        assertThat(migration).contains("check (random_bucket >= 0 and random_bucket < 10000)");
        assertThat(migration).contains("default floor(random() * 10000)::smallint");
        assertThat(migration).contains("idx_am_archive_item_random_bucket_active");
        assertThat(migration).contains("idx_am_archive_volume_random_bucket_active");
        assertThat(migration)
                .contains("comment on column am_archive_item.random_bucket is '随机抽查辅助分桶'");
        assertThat(migration)
                .contains("comment on column am_archive_volume.random_bucket is '随机抽查辅助分桶'");
    }

    @Test
    void shouldMapRandomBucketOnArchiveEntities() throws Exception {
        assertRandomBucketColumn(ArchiveItem.class);
        assertRandomBucketColumn(ArchiveVolume.class);
    }

    private static void assertRandomBucketColumn(Class<?> entityType) throws Exception {
        Column column = entityType.getDeclaredField("randomBucket").getAnnotation(Column.class);

        assertThat(column).isNotNull();
        assertThat(column.name()).isEqualTo("random_bucket");
        assertThat(column.nullable()).isFalse();
        assertThat(column.insertable()).isFalse();
        assertThat(column.updatable()).isFalse();
    }
}
