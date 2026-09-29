package github.luckygc.am.module.archive.metadata.repository;

import jakarta.annotation.Nonnull;
import jakarta.data.page.CursoredPage;
import jakarta.data.page.PageRequest;
import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.archive.metadata.ArchiveFondsEvent;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveFondsEventDataRepository {

    @Insert
    ArchiveFondsEvent insert(@Nonnull ArchiveFondsEvent entity);

    @Transactional(readOnly = true)
    @Find
    @OrderBy(value = "effectiveAt", descending = true)
    @OrderBy(value = "id", descending = true)
    CursoredPage<ArchiveFondsEvent> findByFondsCode(
            @Nonnull String fondsCode, PageRequest pageRequest);
}
