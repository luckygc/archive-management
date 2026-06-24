package github.luckygc.am.infrastructure.search;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

import github.luckygc.am.common.search.ArchiveSearchConfigurationException;

public class ArchiveSearchConfigurationFailureAnalyzer
        extends AbstractFailureAnalyzer<ArchiveSearchConfigurationException> {

    @Override
    protected FailureAnalysis analyze(
            Throwable rootFailure, ArchiveSearchConfigurationException cause) {
        return new FailureAnalysis(cause.getMessage(), cause.action(), cause);
    }
}
