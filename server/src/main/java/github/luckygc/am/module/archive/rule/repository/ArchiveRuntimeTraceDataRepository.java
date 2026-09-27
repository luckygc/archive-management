package github.luckygc.am.module.archive.rule.repository;

import jakarta.annotation.Nonnull;
import jakarta.data.repository.Insert;
import jakarta.data.repository.Repository;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.archive.rule.ArchiveRuntimeTrace;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveRuntimeTraceDataRepository {

    @Insert
    ArchiveRuntimeTrace insert(@Nonnull ArchiveRuntimeTrace entity);
}
