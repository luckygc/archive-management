package github.luckygc.am.module.archive.item.repository;

import java.util.Optional;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.data.repository.By;
import jakarta.data.repository.Find;
import jakarta.data.repository.Repository;

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
}
