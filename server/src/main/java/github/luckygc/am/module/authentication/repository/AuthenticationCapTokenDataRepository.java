package github.luckygc.am.module.authentication.repository;

import java.time.LocalDateTime;

import jakarta.annotation.Nonnull;
import jakarta.data.constraint.GreaterThan;
import jakarta.data.constraint.LessThan;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Delete;
import jakarta.data.repository.Is;
import jakarta.data.repository.Repository;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.authentication.AuthenticationCapToken;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface AuthenticationCapTokenDataRepository
        extends CrudRepository<AuthenticationCapToken, String> {

    @Delete
    int consume(@Nonnull String tokenKey, @Nonnull @Is(GreaterThan.class) LocalDateTime expiresAt);

    @Delete
    int deleteExpired(@Nonnull @Is(LessThan.class) LocalDateTime expiresAt);
}
