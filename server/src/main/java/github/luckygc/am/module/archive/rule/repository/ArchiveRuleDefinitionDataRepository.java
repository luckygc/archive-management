package github.luckygc.am.module.archive.rule.repository;

import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.data.repository.Find;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;

import org.hibernate.annotations.processing.HQL;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.repository.DataRepository;
import github.luckygc.am.module.archive.rule.ArchiveRuleDefinition;
import github.luckygc.am.module.archive.rule.ArchiveRuleStatus;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveRuleDefinitionDataRepository
        extends DataRepository<ArchiveRuleDefinition, Long> {

    @Transactional(readOnly = true)
    @Find
    @OrderBy("priority")
    @OrderBy("ruleCode")
    @OrderBy("id")
    List<ArchiveRuleDefinition> findBySchemeVersionId(@Nonnull Long schemeVersionId);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("priority")
    @OrderBy("ruleCode")
    @OrderBy("id")
    List<ArchiveRuleDefinition> findBySchemeVersionIdAndStatus(
            @Nonnull Long schemeVersionId, @Nonnull ArchiveRuleStatus status);

    @Nullable @Transactional(readOnly = true)
    @Find
    ArchiveRuleDefinition findBySchemeVersionIdAndRuleCode(
            @Nonnull Long schemeVersionId, @Nonnull String ruleCode);

    @Transactional(readOnly = true)
    @HQL(
            """
            select count(rule)
            from ArchiveRuleDefinition rule
            where rule.scopeObjectTypeId = ?1
            """)
    long countByScopeObjectTypeId(@Nonnull Long objectTypeId);

    @Transactional(readOnly = true)
    @HQL(
            """
            select count(rule)
            from ArchiveRuleDefinition rule
            where rule.scopeEventTypeId = ?1
            """)
    long countByScopeEventTypeId(@Nonnull Long eventTypeId);
}
