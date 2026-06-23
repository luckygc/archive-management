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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Testcontainers;

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
        properties = {
            "spring.autoconfigure.exclude=org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatAutoConfiguration",
            "spring.quartz.auto-startup=false",
            "spring.session.jdbc.cleanup-cron=-",
            "flowable.async-executor-activate=false",
            "flowable.check-process-definitions=false",
            "flowable.eventregistry.enabled=false"
        })
class ServerApplicationTests extends PostgreSqlContainerTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PowChallengeService powChallengeService;
    @Autowired private ArchiveFondsDataRepository archiveFondsDataRepository;
    @Autowired private JsonMapper jsonMapper;

    @Test
    void contextLoads() {
        Assertions.assertTrue(POSTGRES.isRunning());
    }

    @Test
    void migratedPostgreSqlResourcesAreAvailable() {
        Assertions.assertEquals(
                "archive_management_test",
                jdbcTemplate.queryForObject("select current_database()", String.class));
        Assertions.assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "select count(*) from pg_extension where extname = 'pg_textsearch'",
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
                        "select to_regclass('public.am_archive_record_search')::text",
                        String.class));
        Assertions.assertEquals(
                "idx_am_archive_record_search_bm25",
                jdbcTemplate.queryForObject(
                        "select to_regclass('public.idx_am_archive_record_search_bm25')::text",
                        String.class));
        Assertions.assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "select count(*) from information_schema.columns "
                                + "where table_schema = 'public' "
                                + "and table_name = 'am_archive_field' "
                                + "and column_name = 'exact_searchable'",
                        Integer.class));
        Assertions.assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        "select count(*) from information_schema.columns "
                                + "where table_schema = 'public' "
                                + "and table_name = 'am_archive_field' "
                                + "and column_name = 'full_text_searchable'",
                        Integer.class));
    }

    @Test
    void capChallengeCreationPersistsCreatedAt() {
        PowChallengeService.CapChallengeResponse response = powChallengeService.createChallenge();

        Assertions.assertNotNull(
                jdbcTemplate.queryForObject(
                        "select created_at from am_auth_cap_challenge where token = ?",
                        java.time.LocalDateTime.class,
                        response.token()));
    }

    @Test
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
