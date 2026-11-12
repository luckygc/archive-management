package github.luckygc.am.module.authentication.repository;

import java.util.List;
import java.util.Optional;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.data.page.CursoredPage;
import jakarta.data.page.PageRequest;
import jakarta.data.repository.By;
import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;
import jakarta.data.repository.Update;

import org.hibernate.annotations.processing.HQL;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.authentication.AccessToken;
import github.luckygc.am.module.authentication.AccessTokenType;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface AccessTokenDataRepository {

    @Find
    Optional<AccessToken> findById(@By(By.ID) @Nonnull Long id);

    @Insert
    AccessToken insert(@Nonnull AccessToken entity);

    @Update
    AccessToken update(@Nonnull AccessToken entity);

    @Nullable @Transactional(readOnly = true)
    @Find
    AccessToken findByTokenPrefix(@Nonnull String tokenPrefix);

    @Transactional(readOnly = true)
    @Find
    @OrderBy(value = "id", descending = true)
    CursoredPage<AccessToken> findByServiceAccountId(
            @Nonnull Long serviceAccountId, PageRequest pageRequest);

    @Transactional(readOnly = true)
    @Find
    @OrderBy(value = "id", descending = true)
    CursoredPage<AccessToken> findByUserIdAndTokenType(
            @Nonnull Long userId, @Nonnull AccessTokenType tokenType, PageRequest pageRequest);

    @Transactional(readOnly = true)
    @HQL("from AccessToken where serviceAccountId = ?1 and revokedAt is null")
    List<AccessToken> findActiveByServiceAccountId(@Nonnull Long serviceAccountId);
}
