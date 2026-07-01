package github.luckygc.am.module.authorization.repository;

import java.util.List;
import java.util.Optional;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.data.repository.By;
import jakarta.data.repository.Find;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.repository.DataRepository;
import github.luckygc.am.module.authorization.AuthorizationPermission;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface AuthorizationPermissionDataRepository
        extends DataRepository<AuthorizationPermission, Long> {

    @Transactional(readOnly = true)
    @Find
    Optional<AuthorizationPermission> findById(@By(By.ID) @Nonnull Long id);

    @Nullable @Transactional(readOnly = true)
    @Find
    AuthorizationPermission findByPermissionCode(@Nonnull String permissionCode);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("moduleCode")
    @OrderBy("permissionCode")
    List<AuthorizationPermission> list(boolean enabled);
}
