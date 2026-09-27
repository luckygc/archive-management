package github.luckygc.am.module.intake.repository;

import java.util.Optional;

import jakarta.annotation.Nonnull;
import jakarta.data.Order;
import jakarta.data.page.CursoredPage;
import jakarta.data.page.PageRequest;
import jakarta.data.repository.By;
import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.Repository;
import jakarta.data.repository.Update;
import jakarta.data.restrict.Restriction;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.intake.ArchiveIntakePackage;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveIntakePackageDataRepository {

    @Find
    Optional<ArchiveIntakePackage> findById(@By(By.ID) @Nonnull Long id);

    @Transactional(readOnly = true)
    @Find
    CursoredPage<ArchiveIntakePackage> find(
            Restriction<ArchiveIntakePackage> restriction,
            PageRequest pageRequest,
            Order<ArchiveIntakePackage> order);

    @Insert
    ArchiveIntakePackage insert(@Nonnull ArchiveIntakePackage entity);

    @Update
    ArchiveIntakePackage update(@Nonnull ArchiveIntakePackage entity);
}
