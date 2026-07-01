package github.luckygc.am.module.archive.authorization.repository;

import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.data.repository.Find;
import jakarta.data.repository.Repository;

import org.hibernate.annotations.processing.HQL;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.repository.DataRepository;
import github.luckygc.am.module.archive.authorization.ArchiveDataScopeSubjectRelation;
import github.luckygc.am.module.archive.authorization.ArchiveDataScopeSubjectType;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveDataScopeSubjectRelationDataRepository
        extends DataRepository<ArchiveDataScopeSubjectRelation, Long> {

    @Transactional(readOnly = true)
    @Find
    List<ArchiveDataScopeSubjectRelation> findBySubjectTypeAndSubjectId(
            @Nonnull ArchiveDataScopeSubjectType subjectType, @Nonnull Long subjectId);

    @HQL("delete from ArchiveDataScopeSubjectRelation where subjectType = ?1 and subjectId = ?2")
    void deleteBySubjectTypeAndSubjectId(
            @Nonnull ArchiveDataScopeSubjectType subjectType, @Nonnull Long subjectId);
}
