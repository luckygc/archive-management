package github.luckygc.am.module.archive.metadata.repository;

import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.data.repository.Delete;
import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.archive.metadata.ArchiveFondsCategoryScope;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveFondsCategoryScopeDataRepository {

    @Insert
    List<ArchiveFondsCategoryScope> insertAll(@Nonnull List<ArchiveFondsCategoryScope> entities);

    @Delete
    void deleteAll(@Nonnull List<ArchiveFondsCategoryScope> entities);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("sortOrder")
    @OrderBy("id")
    List<ArchiveFondsCategoryScope> findByFondsCode(@Nonnull String fondsCode);
}
