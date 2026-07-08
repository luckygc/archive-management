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
import github.luckygc.am.module.archive.ontology.ArchiveOntologyEventType;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveOntologyEventTypeDataRepository
        extends DataRepository<ArchiveOntologyEventType, Long> {

    @Transactional(readOnly = true)
    @Find
    @OrderBy("eventCode")
    @OrderBy("id")
    List<ArchiveOntologyEventType> list();

    @Nullable @Transactional(readOnly = true)
    @Find
    ArchiveOntologyEventType findByEventCode(@Nonnull String eventCode);

    @Transactional(readOnly = true)
    @HQL(
            """
            select count(eventType)
            from ArchiveOntologyEventType eventType
            where eventType.objectTypeId = ?1
            """)
    long countByObjectTypeId(@Nonnull Long objectTypeId);
}
