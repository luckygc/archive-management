package github.luckygc.am.module.archive.authorization.repository;

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

import github.luckygc.am.module.archive.authorization.ArchiveDataScope;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveDataScopeDataRepository {

    @Find
    Optional<ArchiveDataScope> findById(@By(By.ID) @Nonnull Long id);

    @Insert
    ArchiveDataScope insert(@Nonnull ArchiveDataScope entity);

    @Update
    ArchiveDataScope update(@Nonnull ArchiveDataScope entity);

    @Nullable @Transactional(readOnly = true)
    @Find
    ArchiveDataScope findByScopeCode(@Nonnull String scopeCode);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("scopeCode")
    List<ArchiveDataScope> list(boolean enabled);
}
