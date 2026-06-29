package github.luckygc.am.module.authentication.repository;

import jakarta.data.Order;
import jakarta.data.page.CursoredPage;
import jakarta.data.page.PageRequest;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Find;
import jakarta.data.repository.Repository;
import jakarta.data.restrict.Restriction;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface SpringSessionRecordDataRepository
        extends CrudRepository<SpringSessionRecord, String> {

    @Transactional(readOnly = true)
    @Find
    CursoredPage<SpringSessionRecord> find(
            Restriction<SpringSessionRecord> restriction,
            PageRequest pageRequest,
            Order<SpringSessionRecord> order);
}
