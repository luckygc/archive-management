package github.luckygc.am.module.archive.physical.repository;

import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.data.repository.By;
import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.archive.physical.ArchivePhysicalLocationHistory;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchivePhysicalLocationHistoryDataRepository {

    @Insert
    ArchivePhysicalLocationHistory insert(@Nonnull ArchivePhysicalLocationHistory entity);

    @Transactional(readOnly = true)
    @Find
    @OrderBy(value = "operatedAt", descending = true)
    @OrderBy(value = "id", descending = true)
    List<ArchivePhysicalLocationHistory> list(
            @By("physicalObjectId") @Nonnull Long physicalObjectId);
}
