package github.luckygc.am.module.auth;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.data.repository.BasicRepository;
import jakarta.data.repository.Find;
import jakarta.data.repository.Repository;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface AuthUserDataRepository extends BasicRepository<AuthUser, Long> {

    @Nullable @Transactional(readOnly = true)
    @Find
    AuthUser findOptionalByUsername(@Nonnull String username);
}
