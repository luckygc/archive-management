package github.luckygc.am.module.authentication.repository;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.data.repository.Find;
import jakarta.data.repository.Repository;

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
}
