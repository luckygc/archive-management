package github.luckygc.am.module.authorization.repository;

import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.data.repository.Insert;
import jakarta.data.repository.Repository;

import org.hibernate.annotations.processing.HQL;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.authorization.AuthorizationServiceAccountRoleRelation;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface AuthorizationServiceAccountRoleRelationDataRepository {

    @Insert
    AuthorizationServiceAccountRoleRelation insert(
            @Nonnull AuthorizationServiceAccountRoleRelation entity);

    @Transactional(readOnly = true)
    @HQL("from AuthorizationServiceAccountRoleRelation where serviceAccountId = ?1")
    List<AuthorizationServiceAccountRoleRelation> findByServiceAccountId(
            @Nonnull Long serviceAccountId);

    @Transactional(readOnly = true)
    @HQL("from AuthorizationServiceAccountRoleRelation where serviceAccountId in ?1")
    List<AuthorizationServiceAccountRoleRelation> findByServiceAccountIdIn(
            @Nonnull List<Long> serviceAccountIds);

    @HQL("delete from AuthorizationServiceAccountRoleRelation where serviceAccountId = ?1")
    void deleteByServiceAccountId(@Nonnull Long serviceAccountId);
}
