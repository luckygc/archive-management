package github.luckygc.am.module.archive.metadata;

import jakarta.data.repository.BasicRepository;
import jakarta.data.repository.Repository;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveCategoryDataRepository extends BasicRepository<ArchiveCategory, Long> {}
