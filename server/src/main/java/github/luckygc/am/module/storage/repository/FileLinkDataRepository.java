package github.luckygc.am.module.storage.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import jakarta.annotation.Nonnull;
import jakarta.data.repository.Find;
import jakarta.data.repository.Repository;

import org.hibernate.annotations.processing.HQL;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.repository.DataRepository;
import github.luckygc.am.module.storage.FileLink;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface FileLinkDataRepository extends DataRepository<FileLink, Long> {

    @Transactional(readOnly = true)
    @Find
    Optional<FileLink> findByCode(@Nonnull String code);

    @HQL("delete from FileLink where expiresAt <= ?1")
    int deleteExpired(@Nonnull LocalDateTime expiresAt);
}
