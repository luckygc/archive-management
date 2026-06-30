package github.luckygc.am.module.authorization.repository;

import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Delete;
import jakarta.data.repository.Find;
import jakarta.data.repository.Repository;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.authorization.AuthorizationRolePermissionRelation;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface AuthorizationRolePermissionRelationDataRepository
        extends CrudRepository<AuthorizationRolePermissionRelation, Long> {

    @Transactional(readOnly = true)
    @Find
    List<AuthorizationRolePermissionRelation> findByRoleId(@Nonnull Long roleId);

    @Delete
    void deleteByRoleId(@Nonnull Long roleId);
}
