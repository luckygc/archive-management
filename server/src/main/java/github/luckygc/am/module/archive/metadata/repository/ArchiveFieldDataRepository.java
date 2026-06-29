package github.luckygc.am.module.archive.metadata.repository;

import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Find;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.archive.ArchiveLevel;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveFieldDataRepository extends CrudRepository<ArchiveField, Long> {

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
