package github.luckygc.am.module.storage.repository;

import java.util.Optional;

import jakarta.annotation.Nonnull;
import jakarta.data.repository.By;
import jakarta.data.repository.Find;
import jakarta.data.repository.Repository;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.repository.DataRepository;
import github.luckygc.am.module.storage.StorageObject;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface StorageObjectDataRepository extends DataRepository<StorageObject, Long> {

    @Transactional(readOnly = true)
    @Find
    Optional<StorageObject> findById(@By(By.ID) @Nonnull Long id);
}
