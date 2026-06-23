package github.luckygc.am.module.archive.metadata;

import java.util.List;
import java.util.Optional;

import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Find;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.archive.ArchiveLevel;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveFieldDataRepository extends CrudRepository<ArchiveField, Long> {

    @Transactional(readOnly = true)
    @Find
    @OrderBy("sortOrder")
    @OrderBy("id")
    List<ArchiveField> list(Long categoryId, boolean deletedFlag);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("sortOrder")
    @OrderBy("id")
    List<ArchiveField> list(
            Long categoryId, ArchiveLevel archiveLevel, boolean deletedFlag, boolean enabled);

    @Transactional(readOnly = true)
    @Find
    Optional<ArchiveField> find(Long id, boolean deletedFlag);
}
