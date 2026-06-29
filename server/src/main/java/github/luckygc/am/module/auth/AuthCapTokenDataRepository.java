package github.luckygc.am.module.auth;

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

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface AuthCapTokenDataRepository extends CrudRepository<AuthCapToken, String> {

    @Delete
    int consume(@Nonnull String tokenKey, @Nonnull @Is(GreaterThan.class) LocalDateTime expiresAt);

    @Delete
    int deleteExpired(@Nonnull @Is(LessThan.class) LocalDateTime expiresAt);
}
