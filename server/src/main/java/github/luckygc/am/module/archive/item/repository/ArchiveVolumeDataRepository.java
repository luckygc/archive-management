package github.luckygc.am.module.archive.item.repository;

import jakarta.data.Order;
import jakarta.data.page.CursoredPage;
import jakarta.data.page.PageRequest;
import jakarta.data.repository.Find;
import jakarta.data.repository.Repository;
import jakarta.data.restrict.Restriction;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.repository.DataRepository;
import github.luckygc.am.module.archive.item.ArchiveVolume;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveVolumeDataRepository extends DataRepository<ArchiveVolume, Long> {

    @Transactional(readOnly = true)
    @Find
    CursoredPage<ArchiveVolume> find(
            Restriction<ArchiveVolume> restriction,
            PageRequest pageRequest,
            Order<ArchiveVolume> order);
}
