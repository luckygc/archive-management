package github.luckygc.am.module.archive.ontology.repository;

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

import org.hibernate.annotations.processing.HQL;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.archive.ontology.ArchiveOntologyAttributeMapping;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveOntologyAttributeMappingDataRepository {

    @Find
    Optional<ArchiveOntologyAttributeMapping> findById(@By(By.ID) @Nonnull Long id);

    @Insert
    ArchiveOntologyAttributeMapping insert(@Nonnull ArchiveOntologyAttributeMapping entity);

    @Update
    ArchiveOntologyAttributeMapping update(@Nonnull ArchiveOntologyAttributeMapping entity);

    @Delete
    void delete(@Nonnull ArchiveOntologyAttributeMapping entity);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("id")
    List<ArchiveOntologyAttributeMapping> list();

    @Transactional(readOnly = true)
    @Find
    @OrderBy("id")
    List<ArchiveOntologyAttributeMapping> findByAttributeTypeId(@Nonnull Long attributeTypeId);

    @Transactional(readOnly = true)
    @HQL(
            """
            select count(mapping)
            from ArchiveOntologyAttributeMapping mapping
            where mapping.attributeTypeId = ?1
            """)
    long countByAttributeTypeId(@Nonnull Long attributeTypeId);
}
