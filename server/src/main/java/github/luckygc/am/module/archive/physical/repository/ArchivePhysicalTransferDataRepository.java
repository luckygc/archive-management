package github.luckygc.am.module.archive.physical.repository;

import java.util.Optional;

import jakarta.annotation.Nonnull;
import jakarta.data.repository.By;
import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.Repository;
import jakarta.data.repository.Update;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.archive.physical.ArchivePhysicalTransfer;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchivePhysicalTransferDataRepository {

    @Find
    Optional<ArchivePhysicalTransfer> findById(@By(By.ID) @Nonnull Long id);

    @Transactional(readOnly = true)
    @Find
    Optional<ArchivePhysicalTransfer> findByTransferNo(
            @By("transferNo") @Nonnull String transferNo);

    @Insert
    ArchivePhysicalTransfer insert(@Nonnull ArchivePhysicalTransfer entity);

    @Update
    ArchivePhysicalTransfer update(@Nonnull ArchivePhysicalTransfer entity);
}
