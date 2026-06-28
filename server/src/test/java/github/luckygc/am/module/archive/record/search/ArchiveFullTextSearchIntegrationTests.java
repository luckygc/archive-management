package github.luckygc.am.module.archive.record.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import github.luckygc.am.app.ArchiveManagementApplication;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.record.ArchiveRecordRoutingService;
import github.luckygc.am.module.archive.record.ArchiveRecordRoutingService.ArchiveRecordOrderBy;
import github.luckygc.am.module.archive.record.ArchiveRecordRoutingService.ArchiveRecordQueryRequest;

@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        classes = ArchiveManagementApplication.class,
        properties = {
            "spring.autoconfigure.exclude=org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatAutoConfiguration",
            "spring.flyway.locations=classpath:db/migration,classpath:db/sample",
            "archive.search.full-text.provider=postgresql",
            "spring.quartz.auto-startup=false",
            "spring.session.jdbc.cleanup-cron=-",
            "flowable.async-executor-activate=false",
            "flowable.check-process-definitions=false",
            "flowable.eventregistry.enabled=false"
        })
@DisplayName("档案记录全文检索集成")
class ArchiveFullTextSearchIntegrationTests {

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18")
                    .withDatabaseName("archive_management_full_text_test")
                    .withUsername("postgres")
                    .withPassword("postgres");

    @Autowired private ArchiveRecordRoutingService archiveRecordRoutingService;

    @Autowired private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void registerPostgreSqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Test
    @DisplayName("全文检索与结构化过滤在同一查询语义中生效")
    void discoverRecordsCombinesFullTextAndDatabaseFilteringInOneQuery() {
        Long categoryId =
                jdbcTemplate.queryForObject(
                        "select id from am_archive_category where category_code = 'HT'",
                        Long.class);

        var result =
                archiveRecordRoutingService.discoverRecords(
                        new ArchiveRecordQueryRequest(
                                categoryId,
                                ArchiveLevel.item,
                                "Z001",
                                "档案管理系统",
                                null,
                                List.of(),
                                50,
                                null,
                                null),
                        1L);

        assertThat(result.items())
                .extracting(row -> row.get("archive_no"))
                .containsExactly("HT-2026-001");
    }

    @Test
    @DisplayName("键集分页使用用户排序并追加兜底排序")
    void searchRecordsUsesCursorWithUserOrderAndFallbackOrder() {
        Long categoryId =
                jdbcTemplate.queryForObject(
                        "select id from am_archive_category where category_code = 'GW'",
                        Long.class);

        var firstPage =
                archiveRecordRoutingService.searchRecords(
                        new ArchiveRecordQueryRequest(
                                categoryId,
                                ArchiveLevel.item,
                                null,
                                null,
                                null,
                                List.of(),
                                1,
                                null,
                                List.of(new ArchiveRecordOrderBy("archiveNo", "ASC"))),
                        1L);

        assertThat(firstPage.items())
                .extracting(row -> row.get("archive_no"))
                .containsExactly("GW-2026-001");
        assertThat(firstPage.next()).isNotBlank();

        var secondPage =
                archiveRecordRoutingService.searchRecords(
                        new ArchiveRecordQueryRequest(
                                categoryId,
                                ArchiveLevel.item,
                                null,
                                null,
                                null,
                                List.of(),
                                1,
                                firstPage.next(),
                                List.of(new ArchiveRecordOrderBy("archiveNo", "ASC"))),
                        1L);

        assertThat(secondPage.items())
                .extracting(row -> row.get("archive_no"))
                .containsExactly("GW-2026-002");
        assertThat(secondPage.prev()).isNotBlank();

        var previousPage =
                archiveRecordRoutingService.searchRecords(
                        new ArchiveRecordQueryRequest(
                                categoryId,
                                ArchiveLevel.item,
                                null,
                                null,
                                null,
                                List.of(),
                                1,
                                secondPage.prev(),
                                List.of(new ArchiveRecordOrderBy("archiveNo", "ASC"))),
                        1L);

        assertThat(previousPage.items())
                .extracting(row -> row.get("archive_no"))
                .containsExactly("GW-2026-001");

        assertThatThrownBy(
                        () ->
                                archiveRecordRoutingService.searchRecords(
                                        new ArchiveRecordQueryRequest(
                                                categoryId,
                                                ArchiveLevel.item,
                                                "Z001",
                                                null,
                                                null,
                                                List.of(),
                                                1,
                                                firstPage.next(),
                                                List.of(
                                                        new ArchiveRecordOrderBy(
                                                                "archiveNo", "ASC"))),
                                        1L))
                .hasMessageContaining("分页条件已变化");
    }
}
