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

import github.luckygc.am.module.authentication.AuthenticationTotpEnrollment;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface AuthenticationTotpEnrollmentDataRepository {

    @Find
    Optional<AuthenticationTotpEnrollment> findById(@By(By.ID) @Nonnull String tokenKey);

    @Insert
    AuthenticationTotpEnrollment insert(@Nonnull AuthenticationTotpEnrollment entity);

    @HQL("delete from AuthenticationTotpEnrollment where userId = ?1")
    int deleteByUserId(@Nonnull Long userId);

    @HQL(
            "delete from AuthenticationTotpEnrollment "
                    + "where tokenKey = ?1 and userId = ?2 and expiresAt > ?3")
    int consume(@Nonnull String tokenKey, @Nonnull Long userId, @Nonnull LocalDateTime now);

    @HQL("delete from AuthenticationTotpEnrollment where expiresAt < ?1")
    int deleteExpired(@Nonnull LocalDateTime now);
}
