package github.luckygc.am.infrastructure.runtime;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

import github.luckygc.am.common.runtime.RuntimeCapabilityConfigurationException;

public class RuntimeCapabilityFailureAnalyzer
        extends AbstractFailureAnalyzer<RuntimeCapabilityConfigurationException> {

    @Override
    protected FailureAnalysis analyze(
            Throwable rootFailure, RuntimeCapabilityConfigurationException cause) {
        return new FailureAnalysis(cause.getMessage(), cause.action(), cause);
    }
}
