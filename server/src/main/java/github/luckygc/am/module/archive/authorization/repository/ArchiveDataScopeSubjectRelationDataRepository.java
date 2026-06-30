package github.luckygc.am.module.archive.authorization.repository;

import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Delete;
import jakarta.data.repository.Find;
import jakarta.data.repository.Repository;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.archive.authorization.ArchiveDataScopeSubjectRelation;
import github.luckygc.am.module.archive.authorization.ArchiveDataScopeSubjectType;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveDataScopeSubjectRelationDataRepository
        extends CrudRepository<ArchiveDataScopeSubjectRelation, Long> {

    @Transactional(readOnly = true)
    @Find
    List<ArchiveDataScopeSubjectRelation> findBySubjectTypeAndSubjectId(
            @Nonnull ArchiveDataScopeSubjectType subjectType, @Nonnull Long subjectId);

    @Delete
    void deleteBySubjectTypeAndSubjectId(
            @Nonnull ArchiveDataScopeSubjectType subjectType, @Nonnull Long subjectId);
}
