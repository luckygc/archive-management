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

import github.luckygc.am.module.authentication.AuthenticationTotpCredential;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface AuthenticationTotpCredentialDataRepository {

    @Find
    Optional<AuthenticationTotpCredential> findById(@By(By.ID) @Nonnull Long userId);

    @Insert
    AuthenticationTotpCredential insert(@Nonnull AuthenticationTotpCredential entity);

    @HQL(
            "update AuthenticationTotpCredential "
                    + "set lastAcceptedStep = ?2, version = version + 1, updatedAt = ?3 "
                    + "where userId = ?1 and lastAcceptedStep < ?2")
    int advanceAcceptedStep(
            @Nonnull Long userId, long acceptedStep, @Nonnull LocalDateTime updatedAt);

    @HQL("delete from AuthenticationTotpCredential where userId = ?1")
    int deleteByUserId(@Nonnull Long userId);
}
