package github.luckygc.am.module.todo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import jakarta.data.page.PageRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import github.luckygc.am.app.ArchiveManagementApplication;
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.authentication.AuthenticationUser;
import github.luckygc.am.module.authentication.repository.AuthenticationUserDataRepository;
import github.luckygc.am.module.todo.UnifiedTodoStatus;
import github.luckygc.am.module.todo.service.UnifiedTodoService.DispatchUnifiedTodoCommand;
import github.luckygc.am.test.PostgreSqlContainerTest;

@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        classes = ArchiveManagementApplication.class,
        properties = {
            "spring.quartz.auto-startup=false",
            "spring.session.jdbc.cleanup-cron=-",
            "flowable.async-executor-activate=false",
            "flowable.check-process-definitions=false"
        })
@DisplayName("统一待办服务")
class UnifiedTodoServiceTests extends PostgreSqlContainerTest {

    @Autowired private UnifiedTodoService service;
    @Autowired private AuthenticationUserDataRepository userRepository;

    @Test
    @DisplayName("重复投递保持每名候选人一条记录")
    void dispatchShouldBeIdempotentPerAssignee() {
        List<Long> users = users(2);
        DispatchUnifiedTodoCommand command = command("task-idempotent", users);

        service.dispatch(command);
        service.dispatch(command);

        assertThat(service.listMy(false, PageRequest.ofSize(100), users.get(0)).items()).hasSize(1);
        assertThat(service.listMy(false, PageRequest.ofSize(100), users.get(1)).items()).hasSize(1);
    }

    @Test
    @DisplayName("一名候选人完成后其他候选投影被取消")
    void completeSourceShouldCompleteActorAndCancelOthers() {
        List<Long> users = users(2);
        service.dispatch(command("task-complete", users));

        service.completeSource(
                UnifiedTodoService.APPROVAL_SOURCE_TYPE, "task-complete", users.getFirst());

        assertThat(service.listMy(true, PageRequest.ofSize(100), users.getFirst()).items())
                .singleElement()
                .extracting("status")
                .isEqualTo(UnifiedTodoStatus.COMPLETED);
        assertThat(service.listMy(false, PageRequest.ofSize(100), users.get(1)).items()).isEmpty();
        assertThat(service.listMy(true, PageRequest.ofSize(100), users.get(1)).items()).isEmpty();
    }

    @Test
    @DisplayName("来源取消后所有候选待办消失")
    void cancelSourceShouldCancelAllPendingRows() {
        List<Long> users = users(2);
        service.dispatch(command("task-cancel", users));

        service.cancelSource(UnifiedTodoService.APPROVAL_SOURCE_TYPE, "task-cancel");

        assertThat(service.listMy(false, PageRequest.ofSize(100), users.getFirst()).items())
                .isEmpty();
        assertThat(service.listMy(false, PageRequest.ofSize(100), users.get(1)).items()).isEmpty();
    }

    @Test
    @DisplayName("拒绝站外来源路径")
    void dispatchShouldRejectExternalSourcePath() {
        DispatchUnifiedTodoCommand command =
                new DispatchUnifiedTodoCommand(
                        UnifiedTodoService.APPROVAL_SOURCE_TYPE,
                        "task-external",
                        "contract",
                        "C-1",
                        "合同审批",
                        "审核",
                        List.of(401L),
                        "https://example.com/task");

        assertThatThrownBy(() -> service.dispatch(command))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("来源路径非法");
    }

    private DispatchUnifiedTodoCommand command(String sourceTaskId, List<Long> assignees) {
        return new DispatchUnifiedTodoCommand(
                UnifiedTodoService.APPROVAL_SOURCE_TYPE,
                sourceTaskId,
                "contract",
                "C-1",
                "合同审批",
                "审核",
                assignees,
                "/approval/center");
    }

    private List<Long> users(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(
                        index -> {
                            AuthenticationUser user = new AuthenticationUser();
                            user.setUsername("todo-user-" + System.nanoTime() + "-" + index);
                            user.setPassword("not-used");
                            user.setDisplayName("待办用户 " + index);
                            return userRepository.insert(user).getId();
                        })
                .toList();
    }
}
