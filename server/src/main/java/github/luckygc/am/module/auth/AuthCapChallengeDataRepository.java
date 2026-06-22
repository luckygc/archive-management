package github.luckygc.am.module.auth;

import java.time.LocalDateTime;

import jakarta.data.repository.BasicRepository;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface AuthCapChallengeDataRepository extends BasicRepository<AuthCapChallenge, String> {

    @Query("delete from AuthCapChallenge where expiresAt < ?1")
    int deleteExpired(LocalDateTime now);
}
