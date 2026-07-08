package github.luckygc.am.module.archive.ontology.repository;

import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.data.repository.Find;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;

import org.hibernate.annotations.processing.HQL;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.repository.DataRepository;
import github.luckygc.am.module.archive.ontology.ArchiveOntologyAttributeMapping;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveOntologyAttributeMappingDataRepository
        extends DataRepository<ArchiveOntologyAttributeMapping, Long> {

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
