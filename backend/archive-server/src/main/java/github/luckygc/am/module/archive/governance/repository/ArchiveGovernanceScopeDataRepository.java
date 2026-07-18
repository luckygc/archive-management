package github.luckygc.am.module.archive.governance.repository;

import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.data.repository.Delete;
import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.archive.governance.ArchiveGovernanceScope;
import github.luckygc.am.module.archive.governance.ArchiveGovernanceScopeType;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveGovernanceScopeDataRepository {

    @Insert
    List<ArchiveGovernanceScope> insertAll(@Nonnull List<ArchiveGovernanceScope> entities);

    @Delete
    void deleteAll(@Nonnull List<ArchiveGovernanceScope> entities);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("id")
    List<ArchiveGovernanceScope> findBySchemeVersionId(@Nonnull Long schemeVersionId);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("id")
    List<ArchiveGovernanceScope> findByScopeTypeAndDefaultFlag(
            @Nonnull ArchiveGovernanceScopeType scopeType, boolean defaultFlag);
}
