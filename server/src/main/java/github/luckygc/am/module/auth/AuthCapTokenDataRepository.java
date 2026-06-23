package github.luckygc.am.module.auth;

import java.time.LocalDateTime;

import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Repository;

import org.hibernate.annotations.processing.HQL;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface AuthCapTokenDataRepository extends CrudRepository<AuthCapToken, String> {

    @HQL("delete from AuthCapToken where tokenKey = ?1 and expiresAt > ?2")
    int consume(String tokenKey, LocalDateTime now);

    @HQL("delete from AuthCapToken where expiresAt < ?1")
    int deleteExpired(LocalDateTime now);
}
