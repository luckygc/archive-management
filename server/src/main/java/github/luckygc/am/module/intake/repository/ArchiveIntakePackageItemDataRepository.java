package github.luckygc.am.module.intake.repository;

import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.data.repository.By;
import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.intake.ArchiveIntakePackageItem;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveIntakePackageItemDataRepository {

    @Transactional(readOnly = true)
    @Find
    @OrderBy("itemOrder")
    @OrderBy("id")
    List<ArchiveIntakePackageItem> findByIntakePackageId(
            @By("intakePackageId") @Nonnull Long intakePackageId);

    @Insert
    ArchiveIntakePackageItem insert(@Nonnull ArchiveIntakePackageItem entity);
}
