package github.luckygc.am.module.archive.rule.repository;

import jakarta.data.repository.Repository;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.repository.DataRepository;
import github.luckygc.am.module.archive.rule.ArchiveRuleTrace;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveRuleTraceDataRepository extends DataRepository<ArchiveRuleTrace, Long> {}
