package github.luckygc.am;

import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.time.LocalDateTime;
import javax.sql.DataSource;

import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.SessionRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Testcontainers;

import github.luckygc.am.app.ArchiveManagementApplication;
import github.luckygc.am.module.archive.metadata.ArchiveFonds;
import github.luckygc.am.module.archive.metadata.ArchiveFondsStatus;
import github.luckygc.am.module.archive.metadata.ArchiveManagementMode;
import github.luckygc.am.module.archive.metadata.repository.ArchiveCategoryDataRepository;
import github.luckygc.am.module.archive.metadata.repository.ArchiveFondsDataRepository;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes;
import github.luckygc.am.module.authentication.ArchiveUserDetails;
import github.luckygc.am.test.PostgreSqlContainerTest;

@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        classes = ArchiveManagementApplication.class,
        properties = {
            "spring.quartz.auto-startup=false",
            "spring.session.jdbc.cleanup-cron=-",
            "flowable.async-executor-activate=false",
            "flowable.check-process-definitions=false",
            "flowable.eventregistry.enabled=false"
        })
@DisplayName("服务端应用启动与基础合同")
class ServerApplicationTests extends PostgreSqlContainerTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ArchiveFondsDataRepository archiveFondsDataRepository;
    @Autowired private ArchiveCategoryDataRepository archiveCategoryDataRepository;
    @Autowired private ArchiveCategoryService archiveCategoryService;
    @Autowired private SessionRepository<?> sessionRepository;
    @Autowired private CacheManager cacheManager;

    @Test
    @DisplayName("应用上下文启动后使用 JDBC 会话和 Quartz JDBC 调度")
    void contextLoads() {
        Assertions.assertTrue(POSTGRES.isRunning());
        Assertions.assertInstanceOf(JdbcIndexedSessionRepository.class, sessionRepository);
        Assertions.assertInstanceOf(CaffeineCacheManager.class, cacheManager);
    }

    @Test
    @DisplayName("Flyway 迁移后的 PostgreSQL 资源可用")
    void migratedPostgreSqlResourcesAreAvailable() {
        Assertions.assertEquals(
                "archive_management_test",
                jdbcTemplate.queryForObject("select current_database()", String.class));
        Assertions.assertEquals(
                TEST_SCHEMA, jdbcTemplate.queryForObject("select current_schema()", String.class));
        Assertions.assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "select count(*) from pg_extension where extname = 'pg_trgm'",
                        Integer.class));
        Assertions.assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "select count(*) from flyway_schema_history "
                                + "where version = '20260622.0100' and success = true",
                        Integer.class));
        Assertions.assertEquals(
                "am_archive_item_search",
                jdbcTemplate.queryForObject(
                        "select to_regclass('am_archive_item_search')::text", String.class));
        Assertions.assertEquals(
                "idx_am_archive_item_search_trgm",
                jdbcTemplate.queryForObject(
                        "select to_regclass('idx_am_archive_item_search_trgm')::text",
                        String.class));
        Assertions.assertNull(
                jdbcTemplate.queryForObject(
                        "select to_regclass('am_archive_record')::text", String.class));
        Assertions.assertEquals(
                "am_archive_item",
                jdbcTemplate.queryForObject(
                        "select to_regclass('am_archive_item')::text", String.class));
        Assertions.assertEquals(
                "am_archive_volume",
                jdbcTemplate.queryForObject(
                        "select to_regclass('am_archive_volume')::text", String.class));
        Assertions.assertEquals(
                "uk_am_archive_volume_category_archive_no_active",
                jdbcTemplate.queryForObject(
                        "select to_regclass('uk_am_archive_volume_category_archive_no_active')::text",
                        String.class));
        Assertions.assertTrue(
                uniqueIndexUsesActiveRowsOnly("uk_am_archive_volume_category_archive_no_active"));
        Assertions.assertEquals(
                "am_archive_item_relation",
                jdbcTemplate.queryForObject(
                        "select to_regclass('am_archive_item_relation')::text", String.class));
        Assertions.assertEquals(
                "uk_am_archive_item_category_archive_no_active",
                jdbcTemplate.queryForObject(
                        "select to_regclass('uk_am_archive_item_category_archive_no_active')::text",
                        String.class));
        Assertions.assertTrue(
                uniqueIndexUsesActiveRowsOnly("uk_am_archive_item_category_archive_no_active"));
        Assertions.assertEquals(
                "uk_am_archive_category_code",
                jdbcTemplate.queryForObject(
                        "select to_regclass('uk_am_archive_category_code')::text", String.class));
        Assertions.assertNull(
                jdbcTemplate.queryForObject(
                        "select to_regclass('uk_am_archive_category_scheme_code_active')::text",
                        String.class));
        Assertions.assertNull(
                jdbcTemplate.queryForObject(
                        "select to_regclass('uk_am_archive_category_code_active')::text",
                        String.class));
        Assertions.assertFalse(uniqueIndexUsesActiveRowsOnly("uk_am_archive_category_code"));
        Assertions.assertEquals(
                "am_archive_item_line_table",
                jdbcTemplate.queryForObject(
                        "select to_regclass('am_archive_item_line_table')::text", String.class));
        Assertions.assertEquals(
                "am_archive_item_line_field",
                jdbcTemplate.queryForObject(
                        "select to_regclass('am_archive_item_line_field')::text", String.class));
        Assertions.assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "select count(*) from information_schema.columns "
                                + "where table_schema = current_schema() "
                                + "and table_name = 'am_archive_field' "
                                + "and column_name = 'exact_searchable'",
                        Integer.class));
        Assertions.assertEquals(true, relationExistsInCurrentSchema("event_publication"));
        Assertions.assertEquals(true, relationExistsInCurrentSchema("spring_session"));
        Assertions.assertEquals(true, relationExistsInCurrentSchema("spring_session_attributes"));
        Assertions.assertEquals(true, relationExistsInCurrentSchema("qrtz_locks"));
        Assertions.assertEquals(true, relationExistsInCurrentSchema("qrtz_job_details"));
        Assertions.assertEquals(true, relationExistsInCurrentSchema("qrtz_triggers"));
        Assertions.assertEquals(true, relationExistsInCurrentSchema("qrtz_scheduler_state"));
        Assertions.assertEquals(
                "event_publication_by_completion_date_idx",
                jdbcTemplate.queryForObject(
                        "select to_regclass('event_publication_by_completion_date_idx')::text",
                        String.class));
        Assertions.assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "select count(*) from information_schema.columns "
                                + "where table_schema = current_schema() "
                                + "and table_name = 'event_publication' "
                                + "and column_name = 'listener_id'",
                        Integer.class));
        Assertions.assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        "select count(*) from information_schema.columns "
                                + "where table_schema = current_schema() "
                                + "and table_name = 'am_archive_field' "
                                + "and column_name = 'full_text_searchable'",
                        Integer.class));
    }

    private boolean relationExistsInCurrentSchema(String relationName) {
        return Boolean.TRUE.equals(
                jdbcTemplate.queryForObject(
                        "select exists ("
                                + "select 1 from pg_class c "
                                + "join pg_namespace n on n.oid = c.relnamespace "
                                + "where n.nspname = current_schema() "
                                + "and c.relname = ?"
                                + ")",
                        Boolean.class,
                        relationName));
    }

    private boolean uniqueIndexUsesActiveRowsOnly(String indexName) {
        String indexDefinition =
                jdbcTemplate.queryForObject(
                        "select indexdef from pg_indexes "
                                + "where schemaname = current_schema() "
                                + "and indexname = ?",
                        String.class,
                        indexName);
        return indexDefinition != null
                && indexDefinition.contains("UNIQUE")
                && indexDefinition.contains("WHERE (deleted_flag = false)");
    }

    @Test
    @DisplayName("分类编码跨逻辑删除历史永久全局唯一")
    void categoryCodeRemainsGloballyUniqueAfterSoftDelete() {
        String categoryCode = "GLOBAL_UNIQUE_TEST";
        deleteCategoryUniquenessFixtures(categoryCode);
        try {
            jdbcTemplate.update(
                    "insert into am_archive_category "
                            + "(category_code, category_name, management_mode) "
                            + "values (?, '全局唯一测试分类', 'ITEM_ONLY')",
                    categoryCode);

            Assertions.assertThrows(
                    org.springframework.dao.DataIntegrityViolationException.class,
                    () ->
                            jdbcTemplate.update(
                                    "insert into am_archive_category "
                                            + "(category_code, category_name, management_mode) "
                                            + "values (?, '重复分类', 'ITEM_ONLY')",
                                    categoryCode));

            jdbcTemplate.update(
                    "update am_archive_category set deleted_flag = true "
                            + "where category_code = ?",
                    categoryCode);
            Assertions.assertNull(archiveCategoryDataRepository.findByCategoryCode(categoryCode));

            ResponseStatusException exception =
                    Assertions.assertThrows(
                            ResponseStatusException.class,
                            () ->
                                    archiveCategoryService.createCategory(
                                            new ArchiveMetadataTypes.ArchiveCategoryRequest(
                                                    categoryCode,
                                                    "历史编码不可复用",
                                                    null,
                                                    ArchiveManagementMode.ITEM_ONLY,
                                                    true,
                                                    0),
                                            9L));
            Assertions.assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        } finally {
            deleteCategoryUniquenessFixtures(categoryCode);
        }
    }

    private void deleteCategoryUniquenessFixtures(String categoryCode) {
        jdbcTemplate.update(
                "delete from am_archive_category where category_code = ?", categoryCode);
    }

    @Test
    @DisplayName("高增长业务 ID 使用 PostgreSQL sequence 预留内置数据区间")
    void highGrowthBusinessIdsUseReservedPostgreSqlSequences() {
        assertSequence("am_archive_item_id_seq");
        assertSequence("am_archive_volume_id_seq");
    }

    private void assertSequence(String sequenceName) {
        Assertions.assertEquals(
                1_000_000L,
                jdbcTemplate.queryForObject(
                        "select start_value from pg_sequences "
                                + "where schemaname = current_schema() "
                                + "and sequencename = ?",
                        Long.class,
                        sequenceName));
        Assertions.assertEquals(
                1_000L,
                jdbcTemplate.queryForObject(
                        "select increment_by from pg_sequences "
                                + "where schemaname = current_schema() "
                                + "and sequencename = ?",
                        Long.class,
                        sequenceName));
    }

    @Test
    @DisplayName("无状态 Repository 从安全上下文填充审计字段")
    void statelessRepositoryFillsAuditFieldsFromSecurityContext() {
        LocalDateTime forgedCreatedAt = LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime forgedInsertedUpdatedAt = LocalDateTime.of(2001, 1, 1, 0, 0);
        LocalDateTime forgedUpdatedAt = LocalDateTime.of(2002, 1, 1, 0, 0);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                new ArchiveUserDetails(
                                        99L,
                                        "audit-user",
                                        "N/A",
                                        true,
                                        "审计用户",
                                        java.util.List.of()),
                                "N/A",
                                java.util.List.of()));
        try {
            ArchiveFonds fonds = new ArchiveFonds();
            fonds.setFondsCode("AUDIT_TEST");
            fonds.setFondsName("审计测试全宗");
            fonds.setStatus(ArchiveFondsStatus.ACTIVE);
            fonds.setSortOrder(0);
            fonds.setCreatedAt(forgedCreatedAt);
            fonds.setCreatedBy(-1L);
            fonds.setUpdatedAt(forgedInsertedUpdatedAt);
            fonds.setUpdatedBy(-2L);

            archiveFondsDataRepository.insert(fonds);

            LocalDateTime createdAt =
                    jdbcTemplate.queryForObject(
                            "select created_at from am_archive_fonds where fonds_code = 'AUDIT_TEST'",
                            LocalDateTime.class);
            LocalDateTime updatedAt =
                    jdbcTemplate.queryForObject(
                            "select updated_at from am_archive_fonds where fonds_code = 'AUDIT_TEST'",
                            LocalDateTime.class);
            Assertions.assertNotEquals(forgedCreatedAt, createdAt);
            Assertions.assertNotEquals(forgedInsertedUpdatedAt, updatedAt);
            assertSameTimeWithinPostgreSqlPrecision(createdAt, fonds.getCreatedAt());
            assertSameTimeWithinPostgreSqlPrecision(updatedAt, fonds.getUpdatedAt());
            Assertions.assertEquals(
                    99L,
                    jdbcTemplate.queryForObject(
                            "select created_by from am_archive_fonds where fonds_code = 'AUDIT_TEST'",
                            Long.class));
            Assertions.assertEquals(
                    99L,
                    jdbcTemplate.queryForObject(
                            "select updated_by from am_archive_fonds where fonds_code = 'AUDIT_TEST'",
                            Long.class));

            SecurityContextHolder.getContext()
                    .setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                    new ArchiveUserDetails(
                                            100L,
                                            "audit-updater",
                                            "N/A",
                                            true,
                                            "审计更新人",
                                            java.util.List.of()),
                                    "N/A",
                                    java.util.List.of()));
            ArchiveFonds saved = archiveFondsDataRepository.find("AUDIT_TEST").orElseThrow();
            saved.setFondsName("审计测试全宗-更新");
            saved.setUpdatedAt(forgedUpdatedAt);
            saved.setUpdatedBy(-3L);
            archiveFondsDataRepository.update(saved);

            Assertions.assertEquals(
                    createdAt,
                    jdbcTemplate.queryForObject(
                            "select created_at from am_archive_fonds where fonds_code = 'AUDIT_TEST'",
                            LocalDateTime.class));
            LocalDateTime updatedAtAfterUpdate =
                    jdbcTemplate.queryForObject(
                            "select updated_at from am_archive_fonds where fonds_code = 'AUDIT_TEST'",
                            LocalDateTime.class);
            Assertions.assertNotEquals(forgedUpdatedAt, updatedAtAfterUpdate);
            assertSameTimeWithinPostgreSqlPrecision(updatedAtAfterUpdate, saved.getUpdatedAt());
            Assertions.assertEquals(
                    99L,
                    jdbcTemplate.queryForObject(
                            "select created_by from am_archive_fonds where fonds_code = 'AUDIT_TEST'",
                            Long.class));
            Assertions.assertEquals(
                    100L,
                    jdbcTemplate.queryForObject(
                            "select updated_by from am_archive_fonds where fonds_code = 'AUDIT_TEST'",
                            Long.class));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static void assertSameTimeWithinPostgreSqlPrecision(
            LocalDateTime persistedTime, LocalDateTime entityTime) {
        Assertions.assertTrue(
                Duration.between(persistedTime, entityTime).abs().compareTo(Duration.ofNanos(1_000))
                        < 0);
    }

    @TestConfiguration
    static class TestPersistenceConfiguration {

        @Bean
        SqlSessionFactory sqlSessionFactory() {
            SqlSessionFactory sqlSessionFactory = mock(SqlSessionFactory.class);
            Configuration configuration = new Configuration();
            configuration.setEnvironment(
                    new Environment("test", new JdbcTransactionFactory(), mock(DataSource.class)));
            org.mockito.Mockito.when(sqlSessionFactory.getConfiguration())
                    .thenReturn(configuration);
            return sqlSessionFactory;
        }
    }
}
