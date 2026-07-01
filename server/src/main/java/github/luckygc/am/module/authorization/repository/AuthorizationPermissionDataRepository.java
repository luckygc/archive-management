package github.luckygc.am.module.authorization.repository;

import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
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

    @Nullable @Transactional(readOnly = true)
    @Find
    AuthorizationPermission findByPermissionCode(@Nonnull String permissionCode);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("moduleCode")
    @OrderBy("permissionCode")
    List<AuthorizationPermission> list(boolean enabled);
}
