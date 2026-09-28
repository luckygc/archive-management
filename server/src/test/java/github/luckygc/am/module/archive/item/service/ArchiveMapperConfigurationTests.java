package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.List;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("档案 Mapper 写入缓存行为")
class ArchiveMapperConfigurationTests {

    @Test
    @DisplayName("INSERT RETURNING 影响数据并刷新 MyBatis 缓存")
    void returningWritesShouldAffectDataAndFlushCache() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/archive/ArchiveMapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments())
                    .parse();
        }
        String namespace = "github.luckygc.am.module.archive.mapper.ArchiveMapper.";

        for (String statementId :
                List.of(
                        "insertArchiveItem",
                        "insertArchiveVolume",
                        "insertArchiveItemElectronicFile",
                        "insertUniqueConstraint",
                        "insertItemRelation",
                        "insertItemLineTable",
                        "insertItemLineField",
                        "insertItemLineRow")) {
            MappedStatement statement = configuration.getMappedStatement(namespace + statementId);
            assertThat(statement.isDirtySelect()).as(statementId + " affectData").isTrue();
            assertThat(statement.isFlushCacheRequired()).as(statementId + " flushCache").isTrue();
        }
    }
}
