package github.luckygc.am.module.authorization.repository;

import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.data.repository.Find;
import jakarta.data.repository.Repository;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.repository.DataRepository;
import github.luckygc.am.module.authorization.AuthorizationUserRoleRelation;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface AuthorizationUserRoleRelationDataRepository
        extends DataRepository<AuthorizationUserRoleRelation, Long> {

    @Transactional(readOnly = true)
    @Find
    List<AuthorizationUserRoleRelation> findByUserId(@Nonnull Long userId);
}
