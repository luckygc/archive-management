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

import github.luckygc.am.module.archive.metadata.ArchiveFonds;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveFondsDataRepository {

    @Find
    Optional<ArchiveFonds> findById(@By(By.ID) @Nonnull Long id);

    @Insert
    ArchiveFonds insert(@Nonnull ArchiveFonds entity);

    @Update
    ArchiveFonds update(@Nonnull ArchiveFonds entity);

    @Delete
    void delete(@Nonnull ArchiveFonds entity);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("sortOrder")
    @OrderBy("id")
    List<ArchiveFonds> list();

    @Transactional(readOnly = true)
    @Find
    @OrderBy("sortOrder")
    @OrderBy("id")
    List<ArchiveFonds> list(boolean enabled);

    @Transactional(readOnly = true)
    @Find
    Optional<ArchiveFonds> find(@Nonnull String fondsCode);
}
