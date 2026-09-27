package github.luckygc.am.infrastructure.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.common.search.ArchiveSearchConfigurationException;

@DisplayName("档案搜索配置失败分析器")
class ArchiveSearchConfigurationFailureAnalyzerTests {

    @Test
    @DisplayName("搜索配置异常生成可读失败分析")
    void buildReadableFailureAnalysisForSearchConfigurationException() {
        ArchiveSearchConfigurationFailureAnalyzer analyzer =
                new ArchiveSearchConfigurationFailureAnalyzer();

        var analysis =
                analyzer.analyze(
                        new ArchiveSearchConfigurationException("搜索配置不合法", "修正 archive.search 配置"));

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription()).isEqualTo("搜索配置不合法");
        assertThat(analysis.getAction()).isEqualTo("修正 archive.search 配置");
    }
}
