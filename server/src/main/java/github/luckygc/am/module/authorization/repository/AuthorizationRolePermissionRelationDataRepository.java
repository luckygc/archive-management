package github.luckygc.am.module.authorization.repository;

import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.Repository;

import org.hibernate.annotations.processing.HQL;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.authorization.AuthorizationRolePermissionRelation;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface AuthorizationRolePermissionRelationDataRepository {

    @Insert
    AuthorizationRolePermissionRelation insert(@Nonnull AuthorizationRolePermissionRelation entity);

    @Transactional(readOnly = true)
    @Find
    List<AuthorizationRolePermissionRelation> findByRoleId(@Nonnull Long roleId);

    @HQL("delete from AuthorizationRolePermissionRelation where roleId = ?1")
    void deleteByRoleId(@Nonnull Long roleId);
}
