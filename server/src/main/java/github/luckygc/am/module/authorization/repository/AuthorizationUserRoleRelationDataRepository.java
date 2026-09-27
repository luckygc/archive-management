package github.luckygc.am.module.authorization.repository;

import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.data.repository.Delete;
import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.Repository;

import org.hibernate.annotations.processing.HQL;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.authorization.AuthorizationUserRoleRelation;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface AuthorizationUserRoleRelationDataRepository {

    @Insert
    AuthorizationUserRoleRelation insert(@Nonnull AuthorizationUserRoleRelation entity);

    @Delete
    void delete(@Nonnull AuthorizationUserRoleRelation entity);

    @Transactional(readOnly = true)
    @Find
    List<AuthorizationUserRoleRelation> findByUserId(@Nonnull Long userId);

    @Transactional(readOnly = true)
    @Find
    List<AuthorizationUserRoleRelation> findByRoleId(@Nonnull Long roleId);

    @HQL("delete from AuthorizationUserRoleRelation where userId = ?1")
    void deleteByUserId(@Nonnull Long userId);
}
