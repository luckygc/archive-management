package github.luckygc.am.module.authorization.repository;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Find;
import jakarta.data.repository.Repository;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.authorization.AuthorizationRole;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface AuthorizationRoleDataRepository extends CrudRepository<AuthorizationRole, Long> {

    @Nullable @Transactional(readOnly = true)
    @Find
    AuthorizationRole findOptionalByRoleName(@Nonnull String roleName);
}
