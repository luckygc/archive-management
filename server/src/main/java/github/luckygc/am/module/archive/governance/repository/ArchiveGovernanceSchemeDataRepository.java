package github.luckygc.am.module.archive.governance.repository;

import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.data.repository.Find;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.repository.DataRepository;
import github.luckygc.am.module.archive.governance.ArchiveGovernanceScheme;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveGovernanceSchemeDataRepository
        extends DataRepository<ArchiveGovernanceScheme, Long> {

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
