package github.luckygc.am.module.authorization.repository;

import java.util.Optional;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.data.repository.By;
import jakarta.data.repository.Find;
import jakarta.data.repository.Repository;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.repository.DataRepository;
import github.luckygc.am.module.authorization.AuthorizationRole;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface AuthorizationRoleDataRepository extends DataRepository<AuthorizationRole, Long> {

    @Transactional(readOnly = true)
    @Find
    Optional<AuthorizationRole> findById(@By(By.ID) @Nonnull Long id);

    @Nullable @Transactional(readOnly = true)
    @Find
    AuthorizationRole findOptionalByRoleName(@Nonnull String roleName);
}
