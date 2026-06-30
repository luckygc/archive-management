package github.luckygc.am.module.archive.metadata.repository;

import java.util.List;

import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Find;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.archive.metadata.ArchiveRetentionPeriod;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveRetentionPeriodDataRepository
        extends CrudRepository<ArchiveRetentionPeriod, Long> {

    @Transactional(readOnly = true)
    @Find
    @OrderBy("sortOrder")
    @OrderBy("id")
    List<ArchiveRetentionPeriod> list();

    @Transactional(readOnly = true)
    @Find
    @OrderBy("sortOrder")
    @OrderBy("id")
    List<ArchiveRetentionPeriod> list(boolean enabled);
}
