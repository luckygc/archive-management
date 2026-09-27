package github.luckygc.am.module.archive.item.repository;

import java.util.Optional;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.data.Limit;
import jakarta.data.repository.By;
import jakarta.data.repository.Find;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;
import jakarta.data.repository.Update;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.archive.item.ArchiveItem;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveItemDataRepository {

    @Find
    Optional<ArchiveItem> findById(@By(By.ID) @Nonnull Long id);

    @Nullable @Transactional(readOnly = true)
    @Find
    ArchiveItem findByArchiveNo(@Nonnull String categoryCode, @Nonnull String archiveNo);

    @Update
    ArchiveItem update(@Nonnull ArchiveItem entity);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("id")
    java.util.List<ArchiveItem> findByRepositoryId(
            @By("repositoryId") @Nonnull Long repositoryId, Limit limit);
}
