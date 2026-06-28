package github.luckygc.am;

import static org.mockito.Mockito.mock;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

import org.apache.commons.codec.digest.DigestUtils;
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
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.SessionRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import github.luckygc.am.app.ArchiveManagementApplication;
import github.luckygc.am.common.api.LongStringSerializer;
import github.luckygc.am.infrastructure.hibernate.SecurityAuditingInterceptor;
import github.luckygc.am.module.archive.metadata.ArchiveFonds;
import github.luckygc.am.module.archive.metadata.ArchiveFondsDataRepository;
import github.luckygc.am.module.auth.ArchiveUserDetails;
import github.luckygc.am.module.auth.PowChallengeService;
import github.luckygc.am.test.PostgreSqlContainerTest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        classes = ArchiveManagementApplication.class,
        properties = {
            "spring.autoconfigure.exclude=org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatAutoConfiguration",
            "spring.quartz.auto-startup=false",
            "spring.session.jdbc.cleanup-cron=-",
            "flowable.async-executor-activate=false",
            "flowable.check-process-definitions=false",
            "flowable.eventregistry.enabled=false"
        })
@DisplayName("服务端应用启动与基础合同")
class ServerApplicationTests extends PostgreSqlContainerTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PowChallengeService powChallengeService;
    @Autowired private ArchiveFondsDataRepository archiveFondsDataRepository;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private SessionRepository<?> sessionRepository;
    @Autowired private CacheManager cacheManager;

    @Test
    @DisplayName("应用上下文启动后使用 JDBC 会话和 Quartz JDBC 调度")
    void contextLoads() {
        Assertions.assertTrue(POSTGRES.isRunning());
        Assertions.assertInstanceOf(JdbcIndexedSessionRepository.class, sessionRepository);
        Assertions.assertInstanceOf(NoOpCacheManager.class, cacheManager);
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
                "am_archive_record_search",
                jdbcTemplate.queryForObject(
                        "select to_regclass('am_archive_record_search')::text", String.class));
        Assertions.assertEquals(
                "idx_am_archive_record_search_trgm",
                jdbcTemplate.queryForObject(
                        "select to_regclass('idx_am_archive_record_search_trgm')::text",
                        String.class));
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

    @Test
    @DisplayName("高增长业务 ID 使用 PostgreSQL sequence 预留内置数据区间")
    void highGrowthBusinessIdsUseReservedPostgreSqlSequences() {
        assertSequence("am_archive_record_id_seq");
        assertSequence("am_archive_record_electronic_file_id_seq");
        assertSequence("am_archive_physical_object_id_seq");
        assertSequence("am_storage_object_id_seq");
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
    @DisplayName("CAP 挑战创建时持久化创建时间")
    void capChallengeCreationPersistsCreatedAt() {
        PowChallengeService.CapChallengeResponse response = powChallengeService.createChallenge();

        Assertions.assertNotNull(
                jdbcTemplate.queryForObject(
                        "select created_at from am_auth_cap_challenge where token = ?",
                        java.time.LocalDateTime.class,
                        response.token()));
    }

    @Test
    @DisplayName("CAP challenge 和 redeem 响应符合前端组件格式")
    void capChallengeAndRedeemResponsesMatchWidgetFormat() throws Exception {
        PowChallengeService.CapChallengeResponse response = powChallengeService.createChallenge();

        JsonNode challengeJson = jsonMapper.readTree(jsonMapper.writeValueAsString(response));
        Assertions.assertTrue(challengeJson.get("challenge").isObject());
        Assertions.assertTrue(challengeJson.get("challenge").get("c").isInt());
        Assertions.assertTrue(challengeJson.get("challenge").get("s").isInt());
        Assertions.assertTrue(challengeJson.get("challenge").get("d").isInt());
        Assertions.assertTrue(challengeJson.get("token").isTextual());
        Assertions.assertTrue(challengeJson.get("expires").isNumber());

        var redeemResult =
                powChallengeService.redeemChallenge(
                        new PowChallengeService.CapRedeemCommand(
                                response.token(), solveCapChallenge(response)));

        JsonNode redeemJson = jsonMapper.readTree(jsonMapper.writeValueAsString(redeemResult));
        Assertions.assertEquals(true, redeemJson.get("success").booleanValue());
        Assertions.assertTrue(redeemJson.get("token").isTextual());
        Assertions.assertTrue(redeemJson.get("expires").isNumber());
    }

    @Test
    @DisplayName("LongStringSerializer 只作用于显式标注字段")
    void jsonLongStringSerializationAppliesOnlyToAnnotatedFields() throws Exception {
        JsonNode json =
                jsonMapper.readTree(
                        jsonMapper.writeValueAsString(
                                new LongFieldResponse(1L, 2L, 1_782_192_922_952L)));

        Assertions.assertTrue(json.get("id").isTextual());
        Assertions.assertTrue(json.get("categoryId").isNumber());
        Assertions.assertTrue(json.get("expires").isNumber());
    }

    @Test
    @DisplayName("CAP 挑战解答只能兑换一次登录 token")
    void capChallengeRedeemAcceptsGeneratedChallengeOnce() {
        PowChallengeService.CapChallengeResponse response = powChallengeService.createChallenge();

        List<Long> solutions = solveCapChallenge(response);
        var redeemResult =
                powChallengeService.redeemChallenge(
                        new PowChallengeService.CapRedeemCommand(response.token(), solutions));

        Assertions.assertEquals(true, redeemResult.get("success"));
        Assertions.assertNotNull(redeemResult.get("token"));
    }

    @Test
    @DisplayName("无状态 Repository 从安全上下文填充审计字段")
    void statelessRepositoryFillsAuditFieldsFromSecurityContext() {
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
            fonds.setEnabled(true);
            fonds.setSortOrder(0);

            archiveFondsDataRepository.insert(fonds);

            LocalDateTime createdAt =
                    jdbcTemplate.queryForObject(
                            "select created_at from am_archive_fonds where fonds_code = 'AUDIT_TEST'",
                            LocalDateTime.class);
            LocalDateTime updatedAt =
                    jdbcTemplate.queryForObject(
                            "select updated_at from am_archive_fonds where fonds_code = 'AUDIT_TEST'",
                            LocalDateTime.class);
            Assertions.assertNotNull(createdAt);
            Assertions.assertNotNull(updatedAt);
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
            ArchiveFonds saved = archiveFondsDataRepository.find("AUDIT_TEST", false).orElseThrow();
            saved.setFondsName("审计测试全宗-更新");
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
            Assertions.assertFalse(updatedAtAfterUpdate.isBefore(updatedAt));
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

    @Test
    @DisplayName("无状态 upsert 不覆盖创建审计字段")
    void statelessUpsertDoesNotOverwriteCreationAuditFields() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                new ArchiveUserDetails(
                                        101L,
                                        "audit-upsert",
                                        "N/A",
                                        true,
                                        "审计 upsert 用户",
                                        java.util.List.of()),
                                "N/A",
                                java.util.List.of()));
        try {
            LocalDateTime createdAt = LocalDateTime.now().minusDays(1);
            ArchiveFonds fonds = new ArchiveFonds();
            fonds.setCreatedAt(createdAt);
            fonds.setCreatedBy(77L);

            new SecurityAuditingInterceptor()
                    .onUpsert(
                            fonds,
                            1L,
                            new Object[] {createdAt, 77L, null, null},
                            new String[] {"createdAt", "createdBy", "updatedAt", "updatedBy"},
                            new org.hibernate.type.Type[4]);

            Assertions.assertEquals(createdAt, fonds.getCreatedAt());
            Assertions.assertEquals(77L, fonds.getCreatedBy());
            Assertions.assertNotNull(fonds.getUpdatedAt());
            Assertions.assertEquals(101L, fonds.getUpdatedBy());
        } finally {
            SecurityContextHolder.clearContext();
        }
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

    private static List<Long> solveCapChallenge(PowChallengeService.CapChallengeResponse response) {
        List<Long> solutions = new ArrayList<>(response.challenge().c());
        for (int index = 0; index < response.challenge().c(); index++) {
            String salt = prng(response.token() + (index + 1), response.challenge().s());
            String target = prng(response.token() + (index + 1) + "d", response.challenge().d());
            long nonce = 0;
            while (!DigestUtils.sha256Hex(salt + nonce).startsWith(target)) {
                nonce++;
            }
            solutions.add(nonce);
        }
        return solutions;
    }

    private static String prng(String seed, int length) {
        long state = fnv1a(seed);
        StringBuilder result = new StringBuilder(length);
        while (result.length() < length) {
            state ^= (state << 13) & 0xffff_ffffL;
            state &= 0xffff_ffffL;
            state ^= state >>> 17;
            state &= 0xffff_ffffL;
            state ^= (state << 5) & 0xffff_ffffL;
            state &= 0xffff_ffffL;
            result.append("%08x".formatted(state));
        }
        return result.substring(0, length);
    }

    private static long fnv1a(String value) {
        long hash = 2166136261L;
        for (int index = 0; index < value.length(); index++) {
            hash ^= value.charAt(index);
            hash =
                    (hash
                                    + ((hash << 1) & 0xffff_ffffL)
                                    + ((hash << 4) & 0xffff_ffffL)
                                    + ((hash << 7) & 0xffff_ffffL)
                                    + ((hash << 8) & 0xffff_ffffL)
                                    + ((hash << 24) & 0xffff_ffffL))
                            & 0xffff_ffffL;
        }
        return hash;
    }

    private record LongFieldResponse(
            @JsonSerialize(using = LongStringSerializer.class) Long id,
            Long categoryId,
            long expires) {}
}
