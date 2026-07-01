package github.luckygc.am.module.authentication.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import jakarta.annotation.Nonnull;
import jakarta.data.repository.By;
import jakarta.data.repository.Find;
import jakarta.data.repository.Repository;

import org.hibernate.annotations.processing.HQL;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.repository.DataRepository;
import github.luckygc.am.module.authentication.AuthenticationCapChallenge;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface AuthenticationCapChallengeDataRepository
        extends DataRepository<AuthenticationCapChallenge, String> {

    @Transactional(readOnly = true)
    @Find
    Optional<AuthenticationCapChallenge> findById(@By(By.ID) @Nonnull String token);

    @HQL("delete from AuthenticationCapChallenge where token = ?1")
    void deleteById(@Nonnull String token);

    @HQL("delete from AuthenticationCapChallenge where expiresAt < ?1")
    int deleteExpired(@Nonnull LocalDateTime expiresAt);
}
