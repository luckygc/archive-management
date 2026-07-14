package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.module.archive.mapper.ArchiveMapper;

@DisplayName("档案 Mapper XML 合同")
class ArchiveMapperXmlContractTests {

    @Test
    @DisplayName("明细行列表使用显式投影、复合游标与 pageSize 加一窗口")
    void lineRowListShouldUseExplicitProjectionAndCompositeCursor() throws Exception {
        String sql = selectStatement(archiveMapperXml(), "listItemLineRows");

        assertThat(mapperParamNames("listItemLineRows")).containsExactly("query");
        assertThat(sql).doesNotContain("select *");
        assertThat(sql).contains("collection=\"query.selectColumns\"");
        assertThat(sql).contains("line_order &gt; #{query.cursorLineOrder}");
        assertThat(sql).contains("id &gt; #{query.cursorId}");
        assertThat(sql).contains("line_order &lt; #{query.cursorLineOrder}");
        assertThat(sql).contains("id &lt; #{query.cursorId}");
        assertThat(sql).contains("limit #{query.rowLimit}");
    }

    @Test
    @DisplayName("明细行写入仅拼接服务端标识符并参数绑定所有请求值")
    void lineRowWritesShouldBindValuesAndScopeByPath() throws Exception {
        String insert = selectStatement(archiveMapperXml(), "insertItemLineRow");
        String update = updateStatement(archiveMapperXml(), "updateItemLineRow");
        String delete = updateStatement(archiveMapperXml(), "deleteItemLineRow");

        assertThat(insert).contains("${command.tableName}", "${assignment.columnName}");
        assertThat(insert).contains("#{assignment.value}");
        assertThat(insert).doesNotContain("${assignment.value}");
        assertThat(update).contains("id = #{command.rowId}", "item_id = #{command.itemId}");
        assertThat(delete)
                .contains(
                        "deleted_flag = true",
                        "deleted_at = localtimestamp",
                        "deleted_by = #{command.userId}",
                        "item_id = #{command.itemId}");
    }

    @Test
    @DisplayName("搜索投影明细查询不使用星号暴露审计列")
    void lineProjectionShouldUseExplicitColumns() throws Exception {
        String sql = selectStatement(archiveMapperXml(), "listItemLineRowsForProjection");

        assertThat(sql).doesNotContain("select *");
        assertThat(sql).contains("collection=\"query.selectColumns\"");
        assertThat(sql).contains("deleted_flag = false", "order by line_order, id");
    }

    @Test
    @DisplayName("动态档案表查询 Mapper 参数按语义分组")
    void dynamicItemQueriesShouldUseGroupedMapperParameters() {
        assertThat(mapperParamNames("listDynamicItems"))
                .containsExactly("source", "projection", "criteria", "page");
        assertThat(mapperParamNames("countDynamicItems")).containsExactly("source", "criteria");
        assertThat(mapperParamNames("summarizeDynamicItems")).containsExactly("source", "criteria");
    }

    @Test
    @DisplayName("工作台摘要复用动态查询范围并避免电子文件放大档案计数")
    void workspaceSummaryShouldReuseDynamicCriteriaAndCountDistinctFiles() throws Exception {
        String sql = selectStatement(archiveMapperXml(), "summarizeDynamicItems");

        assertThat(sql).contains("<include refid=\"dynamicItemFromWhere\"/>");
        assertThat(sql).contains("count(distinct visible.id)");
        assertThat(sql).contains("filter (where visible.electronic_status = 'DRAFT')");
        assertThat(sql).contains("filter (where visible.locked_flag = true)");
        assertThat(sql).contains("count(distinct ef.id)");
        assertThat(sql).contains("left join am_archive_item_electronic_file ef");
        assertThat(sql).doesNotContain("ef.deleted_flag", "ef.deleted_at");
    }

    @Test
    @DisplayName("动态档案表查询不在 SQL 中重复兜底认证状态")
    void dynamicItemQueriesShouldNotGuardAuthenticatedUserInSql() throws Exception {
        String xml = archiveMapperXml();

        assertThat(xml).doesNotContain("requireAuthenticatedUser");
        assertThat(xml).doesNotContain("#{userId} is not null");
    }

    @Test
    @DisplayName("动态档案表查询用 requestedFondsCode 表达用户全宗筛选")
    void dynamicItemQueriesShouldNameUserFondsFilterClearly() throws Exception {
        String dynamicItemFromWhere = dynamicItemFromWhere(archiveMapperXml());

        assertThat(dynamicItemFromWhere).contains("criteria.requestedFondsCode != null");
        assertThat(dynamicItemFromWhere).contains("i.fonds_code = #{criteria.requestedFondsCode}");
        assertThat(dynamicItemFromWhere).doesNotContain("fondsCode != null and fondsCode != ''");
        assertThat(dynamicItemFromWhere).doesNotContain("i.fonds_code = #{fondsCode}");
    }

