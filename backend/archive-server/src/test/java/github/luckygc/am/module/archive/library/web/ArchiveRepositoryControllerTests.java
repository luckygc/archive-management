package github.luckygc.am.module.archive.library.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import github.luckygc.am.module.archive.library.service.ArchiveRepositoryAssignmentService.ChangeArchiveRepositoryRequest;

@DisplayName("档案虚拟业务库 HTTP 入口")
class ArchiveRepositoryControllerTests {

    @Test
    @DisplayName("业务库使用独立集合资源且删除成功返回 204")
    void repositoryUsesDedicatedCollectionResource() throws Exception {
        Method list = ArchiveRepositoryController.class.getDeclaredMethod("list", Boolean.class);
        Method delete =
                ArchiveRepositoryController.class.getDeclaredMethod(
                        "delete", Long.class, Authentication.class);

        assertThat(list.getAnnotation(GetMapping.class).value())
                .containsExactly("/api/v1/archive-repositories");
        assertThat(delete.getAnnotation(DeleteMapping.class).value())
                .containsExactly("/api/v1/archive-repositories/{id}");
        assertThat(delete.getAnnotation(ResponseStatus.class).value())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("条目和案卷通过自定义动作变更业务库")
    void assignmentUsesRepositoryChangeActions() throws Exception {
        Method item =
                ArchiveRepositoryAssignmentController.class.getDeclaredMethod(
                        "changeItemRepository",
                        Long.class,
                        ChangeArchiveRepositoryRequest.class,
                        Authentication.class);
        Method volume =
                ArchiveRepositoryAssignmentController.class.getDeclaredMethod(
                        "changeVolumeRepository",
                        Long.class,
                        ChangeArchiveRepositoryRequest.class,
                        Authentication.class);

        assertThat(item.getAnnotation(PostMapping.class).value())
                .containsExactly("/api/v1/archive-items/{id}:changeRepository");
        assertThat(volume.getAnnotation(PostMapping.class).value())
                .containsExactly("/api/v1/archive-volumes/{id}:changeRepository");
    }
}
