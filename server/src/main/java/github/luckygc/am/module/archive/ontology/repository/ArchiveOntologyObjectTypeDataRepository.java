package github.luckygc.am.module.archive.ontology.repository;

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

import github.luckygc.am.module.archive.ontology.ArchiveOntologyObjectType;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveOntologyObjectTypeDataRepository {

    @Find
    Optional<ArchiveOntologyObjectType> findById(@By(By.ID) @Nonnull Long id);

    @Insert
    ArchiveOntologyObjectType insert(@Nonnull ArchiveOntologyObjectType entity);

    @Insert
    List<ArchiveOntologyObjectType> insertAll(@Nonnull List<ArchiveOntologyObjectType> entities);

    @Update
    ArchiveOntologyObjectType update(@Nonnull ArchiveOntologyObjectType entity);

    @Delete
    void delete(@Nonnull ArchiveOntologyObjectType entity);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("typeCode")
    @OrderBy("id")
    List<ArchiveOntologyObjectType> list();

    @Transactional(readOnly = true)
    @Find
    @OrderBy("typeCode")
    @OrderBy("id")
    List<ArchiveOntologyObjectType> list(boolean enabled);

    @Nullable @Transactional(readOnly = true)
    @Find
    ArchiveOntologyObjectType findByTypeCode(@Nonnull String typeCode);
}
