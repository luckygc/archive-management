package github.luckygc.am.module.archive.metadata.repository;

import java.util.List;
import java.util.Optional;

import jakarta.annotation.Nonnull;
import jakarta.data.repository.By;
import jakarta.data.repository.Find;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;
import jakarta.data.repository.Update;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.archive.metadata.ArchiveRetentionPeriod;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveRetentionPeriodDataRepository {

    @Find
    Optional<ArchiveRetentionPeriod> findById(@By(By.ID) @Nonnull Long id);

    @Update
    ArchiveRetentionPeriod update(@Nonnull ArchiveRetentionPeriod entity);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("sortOrder")
    @OrderBy("id")
    List<ArchiveRetentionPeriod> list();

    @Transactional(readOnly = true)
    @Find
    @OrderBy("sortOrder")
    @OrderBy("id")
    List<ArchiveRetentionPeriod> list(boolean enabled);
}
