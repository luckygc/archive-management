package github.luckygc.am.module.archive.governance.repository;

import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.data.repository.Delete;
import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;

import org.hibernate.annotations.processing.HQL;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.archive.governance.ArchiveGovernanceBinding;
import github.luckygc.am.module.archive.governance.ArchiveGovernanceBindingType;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ArchiveGovernanceBindingDataRepository {

    @Insert
    List<ArchiveGovernanceBinding> insertAll(@Nonnull List<ArchiveGovernanceBinding> entities);

    @Delete
    void deleteAll(@Nonnull List<ArchiveGovernanceBinding> entities);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("bindingOrder")
    @OrderBy("id")
    List<ArchiveGovernanceBinding> findBySchemeVersionId(@Nonnull Long schemeVersionId);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("bindingOrder")
    @OrderBy("id")
    List<ArchiveGovernanceBinding> findBySchemeVersionIdAndBindingType(
            @Nonnull Long schemeVersionId, @Nonnull ArchiveGovernanceBindingType bindingType);

    @Transactional(readOnly = true)
    @HQL(
            """
            select count(binding)
            from ArchiveGovernanceBinding binding, ArchiveGovernanceSchemeVersion schemeVersion
            where schemeVersion.id = binding.schemeVersionId
              and schemeVersion.status in (
                  github.luckygc.am.module.archive.governance.ArchiveGovernanceSchemeVersionStatus.PUBLISHED,
                  github.luckygc.am.module.archive.governance.ArchiveGovernanceSchemeVersionStatus.FROZEN,
                  github.luckygc.am.module.archive.governance.ArchiveGovernanceSchemeVersionStatus.RETIRED
              )
              and binding.bindingType = ?1
              and binding.targetId = ?2
            """)
    long countProtectedByBindingTypeAndTargetId(
            @Nonnull ArchiveGovernanceBindingType bindingType, @Nonnull Long targetId);
}
