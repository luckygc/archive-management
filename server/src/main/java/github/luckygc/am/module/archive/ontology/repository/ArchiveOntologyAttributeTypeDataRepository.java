package github.luckygc.am.module.archive.ontology.repository;

import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.data.repository.Find;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;

import org.hibernate.annotations.processing.HQL;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.repository.DataRepository;
import github.luckygc.am.module.archive.ontology.ArchiveOntologyAttributeType;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveOntologyAttributeTypeDataRepository
        extends DataRepository<ArchiveOntologyAttributeType, Long> {

    @Transactional(readOnly = true)
    @Find
    @OrderBy("attributeCode")
    @OrderBy("id")
    List<ArchiveOntologyAttributeType> list();

    @Transactional(readOnly = true)
    @Find
    @OrderBy("attributeCode")
    @OrderBy("id")
    List<ArchiveOntologyAttributeType> findByObjectTypeId(@Nonnull Long objectTypeId);

    @Nullable @Transactional(readOnly = true)
    @Find
    ArchiveOntologyAttributeType findByAttributeCode(@Nonnull String attributeCode);

    @Transactional(readOnly = true)
    @HQL(
            """
            select count(attribute)
            from ArchiveOntologyAttributeType attribute
            where attribute.objectTypeId = ?1
            """)
    long countByObjectTypeId(@Nonnull Long objectTypeId);
}
