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

import github.luckygc.am.module.approval.ApprovalWorkflowInstance;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface ApprovalWorkflowInstanceDataRepository {

    @Find
    Optional<ApprovalWorkflowInstance> findById(@By(By.ID) @Nonnull Long id);

    @Nullable @Transactional(readOnly = true)
    @Find
    ApprovalWorkflowInstance findByFlowableProcessInstanceId(
            @Nonnull String flowableProcessInstanceId);

    @Transactional(readOnly = true)
    @Find
    @OrderBy(value = "createdAt", descending = true)
    @OrderBy(value = "id", descending = true)
    List<ApprovalWorkflowInstance> findByBusinessTypeAndBusinessId(
            @Nonnull String businessType, @Nonnull String businessId);

    @Transactional(readOnly = true)
    @Find
    @OrderBy(value = "createdAt", descending = true)
    @OrderBy(value = "id", descending = true)
    CursoredPage<ApprovalWorkflowInstance> filterBy(
            Restriction<ApprovalWorkflowInstance> restriction, PageRequest pageRequest);

    @Insert
    ApprovalWorkflowInstance insert(@Nonnull ApprovalWorkflowInstance entity);

    @Update
    ApprovalWorkflowInstance update(@Nonnull ApprovalWorkflowInstance entity);
}
