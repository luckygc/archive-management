package github.luckygc.am.module.archive.governance.repository;

import java.util.List;
import java.util.Optional;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.data.repository.By;
import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;
import jakarta.data.repository.Update;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.archive.governance.ArchiveGovernanceSchemeVersion;
import github.luckygc.am.module.archive.governance.ArchiveGovernanceSchemeVersionStatus;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveGovernanceSchemeVersionDataRepository {

    @Find
    Optional<ArchiveGovernanceSchemeVersion> findById(@By(By.ID) @Nonnull Long id);

    @Insert
    ArchiveGovernanceSchemeVersion insert(@Nonnull ArchiveGovernanceSchemeVersion entity);

    @Update
    ArchiveGovernanceSchemeVersion update(@Nonnull ArchiveGovernanceSchemeVersion entity);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("id")
    List<ArchiveGovernanceSchemeVersion> findBySchemeId(@Nonnull Long schemeId);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("id")
    List<ArchiveGovernanceSchemeVersion> findBySchemeIdAndStatus(
            @Nonnull Long schemeId, @Nonnull ArchiveGovernanceSchemeVersionStatus status);

    @Nullable @Transactional(readOnly = true)
    @Find
    ArchiveGovernanceSchemeVersion findBySchemeIdAndVersionCode(
            @Nonnull Long schemeId, @Nonnull String versionCode);
}
