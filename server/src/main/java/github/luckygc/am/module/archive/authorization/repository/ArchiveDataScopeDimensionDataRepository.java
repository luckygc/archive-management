package github.luckygc.am.module.archive.authorization.repository;

import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Delete;
import jakarta.data.repository.Find;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.archive.authorization.ArchiveDataScopeDimension;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveDataScopeDimensionDataRepository
        extends CrudRepository<ArchiveDataScopeDimension, Long> {

    @Transactional(readOnly = true)
    @Find
    @OrderBy("sortOrder")
    @OrderBy("id")
    List<ArchiveDataScopeDimension> findByScopeId(@Nonnull Long scopeId);

    @Delete
    void deleteByScopeId(@Nonnull Long scopeId);
}
