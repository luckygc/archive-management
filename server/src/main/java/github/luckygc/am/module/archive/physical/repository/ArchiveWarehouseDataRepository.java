package github.luckygc.am.module.archive.physical.repository;

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

import github.luckygc.am.module.archive.physical.ArchiveWarehouse;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveWarehouseDataRepository {

    @Find
    Optional<ArchiveWarehouse> findById(@By(By.ID) @Nonnull Long id);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("sortOrder")
    @OrderBy("id")
    List<ArchiveWarehouse> list();

    @Transactional(readOnly = true)
    @Find
    @OrderBy("sortOrder")
    @OrderBy("id")
    List<ArchiveWarehouse> list(boolean enabled);

    @Insert
    ArchiveWarehouse insert(@Nonnull ArchiveWarehouse entity);

    @Update
    ArchiveWarehouse update(@Nonnull ArchiveWarehouse entity);

    @Delete
    void delete(@Nonnull ArchiveWarehouse entity);
}
