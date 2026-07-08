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
    @DisplayName("动态档案表查询 Mapper 参数按语义分组")
    void dynamicItemQueriesShouldUseGroupedMapperParameters() {
        assertThat(mapperParamNames("listDynamicItems"))
                .containsExactly("source", "projection", "criteria", "page");
        assertThat(mapperParamNames("countDynamicItems")).containsExactly("source", "criteria");
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
}
