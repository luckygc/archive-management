package github.luckygc.am.infrastructure.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import github.luckygc.am.app.ArchiveManagementApplication;
import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.infrastructure.audit.AuditContext;
import github.luckygc.am.module.archive.mapper.ArchiveSqlAssignment;
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
@Import(MyBatisAuditingIntegrationTests.FixedClockConfiguration.class)
@DisplayName("MyBatis 通用审计集成")
class MyBatisAuditingIntegrationTests extends PostgreSqlContainerTest {

    private static final String ARCHIVE_MAPPER =
            "github.luckygc.am.module.archive.mapper.ArchiveMapper.";
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 7, 16, 10, 30);
    private static final LocalDateTime ORIGINAL_CREATED_AT = LocalDateTime.of(2026, 7, 15, 8, 0);
    private static final long AUTHENTICATED_USER_ID = 42L;
    private static final long FORGED_USER_ID = 999L;

    @Autowired private SqlSession sqlSession;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanFixtureBeforeTest() {
        clearSecurityAndFixtures();
    }

    @AfterEach
    void cleanFixtureAfterTest() {
        clearSecurityAndFixtures();
    }

    @Test
    @DisplayName("RETURNING 写入覆盖伪造审计上下文并使用认证用户")
    void returningWriteUsesFixedAuthenticatedAuditContext() {
        authenticate(AUTHENTICATED_USER_ID);
        Map<String, Object> parameters = archiveItemParameters("AUDIT-AUTHENTICATED");
        parameters.put("userId", FORGED_USER_ID);
        parameters.put("_audit", forgedAuditContext());

        Long id = sqlSession.selectOne(ARCHIVE_MAPPER + "insertArchiveItem", parameters);

        AuditRow row = auditRow(id);
        assertThat(row.createdAt()).isEqualTo(FIXED_NOW);
        assertThat(row.updatedAt()).isEqualTo(FIXED_NOW);
        assertThat(row.createdBy()).isEqualTo(AUTHENTICATED_USER_ID);
        assertThat(row.updatedBy()).isEqualTo(AUTHENTICATED_USER_ID);
    }

    @Test
    @DisplayName("普通更新保持创建审计并分离锁定人与更新人")
    void updateKeepsCreationAuditAndSeparatesBusinessUser() {
        Long id = insertFixture("AUDIT-UPDATE", ORIGINAL_CREATED_AT, 7L);
        authenticate(AUTHENTICATED_USER_ID);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("id", id);
        parameters.put("lockReason", "审计集成测试");
        parameters.put("lockedBy", 77L);
        parameters.put("_audit", forgedAuditContext());

        int updated = sqlSession.update(ARCHIVE_MAPPER + "lockArchiveItem", parameters);

        assertThat(updated).isOne();
        AuditRow row = auditRow(id);
        assertThat(row.createdAt()).isEqualTo(ORIGINAL_CREATED_AT);
        assertThat(row.createdBy()).isEqualTo(7L);
        assertThat(row.updatedAt()).isEqualTo(FIXED_NOW);
        assertThat(row.updatedBy()).isEqualTo(AUTHENTICATED_USER_ID);
        assertThat(row.lockedBy()).isEqualTo(77L);
    }

    @Test
    @DisplayName("未认证 RETURNING 写入仍使用固定时间且用户审计为空")
    void unauthenticatedReturningWriteUsesFixedTimeAndNullUser() {
        Map<String, Object> parameters = archiveItemParameters("AUDIT-UNAUTHENTICATED");
        parameters.put("userId", FORGED_USER_ID);
        parameters.put("_audit", forgedAuditContext());

        Long id = sqlSession.selectOne(ARCHIVE_MAPPER + "insertArchiveItem", parameters);

        AuditRow row = auditRow(id);
        assertThat(row.createdAt()).isEqualTo(FIXED_NOW);
        assertThat(row.updatedAt()).isEqualTo(FIXED_NOW);
        assertThat(row.createdBy()).isNull();
        assertThat(row.updatedBy()).isNull();
    }

    @Test
    @DisplayName("动态表插入和更新使用同一审计时钟且更新不改创建时间")
    void dynamicTableWritesUseFixedAuditTime() {
        jdbcTemplate.execute(
                """
                create table am_audit_dynamic_test
                (
                    id bigint primary key,
                    title varchar(100) not null,
                    deleted_flag boolean not null default false,
                    created_at timestamp not null,
                    updated_at timestamp not null
                )
                """);
        Map<String, Object> insertParameters = new HashMap<>();
        insertParameters.put("tableName", "am_audit_dynamic_test");
        insertParameters.put("columns", "id, title");
        insertParameters.put("values", List.of(1L, "before"));
        insertParameters.put("_audit", forgedAuditContext());

        int inserted = sqlSession.insert(ARCHIVE_MAPPER + "insertDynamicRecord", insertParameters);

        assertThat(inserted).isOne();
        assertThat(dynamicAuditRow())
                .isEqualTo(new DynamicAuditRow("before", FIXED_NOW, FIXED_NOW));

        jdbcTemplate.update(
                "update am_audit_dynamic_test set created_at = ?, updated_at = ? where id = 1",
                ORIGINAL_CREATED_AT,
                ORIGINAL_CREATED_AT);
        Map<String, Object> updateParameters = new HashMap<>();
        updateParameters.put("tableName", "am_audit_dynamic_test");
        updateParameters.put("id", 1L);
        updateParameters.put("assignments", List.of(new ArchiveSqlAssignment("title", "after")));
        updateParameters.put("_audit", forgedAuditContext());

        int updated = sqlSession.update(ARCHIVE_MAPPER + "updateDynamicRecord", updateParameters);

        assertThat(updated).isOne();
        assertThat(dynamicAuditRow())
                .isEqualTo(new DynamicAuditRow("after", ORIGINAL_CREATED_AT, FIXED_NOW));
    }

    private Map<String, Object> archiveItemParameters(String archiveNo) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("archiveLevel", "ITEM");
        parameters.put("volumeId", null);
        parameters.put("fondsCode", "AUDIT-FONDS");
        parameters.put("fondsName", "审计测试全宗");
        parameters.put("categoryCode", "AUDIT-CATEGORY");
        parameters.put("categoryName", "审计测试分类");
        parameters.put("archiveNo", archiveNo);
        parameters.put("electronicStatus", "NONE");
        parameters.put("securityLevelId", null);
        parameters.put("retentionPeriodId", null);
        parameters.put("archiveYear", 2026);
        parameters.put("governanceSchemeVersionId", null);
        return parameters;
    }

    private Long insertFixture(String archiveNo, LocalDateTime createdAt, Long createdBy) {
        return jdbcTemplate.queryForObject(
                """
                insert into am_archive_item
                    (fonds_code, fonds_name, category_code, category_name, archive_no,
                     electronic_status, archive_year, created_by, created_at, updated_by, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                returning id
                """,
                Long.class,
                "AUDIT-FONDS",
                "审计测试全宗",
                "AUDIT-CATEGORY",
                "审计测试分类",
                archiveNo,
                "NONE",
                2026,
                createdBy,
                createdAt,
                createdBy,
                createdAt);
    }

    private AuditRow auditRow(Long id) {
        return jdbcTemplate.queryForObject(
                """
                select created_at, updated_at, created_by, updated_by, locked_by
                from am_archive_item
                where id = ?
                """,
                (resultSet, rowNumber) ->
                        new AuditRow(
                                resultSet.getObject("created_at", LocalDateTime.class),
                                resultSet.getObject("updated_at", LocalDateTime.class),
                                resultSet.getObject("created_by", Long.class),
                                resultSet.getObject("updated_by", Long.class),
                                resultSet.getObject("locked_by", Long.class)),
                id);
    }

    private DynamicAuditRow dynamicAuditRow() {
        return jdbcTemplate.queryForObject(
                "select title, created_at, updated_at from am_audit_dynamic_test where id = 1",
                (resultSet, rowNumber) ->
                        new DynamicAuditRow(
                                resultSet.getString("title"),
                                resultSet.getObject("created_at", LocalDateTime.class),
                                resultSet.getObject("updated_at", LocalDateTime.class)));
    }

    private AuditContext forgedAuditContext() {
        return new AuditContext(LocalDateTime.of(2000, 1, 1, 0, 0), FORGED_USER_ID);
    }

    private void authenticate(long userId) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(
                                new TestAuthenticatedUser(userId), "N/A", List.of()));
    }

    private void clearSecurityAndFixtures() {
        SecurityContextHolder.clearContext();
        jdbcTemplate.execute("drop table if exists am_audit_dynamic_test");
        jdbcTemplate.update("delete from am_archive_item where archive_no like 'AUDIT-%'");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedAuditClock() {
            return Clock.fixed(Instant.parse("2026-07-16T02:30:00Z"), ZONE_ID);
        }
    }

    private record TestAuthenticatedUser(Long id) implements AuthenticatedUser {

        @Override
        public String displayName() {
            return "审计测试用户";
        }
    }

    private record AuditRow(
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long createdBy,
            Long updatedBy,
            Long lockedBy) {}

    private record DynamicAuditRow(
            String title, LocalDateTime createdAt, LocalDateTime updatedAt) {}
}
