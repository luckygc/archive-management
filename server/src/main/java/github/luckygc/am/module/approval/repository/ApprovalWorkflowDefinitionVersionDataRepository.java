package github.luckygc.am.module.approval.repository;

import java.util.List;
import java.util.Optional;

import jakarta.annotation.Nonnull;
import jakarta.data.page.CursoredPage;
import jakarta.data.page.PageRequest;
import jakarta.data.repository.By;
import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.approval.ApprovalWorkflowDefinitionVersion;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ApprovalWorkflowDefinitionVersionDataRepository {

    @Find
    Optional<ApprovalWorkflowDefinitionVersion> findById(@By(By.ID) @Nonnull Long id);

    @Transactional(readOnly = true)
    @Find
    @OrderBy(value = "versionNumber", descending = true)
    @OrderBy(value = "id", descending = true)
    List<ApprovalWorkflowDefinitionVersion> findByDefinitionId(@Nonnull Long definitionId);

    @Transactional(readOnly = true)
    @Find
    @OrderBy(value = "versionNumber", descending = true)
    @OrderBy(value = "id", descending = true)
    CursoredPage<ApprovalWorkflowDefinitionVersion> pageByDefinitionId(
            @Nonnull Long definitionId, PageRequest pageRequest);

    @Insert
    ApprovalWorkflowDefinitionVersion insert(@Nonnull ApprovalWorkflowDefinitionVersion entity);
}
