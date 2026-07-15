package github.luckygc.am.module.storage.repository;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.data.Limit;
import jakarta.data.repository.Repository;

import org.hibernate.annotations.processing.HQL;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.repository.DataRepository;
import github.luckygc.am.module.storage.StorageObject;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface StorageObjectDataRepository extends DataRepository<StorageObject, Long> {

    @Transactional(readOnly = true)
    @HQL(
            "from StorageObject where expiresAt is not null and expiresAt <= ?1 order by expiresAt, id")
    List<StorageObject> findExpired(@Nonnull LocalDateTime expiresAt, Limit limit);
}