    @Test
    @DisplayName("动态档案查询按固定字段 volumeId 过滤卷内档案")
    void dynamicItemQueriesShouldFilterByVolumeId() throws Exception {
        String dynamicItemFromWhere = dynamicItemFromWhere(archiveMapperXml());

        assertThat(dynamicItemFromWhere).contains("criteria.volumeId != null");
        assertThat(dynamicItemFromWhere).contains("i.volume_id = #{criteria.volumeId}");
    }

    @Test
    @DisplayName("动态档案查询将合法空数据范围组解释为当前分类全部可见")
    void dynamicItemQueriesShouldAllowMatchedCategoryOnlyScope() throws Exception {
        assertThat(emptyScopeGroupFallback(dynamicItemFromWhere(archiveMapperXml())))
                .contains("true")
                .doesNotContain("false");
    }

    @Test
    @DisplayName("动态档案列表 SQL 由 XML 消费结构化分页和投影参数")
    void dynamicItemListSqlShouldRenderProjectionAndPageInXml() throws Exception {
        String listDynamicItems = selectStatement(archiveMapperXml(), "listDynamicItems");

        assertThat(listDynamicItems).doesNotContain("${selectColumns}");
        assertThat(listDynamicItems).doesNotContain("${orderBySql}");
        assertThat(listDynamicItems).doesNotContain("${cursorPredicateSql}");
        assertThat(listDynamicItems).contains("collection=\"projection.fields\"");
        assertThat(listDynamicItems).contains("collection=\"page.orders\"");
        assertThat(listDynamicItems).contains("collection=\"page.cursorPredicates\"");
    }

    @Test
    @DisplayName("关系分页 SQL 在数据库内解析另一端、数据范围和 id 游标")
    void relationListSqlShouldFilterRelatedEndpointInsideCursorQuery() throws Exception {
        String sql = selectStatement(archiveMapperXml(), "listItemRelations");

        assertThat(mapperParamNames("listItemRelations"))
                .containsExactly("archiveItemId", "criteria", "page");
        assertThat(sql).contains("rel.source_item_id = #{archiveItemId}");
        assertThat(sql).contains("rel.target_item_id = #{archiveItemId}");
        assertThat(sql).contains("case when rel.source_item_id = #{archiveItemId}");
        assertThat(sql).contains("criteria.targetScopes");
        assertThat(sql).contains("scope.groups");
        assertThat(sql).contains("${scope.tableName}");
        assertThat(sql).contains("rel.id &gt; #{page.cursorId}");
        assertThat(sql).contains("rel.id &lt; #{page.cursorId}");
        assertThat(sql).contains("limit #{page.rowLimit}");
    }

    @Test
    @DisplayName("关系查询将合法空数据范围组解释为目标分类全部可见")
    void relationListSqlShouldAllowMatchedCategoryOnlyScope() throws Exception {
        assertThat(
                        emptyScopeGroupFallback(
                                selectStatement(archiveMapperXml(), "listItemRelations")))
                .contains("true")
                .doesNotContain("false");
    }

    @Test
    @DisplayName("档案 Mapper XML 能被 MyBatis 解析")
    void archiveMapperXmlShouldBeParseable() throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream input =
                getClass()
                        .getClassLoader()
                        .getResourceAsStream("mapper/archive/ArchiveMapper.xml")) {
            new XMLMapperBuilder(
                            input,
                            configuration,
                            "mapper/archive/ArchiveMapper.xml",
                            configuration.getSqlFragments())
                    .parse();
        }

        assertThat(
                        configuration.hasStatement(
                                "github.luckygc.am.module.archive.mapper.ArchiveMapper"
                                        + ".listDynamicItems"))
                .isTrue();
    }

    private List<String> mapperParamNames(String methodName) {
        Method method =
                Arrays.stream(ArchiveMapper.class.getDeclaredMethods())
                        .filter(candidate -> candidate.getName().equals(methodName))
                        .findFirst()
                        .orElseThrow();
        return Arrays.stream(method.getParameters())
                .map(parameter -> parameter.getAnnotation(Param.class))
                .map(Param::value)
                .toList();
    }

    private String archiveMapperXml() throws Exception {
        return new String(
                getClass()
                        .getClassLoader()
                        .getResourceAsStream("mapper/archive/ArchiveMapper.xml")
                        .readAllBytes(),
                StandardCharsets.UTF_8);
    }

    private String dynamicItemFromWhere(String xml) {
        int start = xml.indexOf("<sql id=\"dynamicItemFromWhere\">");
        int end = xml.indexOf("</sql>", start);
        return xml.substring(start, end);
    }

    private String selectStatement(String xml, String id) {
        int start = xml.indexOf("<select id=\"" + id + "\"");
        int end = xml.indexOf("</select>", start);
        return xml.substring(start, end);
    }

    private String updateStatement(String xml, String id) {
        int start = xml.indexOf("<update id=\"" + id + "\"");
        int end = xml.indexOf("</update>", start);
        return xml.substring(start, end);
    }

    private String emptyScopeGroupFallback(String sql) {
        int start = sql.indexOf("<if test=\"(group.fondsCodes == null");
        int end = sql.indexOf("</if>", start);
        return sql.substring(start, end);
    }
}
