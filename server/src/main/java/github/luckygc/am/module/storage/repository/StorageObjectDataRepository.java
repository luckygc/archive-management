package github.luckygc.am.module.storage.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.annotation.Nonnull;
import jakarta.data.Limit;
import jakarta.data.repository.By;
import jakarta.data.repository.Delete;
import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.Repository;

import org.hibernate.annotations.processing.HQL;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.storage.StorageObject;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface StorageObjectDataRepository {

    @Find
    Optional<StorageObject> findById(@By(By.ID) @Nonnull Long id);

    @Insert
    StorageObject insert(@Nonnull StorageObject entity);

    @Delete
    void delete(@Nonnull StorageObject entity);

    @Transactional(readOnly = true)
    @HQL(
            "from StorageObject where expiresAt is not null and expiresAt <= ?1 order by expiresAt, id")
    List<StorageObject> findExpired(@Nonnull LocalDateTime expiresAt, Limit limit);
}
