package github.luckygc.am.module.auth;

import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.data.repository.BasicRepository;
import jakarta.data.repository.Find;
import jakarta.data.repository.Repository;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface AuthUserRoleRelationDataRepository
        extends BasicRepository<AuthUserRoleRelation, Long> {

    @Transactional(readOnly = true)
    @Find
    List<AuthUserRoleRelation> findByUserId(@Nonnull Long userId);
}
