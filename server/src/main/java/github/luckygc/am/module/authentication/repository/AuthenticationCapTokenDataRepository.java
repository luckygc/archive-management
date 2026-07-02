package github.luckygc.am.module.authentication.repository;

import java.time.LocalDateTime;

import jakarta.annotation.Nonnull;
import jakarta.data.repository.Repository;

import org.hibernate.annotations.processing.HQL;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.repository.DataRepository;
import github.luckygc.am.module.authentication.AuthenticationCapToken;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface AuthenticationCapTokenDataRepository
        extends DataRepository<AuthenticationCapToken, String> {

    @HQL("delete from AuthenticationCapToken where tokenKey = ?1")
    void deleteById(@Nonnull String tokenKey);

    @HQL("delete from AuthenticationCapToken where tokenKey = ?1 and expiresAt > ?2")
    int consume(@Nonnull String tokenKey, @Nonnull LocalDateTime expiresAt);

    @HQL(
            "delete from AuthenticationCapToken where tokenKey = ?1 and usernameHash = ?2 and expiresAt > ?3")
    int consume(
            @Nonnull String tokenKey,
            @Nonnull String usernameHash,
            @Nonnull LocalDateTime expiresAt);

    @HQL("delete from AuthenticationCapToken where expiresAt < ?1")
    int deleteExpired(@Nonnull LocalDateTime expiresAt);
}
