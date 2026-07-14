package github.luckygc.am.module.archive.item.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
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
import github.luckygc.am.common.api.CursorPageTokenContext;
import github.luckygc.am.module.archive.item.service.ArchiveItemRoutingService;
import github.luckygc.am.module.archive.item.service.ArchiveItemRoutingService.ArchiveItemListDto;
import github.luckygc.am.module.archive.item.service.ArchiveItemRoutingService.ArchiveItemOrderBy;
import github.luckygc.am.module.archive.item.service.ArchiveItemRoutingService.SearchArchiveItemsRequest;

@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        classes = ArchiveManagementApplication.class,
        properties = {
            "spring.flyway.locations=classpath:db/migration,classpath:db/sample",
            "archive.search.full-text.provider=postgresql",
            "spring.quartz.auto-startup=false",
            "spring.session.jdbc.cleanup-cron=-",
            "flowable.async-executor-activate=false",
            "flowable.check-process-definitions=false",
            "flowable.eventregistry.enabled=false"
        })
@DisplayName("档案条目全文检索集成")
class ArchiveFullTextSearchIntegrationTests {

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18")
                    .withDatabaseName("archive_management_full_text_test")
                    .withUsername("postgres")
                    .withPassword("postgres");

    @Autowired private ArchiveItemRoutingService archiveItemRoutingService;

    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void grantQueryUserPermissions() {
        grantSuperAdminRole(1L);
    }

    @DynamicPropertySource
    static void registerPostgreSqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Test
    @DisplayName("全文检索与结构化过滤在同一查询语义中生效")
    void discoverItemsCombinesFullTextAndDatabaseFilteringInOneQuery() {
        Long categoryId =
                jdbcTemplate.queryForObject(
                        "select id from am_archive_category where category_code = 'HT'",
                        Long.class);

        var result =
                archiveItemRoutingService.discoverItems(
                        new SearchArchiveItemsRequest(
                                categoryId, "Z001", "档案管理系统", null, null, 50, null, null),
                        1L);

        assertThat(result.items())
                .extracting(row -> row.get("archive_no"))
                .containsExactly("HT-2026-001");
    }

    @Test
    @DisplayName("键集分页使用用户排序并追加兜底排序")
    void searchItemsUsesCursorWithUserOrderAndFallbackOrder() {
        Long categoryId =
                jdbcTemplate.queryForObject(
                        "select id from am_archive_category where category_code = 'GW'",
                        Long.class);

        var firstPage =
                archiveItemRoutingService.searchItems(
                        new SearchArchiveItemsRequest(
                                categoryId,
                                null,
                                null,
                                null,
                                null,
                                1,
                                null,
                                List.of(new ArchiveItemOrderBy("archiveNo", "ASC"))),
                        1L);

        assertThat(firstPage.items())
                .extracting(row -> row.get("archive_no"))
                .containsExactly("GW-2026-001");
        firstPage = encodePage(firstPage);
        assertThat(firstPage.next()).isNotBlank();

        var secondPage =
                archiveItemRoutingService.searchItems(
                        new SearchArchiveItemsRequest(
                                categoryId,
                                null,
                                null,
                                null,
                                null,
                                1,
                                firstPage.next(),
                                List.of(new ArchiveItemOrderBy("archiveNo", "ASC"))),
                        1L);

        assertThat(secondPage.items())
                .extracting(row -> row.get("archive_no"))
                .containsExactly("GW-2026-002");
        secondPage = encodePage(secondPage);
        assertThat(secondPage.prev()).isNotBlank();

        var previousPage =
                archiveItemRoutingService.searchItems(
                        new SearchArchiveItemsRequest(
                                categoryId,
                                null,
                                null,
                                null,
                                null,
                                1,
                                secondPage.prev(),
                                List.of(new ArchiveItemOrderBy("archiveNo", "ASC"))),
                        1L);

        assertThat(previousPage.items())
                .extracting(row -> row.get("archive_no"))
                .containsExactly("GW-2026-001");
    }

