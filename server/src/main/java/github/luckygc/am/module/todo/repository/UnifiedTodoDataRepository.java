package github.luckygc.am.module.todo.repository;

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

import org.hibernate.annotations.processing.HQL;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.todo.UnifiedTodo;
import github.luckygc.am.module.todo.UnifiedTodoStatus;

@Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED)
@Repository
public interface UnifiedTodoDataRepository {

    @Find
    Optional<UnifiedTodo> findById(@By(By.ID) @Nonnull Long id);

    @Nullable @Transactional(readOnly = true)
    @Find
    UnifiedTodo findBySourceTypeAndSourceTaskIdAndAssigneeUserId(
            @Nonnull String sourceType, @Nonnull String sourceTaskId, @Nonnull Long assigneeUserId);

    @Transactional(readOnly = true)
    @Find
    @OrderBy("id")
    List<UnifiedTodo> findBySourceTypeAndSourceTaskId(
            @Nonnull String sourceType, @Nonnull String sourceTaskId);

    @Transactional(readOnly = true)
    @OrderBy(value = "createdAt", descending = true)
    @OrderBy(value = "id", descending = true)
    @HQL("from UnifiedTodo todo where todo.assigneeUserId = ?1 and todo.status = ?2")
    CursoredPage<UnifiedTodo> findForAssignee(
            @Nonnull Long assigneeUserId,
            @Nonnull UnifiedTodoStatus status,
            PageRequest pageRequest);

    @Insert
    UnifiedTodo insert(@Nonnull UnifiedTodo entity);

    @Update
    UnifiedTodo update(@Nonnull UnifiedTodo entity);
}
