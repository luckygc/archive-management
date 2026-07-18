package github.luckygc.am.module.archive.rule.repository;

import java.util.List;
import java.util.Optional;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.data.repository.By;
import jakarta.data.repository.Delete;
import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;
import jakarta.data.repository.Update;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.archive.rule.ArchiveRuntimeDefinition;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeStatus;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveRuntimeDefinitionDataRepository {

    @Find
    Optional<ArchiveRuntimeDefinition> findById(@By(By.ID) @Nonnull Long id);

    @Insert
    ArchiveRuntimeDefinition insert(@Nonnull ArchiveRuntimeDefinition entity);

    @Update
    ArchiveRuntimeDefinition update(@Nonnull ArchiveRuntimeDefinition entity);

    @Delete
    void delete(@Nonnull ArchiveRuntimeDefinition entity);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("priority")
    @OrderBy("definitionCode")
    @OrderBy("id")
    List<ArchiveRuntimeDefinition> findBySchemeVersionId(@Nonnull Long schemeVersionId);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("priority")
    @OrderBy("definitionCode")
    @OrderBy("id")
    List<ArchiveRuntimeDefinition> findBySchemeVersionIdAndStatus(
            @Nonnull Long schemeVersionId, @Nonnull ArchiveRuntimeStatus status);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("schemeVersionId")
    @OrderBy("definitionCode")
    @OrderBy("id")
    List<ArchiveRuntimeDefinition> findByStatus(@Nonnull ArchiveRuntimeStatus status);

    @Nullable @Transactional(readOnly = true)
    @Find
    ArchiveRuntimeDefinition findBySchemeVersionIdAndDefinitionCode(
            @Nonnull Long schemeVersionId, @Nonnull String definitionCode);
}
