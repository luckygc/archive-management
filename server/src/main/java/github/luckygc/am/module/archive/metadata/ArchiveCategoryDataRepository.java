package github.luckygc.am.module.archive.metadata;

import java.util.List;

import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Find;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveCategoryDataRepository extends CrudRepository<ArchiveCategory, Long> {

    @Transactional(readOnly = true)
    @Find
    @OrderBy("parentId")
    @OrderBy("sortOrder")
    @OrderBy("id")
    List<ArchiveCategory> list();

    @Transactional(readOnly = true)
    @Find
    @OrderBy("parentId")
    @OrderBy("sortOrder")
    @OrderBy("id")
    List<ArchiveCategory> list(boolean enabled);
}
