package github.luckygc.am.module.archive.library.repository;

import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.data.Limit;
import jakarta.data.repository.By;
import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.archive.library.ArchiveRepositoryChangeHistory;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveRepositoryChangeHistoryDataRepository {

    @Insert
    ArchiveRepositoryChangeHistory insert(@Nonnull ArchiveRepositoryChangeHistory entity);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("id")
    List<ArchiveRepositoryChangeHistory> findByFromRepositoryId(
            @By("fromRepositoryId") @Nonnull Long fromRepositoryId, Limit limit);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("id")
    List<ArchiveRepositoryChangeHistory> findByToRepositoryId(
            @By("toRepositoryId") @Nonnull Long toRepositoryId, Limit limit);
}
