package github.luckygc.am.module.archive.metadata.repository;

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

import github.luckygc.am.module.archive.metadata.ArchiveClassificationScheme;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveClassificationSchemeDataRepository {

    @Find
    Optional<ArchiveClassificationScheme> findById(@By(By.ID) @Nonnull Long id);

    @Insert
    ArchiveClassificationScheme insert(@Nonnull ArchiveClassificationScheme entity);

    @Update
    ArchiveClassificationScheme update(@Nonnull ArchiveClassificationScheme entity);

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
