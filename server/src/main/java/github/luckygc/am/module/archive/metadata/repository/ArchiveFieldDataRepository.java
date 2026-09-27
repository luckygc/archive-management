package github.luckygc.am.module.archive.metadata.repository;

import java.util.List;
import java.util.Optional;

import jakarta.annotation.Nonnull;
import jakarta.data.repository.By;
import jakarta.data.repository.Delete;
import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;
import jakarta.data.repository.Update;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.metadata.ArchiveField;
import github.luckygc.am.module.archive.metadata.ArchiveFieldScope;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveFieldDataRepository {

    @Find
    Optional<ArchiveField> findById(@By(By.ID) @Nonnull Long id);

    @Insert
    ArchiveField insert(@Nonnull ArchiveField entity);

    @Update
    ArchiveField update(@Nonnull ArchiveField entity);

    @Delete
    void delete(@Nonnull ArchiveField entity);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("sortOrder")
    @OrderBy("id")
    List<ArchiveField> list(@Nonnull Long categoryId);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("sortOrder")
    @OrderBy("id")
    List<ArchiveField> list(
            @Nonnull Long categoryId, @Nonnull ArchiveLevel archiveLevel, boolean enabled);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("sortOrder")
    @OrderBy("id")
    List<ArchiveField> list(
            @Nonnull Long categoryId,
            @Nonnull ArchiveLevel archiveLevel,
            @Nonnull ArchiveFieldScope fieldScope,
            boolean enabled);
}
