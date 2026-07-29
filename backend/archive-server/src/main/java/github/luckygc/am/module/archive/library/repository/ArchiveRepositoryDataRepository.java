package github.luckygc.am.module.archive.library.repository;

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

import github.luckygc.am.module.archive.library.ArchiveRepository;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveRepositoryDataRepository {

    @Find
    Optional<ArchiveRepository> findById(@By(By.ID) @Nonnull Long id);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("sortOrder")
    @OrderBy("id")
    List<ArchiveRepository> list();

    @Transactional(readOnly = true)
    @Find
    @OrderBy("sortOrder")
    @OrderBy("id")
    List<ArchiveRepository> list(boolean enabled);

    @Insert
    ArchiveRepository insert(@Nonnull ArchiveRepository entity);

    @Update
    ArchiveRepository update(@Nonnull ArchiveRepository entity);

    @Delete
    void delete(@Nonnull ArchiveRepository entity);
}
