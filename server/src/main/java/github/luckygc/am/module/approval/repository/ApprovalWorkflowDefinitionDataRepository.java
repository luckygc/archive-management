package github.luckygc.am.module.approval.repository;

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
import jakarta.data.restrict.Restriction;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.approval.ApprovalWorkflowDefinition;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ApprovalWorkflowDefinitionDataRepository {

    @Find
    Optional<ApprovalWorkflowDefinition> findById(@By(By.ID) @Nonnull Long id);

    @Nullable @Transactional(readOnly = true)
    @Find
    ApprovalWorkflowDefinition findByDefinitionCode(@Nonnull String definitionCode);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("definitionName")
    @OrderBy("id")
    List<ApprovalWorkflowDefinition> list(boolean enabled);

    @Transactional(readOnly = true)
    @Find
    @OrderBy(value = "id", descending = true)
    CursoredPage<ApprovalWorkflowDefinition> filterBy(
            Restriction<ApprovalWorkflowDefinition> restriction, PageRequest pageRequest);

    @Insert
    ApprovalWorkflowDefinition insert(@Nonnull ApprovalWorkflowDefinition entity);

    @Update
    ApprovalWorkflowDefinition update(@Nonnull ApprovalWorkflowDefinition entity);
}
