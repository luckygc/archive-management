package github.luckygc.am.module.archive.physical.repository;

import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.data.repository.By;
import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;
import jakarta.data.repository.Update;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.archive.physical.ArchivePhysicalTransferItem;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchivePhysicalTransferItemDataRepository {

    @Transactional(readOnly = true)
    @Find
    @OrderBy("id")
    List<ArchivePhysicalTransferItem> findByTransferId(@By("transferId") @Nonnull Long transferId);

    @Insert
    ArchivePhysicalTransferItem insert(@Nonnull ArchivePhysicalTransferItem entity);

    @Update
    ArchivePhysicalTransferItem update(@Nonnull ArchivePhysicalTransferItem entity);
}
