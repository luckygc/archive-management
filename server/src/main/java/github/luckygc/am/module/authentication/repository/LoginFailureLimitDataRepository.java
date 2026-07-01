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
import github.luckygc.am.module.authentication.LoginFailureLimit;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface LoginFailureLimitDataRepository extends DataRepository<LoginFailureLimit, String> {

    @Transactional(readOnly = true)
    @Find
    Optional<LoginFailureLimit> findById(@By(By.ID) @Nonnull String username);

    @HQL("delete from LoginFailureLimit where username = ?1")
    void deleteById(@Nonnull String username);

    @HQL("delete from LoginFailureLimit where updatedAt < ?1")
    int deleteExpired(@Nonnull LocalDateTime cleanupAfter);
}
