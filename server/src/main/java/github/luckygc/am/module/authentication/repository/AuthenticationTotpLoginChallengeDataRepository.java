package github.luckygc.am.module.authentication.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import jakarta.annotation.Nonnull;
import jakarta.data.repository.By;
import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.Repository;

import org.hibernate.annotations.processing.HQL;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.authentication.AuthenticationTotpLoginChallenge;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface AuthenticationTotpLoginChallengeDataRepository {

    @Find
    Optional<AuthenticationTotpLoginChallenge> findById(@By(By.ID) @Nonnull String tokenKey);

    @Insert
    AuthenticationTotpLoginChallenge insert(@Nonnull AuthenticationTotpLoginChallenge entity);

    @HQL("delete from AuthenticationTotpLoginChallenge where tokenKey = ?1")
    int deleteById(@Nonnull String tokenKey);

    @HQL("delete from AuthenticationTotpLoginChallenge where userId = ?1")
    int deleteByUserId(@Nonnull Long userId);

    @HQL(
            "delete from AuthenticationTotpLoginChallenge "
                    + "where tokenKey = ?1 and expiresAt > ?2 and failedAttempts < ?3")
    int consume(@Nonnull String tokenKey, @Nonnull LocalDateTime now, int maxAttempts);

    @HQL(
            "update AuthenticationTotpLoginChallenge "
                    + "set failedAttempts = failedAttempts + 1 "
                    + "where tokenKey = ?1 and expiresAt > ?2 and failedAttempts < ?3")
    int recordFailure(@Nonnull String tokenKey, @Nonnull LocalDateTime now, int maxAttempts);

    @HQL("delete from AuthenticationTotpLoginChallenge where expiresAt < ?1")
    int deleteExpired(@Nonnull LocalDateTime now);
}
