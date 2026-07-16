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

import org.hibernate.annotations.processing.HQL;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.archive.ontology.ArchiveOntologyRelationType;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveOntologyRelationTypeDataRepository {

    @Find
    Optional<ArchiveOntologyRelationType> findById(@By(By.ID) @Nonnull Long id);

    @Insert
    ArchiveOntologyRelationType insert(@Nonnull ArchiveOntologyRelationType entity);

    @Update
    ArchiveOntologyRelationType update(@Nonnull ArchiveOntologyRelationType entity);

    @Delete
    void delete(@Nonnull ArchiveOntologyRelationType entity);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("relationCode")
    @OrderBy("id")
    List<ArchiveOntologyRelationType> list();

    @Nullable @Transactional(readOnly = true)
    @Find
    ArchiveOntologyRelationType findByRelationCode(@Nonnull String relationCode);

    @Transactional(readOnly = true)
    @HQL(
            """
            select count(relation)
            from ArchiveOntologyRelationType relation
            where relation.sourceObjectTypeId = ?1
               or relation.targetObjectTypeId = ?1
            """)
    long countByObjectTypeId(@Nonnull Long objectTypeId);
}
