package github.luckygc.am.infrastructure.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.common.runtime.RuntimeCapabilityConfigurationException;

@DisplayName("运行时配置失败分析器")
class RuntimeCapabilityFailureAnalyzerTests {

    @Test
    @DisplayName("运行时配置异常生成可读失败分析")
    void buildReadableFailureAnalysisForRuntimeConfigurationException() {
        RuntimeCapabilityFailureAnalyzer analyzer = new RuntimeCapabilityFailureAnalyzer();

        var analysis =
                analyzer.analyze(
                        new RuntimeCapabilityConfigurationException(
                                "运行时配置不合法", "修正 archive.runtime 配置"));

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription()).isEqualTo("运行时配置不合法");
        assertThat(analysis.getAction()).isEqualTo("修正 archive.runtime 配置");
    }
}
