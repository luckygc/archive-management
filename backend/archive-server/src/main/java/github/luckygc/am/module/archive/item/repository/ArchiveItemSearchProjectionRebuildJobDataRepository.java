package github.luckygc.am.module.archive.item.repository;

import java.util.List;
import java.util.Optional;

import jakarta.annotation.Nonnull;
import jakarta.data.Limit;
import jakarta.data.repository.By;
import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;
import jakarta.data.repository.Update;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.archive.item.ArchiveItemSearchProjectionRebuildJob;
import github.luckygc.am.module.archive.item.ArchiveItemSearchProjectionRebuildJobStatus;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveItemSearchProjectionRebuildJobDataRepository {

    @Find
    Optional<ArchiveItemSearchProjectionRebuildJob> findById(@By(By.ID) @Nonnull Long id);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("id")
    List<ArchiveItemSearchProjectionRebuildJob> findByStatus(
            @Nonnull ArchiveItemSearchProjectionRebuildJobStatus status, Limit limit);

    @Insert
    ArchiveItemSearchProjectionRebuildJob insert(
            @Nonnull ArchiveItemSearchProjectionRebuildJob entity);

    @Update
    ArchiveItemSearchProjectionRebuildJob update(
            @Nonnull ArchiveItemSearchProjectionRebuildJob entity);
}
