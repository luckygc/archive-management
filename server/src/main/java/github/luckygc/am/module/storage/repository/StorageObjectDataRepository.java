package github.luckygc.am.module.storage.repository;

import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Repository;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface StorageObjectDataRepository extends CrudRepository<StorageObject, Long> {}
