package github.luckygc.am.module.archive.item.repository;

import java.util.Optional;

import jakarta.annotation.Nonnull;
import jakarta.data.Limit;
import jakarta.data.Order;
import jakarta.data.page.CursoredPage;
import jakarta.data.page.PageRequest;
import jakarta.data.repository.By;
import jakarta.data.repository.Find;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;
import jakarta.data.repository.Update;
import jakarta.data.restrict.Restriction;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.archive.item.ArchiveVolume;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveVolumeDataRepository {

    @Find
    Optional<ArchiveVolume> findById(@By(By.ID) @Nonnull Long id);

    @Transactional(readOnly = true)
    @Find
    CursoredPage<ArchiveVolume> find(
            Restriction<ArchiveVolume> restriction,
            PageRequest pageRequest,
            Order<ArchiveVolume> order);

    @Update
    ArchiveVolume update(@Nonnull ArchiveVolume entity);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("id")
    java.util.List<ArchiveVolume> findByRepositoryId(
            @By("repositoryId") @Nonnull Long repositoryId, Limit limit);
}
