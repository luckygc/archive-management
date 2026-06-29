package github.luckygc.am.module.archive.metadata;

import java.util.List;
import java.util.Optional;

import jakarta.annotation.Nonnull;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Find;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveFondsDataRepository extends CrudRepository<ArchiveFonds, Long> {

    @Transactional(readOnly = true)
    @Find
    @OrderBy("sortOrder")
    @OrderBy("id")
    List<ArchiveFonds> list();

    @Transactional(readOnly = true)
    @Find
    @OrderBy("sortOrder")
    @OrderBy("id")
    List<ArchiveFonds> list(boolean enabled);

    @Transactional(readOnly = true)
    @Find
    Optional<ArchiveFonds> find(@Nonnull String fondsCode);
}
