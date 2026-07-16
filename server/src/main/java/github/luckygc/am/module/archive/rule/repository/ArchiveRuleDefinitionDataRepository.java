package github.luckygc.am.module.archive.rule.repository;

import java.util.List;
import java.util.Optional;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.data.repository.By;
import jakarta.data.repository.Delete;
import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;
import jakarta.data.repository.Update;

import org.hibernate.annotations.processing.HQL;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.archive.rule.ArchiveRuleDefinition;
import github.luckygc.am.module.archive.rule.ArchiveRuleStatus;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveRuleDefinitionDataRepository {

    @Find
    Optional<ArchiveRuleDefinition> findById(@By(By.ID) @Nonnull Long id);

    @Insert
    ArchiveRuleDefinition insert(@Nonnull ArchiveRuleDefinition entity);

    @Update
    ArchiveRuleDefinition update(@Nonnull ArchiveRuleDefinition entity);

    @Delete
    void delete(@Nonnull ArchiveRuleDefinition entity);

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
