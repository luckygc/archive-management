package github.luckygc.am.module.archive.authorization.repository;

import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Find;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.archive.authorization.ArchiveDataScope;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveDataScopeDataRepository extends CrudRepository<ArchiveDataScope, Long> {

    @Nullable @Transactional(readOnly = true)
    @Find
    ArchiveDataScope findByScopeCode(@Nonnull String scopeCode);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("scopeCode")
    List<ArchiveDataScope> list(boolean enabled);
}
