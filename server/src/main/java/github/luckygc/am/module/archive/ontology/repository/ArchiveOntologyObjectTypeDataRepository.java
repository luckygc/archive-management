package github.luckygc.am.module.archive.ontology.repository;

import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.data.repository.Find;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.repository.DataRepository;
import github.luckygc.am.module.archive.ontology.ArchiveOntologyObjectType;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveOntologyObjectTypeDataRepository
        extends DataRepository<ArchiveOntologyObjectType, Long> {

    @Transactional(readOnly = true)
    @Find
    @OrderBy("typeCode")
    @OrderBy("id")
    List<ArchiveOntologyObjectType> list();

    @Transactional(readOnly = true)
    @Find
    @OrderBy("typeCode")
    @OrderBy("id")
    List<ArchiveOntologyObjectType> list(boolean enabled);

    @Nullable @Transactional(readOnly = true)
    @Find
    ArchiveOntologyObjectType findByTypeCode(@Nonnull String typeCode);
}
