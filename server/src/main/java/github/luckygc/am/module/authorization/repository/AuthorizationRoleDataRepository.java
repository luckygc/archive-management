package github.luckygc.am.module.authorization.repository;

import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.data.page.CursoredPage;
import jakarta.data.page.PageRequest;
import jakarta.data.repository.Find;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;
import jakarta.data.restrict.Restriction;

import org.hibernate.annotations.processing.HQL;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.repository.DataRepository;
import github.luckygc.am.module.authorization.AuthorizationRole;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface AuthorizationRoleDataRepository extends DataRepository<AuthorizationRole, Long> {

    @Nullable @Transactional(readOnly = true)
    @Find
    AuthorizationRole findOptionalByRoleName(@Nonnull String roleName);

    @Transactional(readOnly = true)
    @Find
    @OrderBy(value = "id", descending = true)
    CursoredPage<AuthorizationRole> filterBy(
            Restriction<AuthorizationRole> restriction, PageRequest pageRequest);

    @Transactional(readOnly = true)
    @HQL("from AuthorizationRole where id in ?1")
    List<AuthorizationRole> findByIdIn(@Nonnull List<Long> ids);
}
