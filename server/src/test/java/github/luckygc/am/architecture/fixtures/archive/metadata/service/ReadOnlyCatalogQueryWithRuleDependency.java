package github.luckygc.am.architecture.fixtures.archive.metadata.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.architecture.fixtures.archive.rule.service.RuleService;

@Service
@Profile("archunit-fixture")
public class ReadOnlyCatalogQueryWithRuleDependency {

    private final RuleService ruleService;

    public ReadOnlyCatalogQueryWithRuleDependency(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    @Transactional(readOnly = true)
    public RuleService catalog() {
        return ruleService;
    }
}
