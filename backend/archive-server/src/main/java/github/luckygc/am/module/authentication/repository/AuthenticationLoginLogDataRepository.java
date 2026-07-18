package github.luckygc.am.module.authentication.repository;

import jakarta.annotation.Nonnull;
import jakarta.data.page.CursoredPage;
import jakarta.data.page.PageRequest;
import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;
import jakarta.data.restrict.Restriction;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.authentication.AuthenticationLoginLog;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface AuthenticationLoginLogDataRepository {

    @Insert
    AuthenticationLoginLog insert(@Nonnull AuthenticationLoginLog entity);

    @Transactional(readOnly = true)
    @Find
    @OrderBy(value = "occurredAt", descending = true)
    @OrderBy(value = "id", descending = true)
    CursoredPage<AuthenticationLoginLog> find(
            Restriction<AuthenticationLoginLog> restriction, PageRequest pageRequest);

    @Transactional(readOnly = true)
    @Find
    @OrderBy(value = "occurredAt", descending = true)
    @OrderBy(value = "id", descending = true)
    CursoredPage<AuthenticationLoginLog> find(String eventType, PageRequest pageRequest);
}
