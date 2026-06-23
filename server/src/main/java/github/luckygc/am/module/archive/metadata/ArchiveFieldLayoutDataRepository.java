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
public interface ArchiveFieldLayoutDataRepository extends CrudRepository<ArchiveFieldLayout, Long> {

    @Transactional(readOnly = true)
    @Find
    @OrderBy("rowOrder")
    @OrderBy("colOrder")
    @OrderBy("id")
    List<ArchiveFieldLayout> list(
            Long categoryId, ArchiveLayoutSurface surface, boolean deletedFlag);
}
