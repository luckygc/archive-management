package github.luckygc.am.module.authentication.repository;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.data.page.CursoredPage;
import jakarta.data.page.PageRequest;
import jakarta.data.repository.Find;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;
import jakarta.data.restrict.Restriction;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.repository.DataRepository;
import github.luckygc.am.module.authentication.AuthenticationUser;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface AuthenticationUserDataRepository extends DataRepository<AuthenticationUser, Long> {

    @Nullable @Transactional(readOnly = true)
    @Find
    AuthenticationUser findOptionalByUsername(@Nonnull String username);

    @Transactional(readOnly = true)
    @Find
    @OrderBy(value = "id", descending = true)
    CursoredPage<AuthenticationUser> filterBy(
            Restriction<AuthenticationUser> restriction, PageRequest pageRequest);
}