    @Test
    @DisplayName("默认键集分页使用创建时间和 ID，并以微秒精度编码 cursor")
    void searchItemsCursorUsesCreatedAtMicrosAndIdFallbackOrder() {
        Long categoryId =
                jdbcTemplate.queryForObject(
                        "select id from am_archive_category where category_code = 'GW'",
                        Long.class);
        jdbcTemplate.update(
                """
                update am_archive_item
                set created_at = timestamp '2026-06-29 10:00:00.123456'
                where archive_no = 'GW-2026-001'
                """);
        jdbcTemplate.update(
                """
                update am_archive_item
                set created_at = timestamp '2026-06-29 10:00:00.123455'
                where archive_no = 'GW-2026-002'
                """);

        var firstPage =
                archiveItemRoutingService.searchItems(
                        new SearchArchiveItemsRequest(
                                categoryId, null, null, null, null, 1, null, null),
                        1L);

        assertThat(firstPage.items())
                .extracting(row -> row.get("archive_no"))
                .containsExactly("GW-2026-001");
        firstPage = encodePage(firstPage);
        assertThat(cursorPayload(firstPage.next()))
                .contains("v4|next|1|test-query|T:2026-06-29T10:00:00.123456", "|L:");

        var secondPage =
                archiveItemRoutingService.searchItems(
                        new SearchArchiveItemsRequest(
                                categoryId, null, null, null, null, 1, firstPage.next(), null),
                        1L);

        assertThat(secondPage.items())
                .extracting(row -> row.get("archive_no"))
                .containsExactly("GW-2026-002");
    }

    @Test
    @DisplayName("可搜索动态字段可以作为远程排序字段")
    void searchItemsSupportsIndexedDynamicFieldOrder() {
        Long categoryId =
                jdbcTemplate.queryForObject(
                        "select id from am_archive_category where category_code = 'GW'",
                        Long.class);

        var firstPage =
                archiveItemRoutingService.searchItems(
                        new SearchArchiveItemsRequest(
                                categoryId,
                                null,
                                null,
                                null,
                                null,
                                1,
                                null,
                                List.of(new ArchiveItemOrderBy("formed_date", "DESC"))),
                        1L);

        assertThat(firstPage.items())
                .extracting(row -> row.get("archive_no"))
                .containsExactly("GW-2026-002");
        firstPage = encodePage(firstPage);
        assertThat(firstPage.next()).isNotBlank();

        var secondPage =
                archiveItemRoutingService.searchItems(
                        new SearchArchiveItemsRequest(
                                categoryId,
                                null,
                                null,
                                null,
                                null,
                                1,
                                firstPage.next(),
                                List.of(new ArchiveItemOrderBy("formed_date", "DESC"))),
                        1L);

        assertThat(secondPage.items())
                .extracting(row -> row.get("archive_no"))
                .containsExactly("GW-2026-001");
    }

    @Test
    @DisplayName("删除档案条目时写入删除时间和删除人")
    void deleteItemWritesDeletedAtAndDeletedByOnItemAndDynamicRows() {
        Long categoryId =
                jdbcTemplate.queryForObject(
                        "select id from am_archive_category where category_code = 'GW'",
                        Long.class);
        String itemTableName =
                jdbcTemplate.queryForObject(
                        "select item_table_name from am_archive_category where id = ?",
                        String.class,
                        categoryId);
        String physicalTableName =
                jdbcTemplate.queryForObject(
                        "select item_physical_table_name from am_archive_category where id = ?",
                        String.class,
                        categoryId);
        Long itemId =
                jdbcTemplate.queryForObject(
                        """
                        insert into am_archive_item
                            (fonds_code, fonds_name, category_code, category_name, archive_no, electronic_status, archive_year)
                        values
                            ('Z000', '集团全宗', 'GW', '公文档案', 'GW-DELETE-META', 'DRAFT', 2026)
                        returning id
                        """,
                        Long.class);
        jdbcTemplate.update(
                "insert into " + itemTableName + " (id, f_title) values (?, ?)", itemId, "待删除条目");
        jdbcTemplate.update(
                "insert into " + physicalTableName + " (id, f_box_no) values (?, ?)",
                itemId,
                "D-001");
        grantSuperAdminRole(99L);

        archiveItemRoutingService.deleteItem(
                itemId, 99L, new ArchiveItemRoutingService.DeleteItemRequest("测试删除"));

        assertDeletedMetadata("am_archive_item", itemId, 99L);
        assertDeletedMetadata(itemTableName, itemId, 99L);
        assertDeletedMetadata(physicalTableName, itemId, 99L);
    }

