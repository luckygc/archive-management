package github.luckygc.am.module.authentication.repository;

import jakarta.annotation.Nonnull;
import jakarta.data.page.CursoredPage;
import jakarta.data.page.PageRequest;
import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.authentication.AccessTokenCall;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface AccessTokenCallDataRepository {

    @Insert
    AccessTokenCall insert(@Nonnull AccessTokenCall entity);

    @Transactional(readOnly = true)
    @Find
    @OrderBy(value = "id", descending = true)
    CursoredPage<AccessTokenCall> findAll(PageRequest pageRequest);

    @Transactional(readOnly = true)
    @Find
    @OrderBy(value = "id", descending = true)
    CursoredPage<AccessTokenCall> findByServiceAccountId(
            @Nonnull Long serviceAccountId, PageRequest pageRequest);

    @Transactional(readOnly = true)
    @Find
    @OrderBy(value = "id", descending = true)
    CursoredPage<AccessTokenCall> findByTokenId(@Nonnull Long tokenId, PageRequest pageRequest);

    @Transactional(readOnly = true)
    @Find
    @OrderBy(value = "id", descending = true)
    CursoredPage<AccessTokenCall> findByServiceAccountIdAndTokenId(
            @Nonnull Long serviceAccountId, @Nonnull Long tokenId, PageRequest pageRequest);
}
