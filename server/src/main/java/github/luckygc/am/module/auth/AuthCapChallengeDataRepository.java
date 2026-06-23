package github.luckygc.am.module.auth;

import java.time.LocalDateTime;

import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Repository;

import org.hibernate.annotations.processing.HQL;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface AuthCapChallengeDataRepository extends CrudRepository<AuthCapChallenge, String> {

    @HQL("delete from AuthCapChallenge where expiresAt < ?1")
    int deleteExpired(LocalDateTime now);
}
