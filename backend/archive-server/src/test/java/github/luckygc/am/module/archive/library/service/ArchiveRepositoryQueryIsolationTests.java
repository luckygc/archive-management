package github.luckygc.am.module.archive.library.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("正式档案查询虚拟业务库隔离")
class ArchiveRepositoryQueryIsolationTests {

    @Test
    @DisplayName("条目正式查询只读取 HOLDING 角色业务库")
    void itemQueriesShouldFilterHoldingRepository() throws IOException {
        String xml;
        try (var input = getClass().getResourceAsStream("/mapper/archive/ArchiveMapper.xml")) {
            assertThat(input).isNotNull();
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(xml)
                .contains("join am_archive_repository repository")
                .contains("repository.repository_role = 'HOLDING'");
    }
}
