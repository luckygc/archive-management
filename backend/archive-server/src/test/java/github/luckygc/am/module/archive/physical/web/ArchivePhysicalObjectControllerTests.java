package github.luckygc.am.module.archive.physical.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import github.luckygc.am.module.archive.physical.service.ArchivePhysicalObjectService.BatchAssignArchiveLocationRequest;

@DisplayName("档案实物 HTTP 入口")
class ArchivePhysicalObjectControllerTests {

    @Test
    @DisplayName("实物对象和位置历史使用独立资源")
    void objectAndHistoryUseDedicatedResources() throws Exception {
        Method find =
                ArchivePhysicalObjectController.class.getDeclaredMethod(
                        "findByOwner", Long.class, Long.class, Authentication.class);
        Method history =
                ArchivePhysicalObjectController.class.getDeclaredMethod(
                        "listLocationHistory", Long.class, Authentication.class);

        assertThat(find.getAnnotation(GetMapping.class).value())
                .containsExactly("/api/v1/archive-physical-objects");
        assertThat(history.getAnnotation(GetMapping.class).value())
                .containsExactly("/api/v1/archive-physical-objects/{id}/location-history");
    }

    @Test
    @DisplayName("批量关联位置使用实物对象集合动作")
    void batchLocationUsesCollectionAction() throws Exception {
        Method method =
                ArchivePhysicalObjectController.class.getDeclaredMethod(
                        "batchAssignLocation",
                        BatchAssignArchiveLocationRequest.class,
                        Authentication.class);

        assertThat(method.getAnnotation(PostMapping.class).value())
                .containsExactly("/api/v1/archive-physical-objects:batchAssignLocation");
    }
}