    private void grantSuperAdminRole(Long userId) {
        jdbcTemplate.update(
                """
                insert into am_authentication_user (id, username, password, display_name)
                values (?, ?, '{noop}test', ?)
                on conflict (id) do nothing
                """,
                userId,
                "test-user-" + userId,
                "测试用户 " + userId);
        jdbcTemplate.update(
                """
                insert into am_authorization_user_role_rel (user_id, role_id)
                select ?, id
                from am_authorization_role
                where role_name = '超级管理员'
                on conflict (user_id, role_id) do nothing
                """,
                userId);
    }

    @Test
    @DisplayName("回收站按删除时间和 ID 稳定分页")
    void searchDeletedItemsUsesDeletedAtAndIdFallbackOrder() {
        Long categoryId =
                jdbcTemplate.queryForObject(
                        "select id from am_archive_category where category_code = 'GW'",
                        Long.class);
        String itemTableName =
                jdbcTemplate.queryForObject(
                        "select item_table_name from am_archive_category where id = ?",
                        String.class,
                        categoryId);
        insertDeletedItem(itemTableName, "GW-TRASH-001", "2099-06-29 11:00:00.123456");
        insertDeletedItem(itemTableName, "GW-TRASH-002", "2099-06-29 11:00:00.123455");

        var firstPage =
                archiveItemRoutingService.searchDeletedItems(
                        new SearchArchiveItemsRequest(
                                categoryId, null, null, null, null, 1, null, null),
                        1L);

        assertThat(firstPage.items())
                .extracting(row -> row.get("archive_no"))
                .containsExactly("GW-TRASH-001");
        firstPage = encodePage(firstPage);
        assertThat(cursorPayload(firstPage.next()))
                .contains("v4|next|1|test-query|T:2099-06-29T11:00:00.123456", "|L:");

        var secondPage =
                archiveItemRoutingService.searchDeletedItems(
                        new SearchArchiveItemsRequest(
                                categoryId, null, null, null, null, 1, firstPage.next(), null),
                        1L);

        assertThat(secondPage.items())
                .extracting(row -> row.get("archive_no"))
                .containsExactly("GW-TRASH-002");
    }

    private Long insertDeletedItem(String itemTableName, String archiveNo, String deletedAt) {
        Long itemId =
                jdbcTemplate.queryForObject(
                        """
                        insert into am_archive_item
                            (fonds_code, fonds_name, category_code, category_name, archive_no, electronic_status,
                             archive_year, deleted_flag, deleted_at, deleted_by)
                        values
                            ('Z000', '集团全宗', 'GW', '公文档案', ?, 'DRAFT',
                             2026, true, timestamp '%s', 99)
                        returning id
                        """
                                .formatted(deletedAt),
                        Long.class,
                        archiveNo);
        jdbcTemplate.update(
                "insert into "
                        + itemTableName
                        + " (id, f_title, deleted_flag, deleted_at, deleted_by) values (?, ?, true, timestamp '"
                        + deletedAt
                        + "', 99)",
                itemId,
                archiveNo);
        return itemId;
    }

    private void assertDeletedMetadata(String tableName, Long id, Long expectedDeletedBy) {
        Boolean deleted =
                jdbcTemplate.queryForObject(
                        "select deleted_flag from " + tableName + " where id = ?",
                        Boolean.class,
                        id);
        LocalDateTime deletedAt =
                jdbcTemplate.queryForObject(
                        "select deleted_at from " + tableName + " where id = ?",
                        LocalDateTime.class,
                        id);
        assertThat(deleted).isTrue();
        assertThat(deletedAt).isNotNull();
        if (expectedDeletedBy != null) {
            Long deletedBy =
                    jdbcTemplate.queryForObject(
                            "select deleted_by from " + tableName + " where id = ?",
                            Long.class,
                            id);
            assertThat(deletedBy).isEqualTo(expectedDeletedBy);
        }
    }

    private String cursorPayload(String cursor) {
        return new String(
                Base64.getUrlDecoder().decode(cursor.split("\\.", 2)[0]), StandardCharsets.UTF_8);
    }

    private ArchiveItemListDto encodePage(ArchiveItemListDto page) {
        return page.encodeCursorTokens(new CursorPageTokenContext("test-query"));
    }
}
