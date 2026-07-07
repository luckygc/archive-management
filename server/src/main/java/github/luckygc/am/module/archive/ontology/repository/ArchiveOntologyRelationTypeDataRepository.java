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
import github.luckygc.am.module.archive.ontology.ArchiveOntologyRelationType;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveOntologyRelationTypeDataRepository
        extends DataRepository<ArchiveOntologyRelationType, Long> {

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
