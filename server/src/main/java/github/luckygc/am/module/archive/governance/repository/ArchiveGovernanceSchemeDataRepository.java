package github.luckygc.am.module.archive.governance.repository;

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

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.archive.governance.ArchiveGovernanceScheme;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveGovernanceSchemeDataRepository {

    @Find
    Optional<ArchiveGovernanceScheme> findById(@By(By.ID) @Nonnull Long id);

    @Insert
    ArchiveGovernanceScheme insert(@Nonnull ArchiveGovernanceScheme entity);

    @Update
    ArchiveGovernanceScheme update(@Nonnull ArchiveGovernanceScheme entity);

    @Delete
    void delete(@Nonnull ArchiveGovernanceScheme entity);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("sortOrder")
    @OrderBy("id")
    List<ArchiveGovernanceScheme> list();

    @Transactional(readOnly = true)
    @Find
    @OrderBy("sortOrder")
    @OrderBy("id")
    List<ArchiveGovernanceScheme> list(boolean enabled);

    @Nullable @Transactional(readOnly = true)
    @Find
    ArchiveGovernanceScheme findBySchemeCode(@Nonnull String schemeCode);
}
