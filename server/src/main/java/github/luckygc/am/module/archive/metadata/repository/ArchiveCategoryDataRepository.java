package github.luckygc.am.module.archive.metadata.repository;

import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.data.repository.Find;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.repository.DataRepository;
import github.luckygc.am.module.archive.metadata.ArchiveCategory;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveCategoryDataRepository extends DataRepository<ArchiveCategory, Long> {

    @Transactional(readOnly = true)
    @Find
    @Nullable ArchiveCategory findByCategoryCode(@Nonnull String categoryCode);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("parentId")
    @OrderBy("sortOrder")
    @OrderBy("id")
    List<ArchiveCategory> list();

    @Transactional(readOnly = true)
    @Find
    @OrderBy("parentId")
    @OrderBy("sortOrder")
    @OrderBy("id")
    List<ArchiveCategory> list(boolean enabled);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("parentId")
    @OrderBy("sortOrder")
    @OrderBy("id")
    List<ArchiveCategory> findBySchemeId(@Nonnull Long schemeId);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("parentId")
    @OrderBy("sortOrder")
    @OrderBy("id")
    List<ArchiveCategory> findBySchemeIdAndEnabled(@Nonnull Long schemeId, boolean enabled);
}
