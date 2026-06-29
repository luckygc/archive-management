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

import github.luckygc.am.module.authentication.AuthenticationLoginLog;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface AuthenticationLoginLogDataRepository
        extends CrudRepository<AuthenticationLoginLog, Long> {

    @Transactional(readOnly = true)
    @Find
    CursoredPage<AuthenticationLoginLog> find(
            Restriction<AuthenticationLoginLog> restriction,
            PageRequest pageRequest,
            Order<AuthenticationLoginLog> order);

    @Transactional(readOnly = true)
    @Find
    CursoredPage<AuthenticationLoginLog> find(
            String eventType, PageRequest pageRequest, Order<AuthenticationLoginLog> order);
}
