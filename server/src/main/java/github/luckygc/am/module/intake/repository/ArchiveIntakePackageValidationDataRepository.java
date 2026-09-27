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

import github.luckygc.am.module.intake.ArchiveIntakePackageValidation;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveIntakePackageValidationDataRepository {

    @Transactional(readOnly = true)
    @Find
    @OrderBy("id")
    List<ArchiveIntakePackageValidation> findByIntakePackageId(
            @By("intakePackageId") @Nonnull Long intakePackageId);

    @Insert
    List<ArchiveIntakePackageValidation> insertAll(
            @Nonnull List<ArchiveIntakePackageValidation> entities);
}
