package github.luckygc.am.module.archive.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("档案 Mapper 数据范围 SQL")
class ArchiveMapperDataScopeSqlTests {

    @Test
    @DisplayName("部门范围生成主表 department_id 谓词")
    void listDynamicItemsShouldApplyDepartmentDataScope() throws Exception {
        Configuration configuration = new Configuration();
        try (Reader reader = Resources.getResourceAsReader("mapper/archive/ArchiveMapper.xml")) {
            new XMLMapperBuilder(
                            reader,
                            configuration,
                            "mapper/archive/ArchiveMapper.xml",
                            configuration.getSqlFragments())
                    .parse();
        }
        MappedStatement statement =
                configuration.getMappedStatement(
                        "github.luckygc.am.module.archive.mapper.ArchiveMapper.listDynamicItems");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("tableName", "am_archive_item_contract");
        parameters.put("selectColumns", "");
        parameters.put("archiveLevel", "ITEM");
        parameters.put("deleted", false);
        parameters.put("fondsCode", null);
        parameters.put(
                "dataScopeGroups",
                List.of(
                        new ArchiveDataScopeSqlGroup(
                                List.of(), List.of(), List.of(), List.of(5L), List.of())));
        parameters.put("conditions", List.of());
        parameters.put("relatedGroups", List.of());
        parameters.put("fullTextKeyword", null);
        parameters.put("userId", 9L);
        parameters.put("requireAuthenticatedUser", false);
        parameters.put("orderBySql", "i.id desc");
        parameters.put("cursorPredicateSql", null);
        parameters.put("cursorValues", List.of());
        parameters.put("limit", 100);

        String sql = statement.getBoundSql(parameters).getSql().replaceAll("\\s+", " ");

        assertThat(sql).contains("i.department_id in ( ? )");
    }
}
