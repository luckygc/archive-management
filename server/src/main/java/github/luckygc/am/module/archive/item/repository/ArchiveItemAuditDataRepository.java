package github.luckygc.am.module.archive.item.repository;

import jakarta.data.page.CursoredPage;
import jakarta.data.page.PageRequest;
import jakarta.data.repository.Find;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;
import jakarta.data.restrict.Restriction;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.repository.DataRepository;
import github.luckygc.am.module.archive.item.ArchiveItemAudit;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveItemAuditDataRepository extends DataRepository<ArchiveItemAudit, Long> {

    @Transactional(readOnly = true)
    @Find
    @OrderBy(value = "operatedAt", descending = true)
    @OrderBy(value = "id", descending = true)
    CursoredPage<ArchiveItemAudit> find(
            Restriction<ArchiveItemAudit> restriction, PageRequest pageRequest);
}
