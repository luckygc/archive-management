package github.luckygc.am.module.archive.rule.repository;

import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.data.repository.Delete;
import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;
import jakarta.data.repository.Update;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.archive.rule.ArchiveRuntimeAction;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveRuntimeActionDataRepository {

    @Insert
    List<ArchiveRuntimeAction> insertAll(@Nonnull List<ArchiveRuntimeAction> entities);

    @Update
    ArchiveRuntimeAction update(@Nonnull ArchiveRuntimeAction entity);

    @Delete
    void delete(@Nonnull ArchiveRuntimeAction entity);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("actionOrder")
    @OrderBy("id")
    List<ArchiveRuntimeAction> findByDefinitionId(@Nonnull Long definitionId);
}
