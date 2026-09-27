package github.luckygc.am.module.archive.physical.repository;

import java.util.List;
import java.util.Optional;

import jakarta.annotation.Nonnull;
import jakarta.data.repository.By;
import jakarta.data.repository.Delete;
import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.Repository;
import jakarta.data.repository.Update;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.archive.physical.ArchivePhysicalObject;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchivePhysicalObjectDataRepository {

    @Find
    Optional<ArchivePhysicalObject> findById(@By(By.ID) @Nonnull Long id);

    @Transactional(readOnly = true)
    @Find
    Optional<ArchivePhysicalObject> findByArchiveItemId(
            @By("archiveItemId") @Nonnull Long archiveItemId);

    @Transactional(readOnly = true)
    @Find
    Optional<ArchivePhysicalObject> findByArchiveVolumeId(
            @By("archiveVolumeId") @Nonnull Long archiveVolumeId);

    @Transactional(readOnly = true)
    @Find
    List<ArchivePhysicalObject> findByCurrentLocationId(
            @By("currentLocationId") @Nonnull Long currentLocationId);

    @Insert
    ArchivePhysicalObject insert(@Nonnull ArchivePhysicalObject entity);

    @Update
    ArchivePhysicalObject update(@Nonnull ArchivePhysicalObject entity);

    @Delete
    void delete(@Nonnull ArchivePhysicalObject entity);
}
