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
import github.luckygc.am.module.archive.metadata.ArchiveClassificationScheme;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveClassificationSchemeDataRepository
        extends DataRepository<ArchiveClassificationScheme, Long> {

    @Transactional(readOnly = true)
    @Find
    @OrderBy("sortOrder")
    @OrderBy("id")
    List<ArchiveClassificationScheme> list();

    @Transactional(readOnly = true)
    @Find
    @OrderBy("sortOrder")
    @OrderBy("id")
    List<ArchiveClassificationScheme> list(boolean enabled);

    @Transactional(readOnly = true)
    @Find
    @Nullable ArchiveClassificationScheme findBySchemeCode(@Nonnull String schemeCode);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("sortOrder")
    @OrderBy("id")
    List<ArchiveClassificationScheme> findByDefaultFlag(boolean defaultFlag);
}
