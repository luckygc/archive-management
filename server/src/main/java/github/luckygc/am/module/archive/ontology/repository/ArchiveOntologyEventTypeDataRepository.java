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

import github.luckygc.am.module.archive.ontology.ArchiveOntologyEventType;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveOntologyEventTypeDataRepository {

    @Find
    Optional<ArchiveOntologyEventType> findById(@By(By.ID) @Nonnull Long id);

    @Insert
    ArchiveOntologyEventType insert(@Nonnull ArchiveOntologyEventType entity);

    @Insert
    List<ArchiveOntologyEventType> insertAll(@Nonnull List<ArchiveOntologyEventType> entities);

    @Update
    ArchiveOntologyEventType update(@Nonnull ArchiveOntologyEventType entity);

    @Delete
    void delete(@Nonnull ArchiveOntologyEventType entity);

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
