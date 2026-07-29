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
    @DisplayName("条目和案卷实物对象使用单资源路径")
    void objectAndHistoryUseDedicatedResources() throws Exception {
        Method itemObject =
                ArchivePhysicalObjectController.class.getDeclaredMethod(
                        "getByArchiveItem", Long.class, Authentication.class);
        Method volumeObject =
                ArchivePhysicalObjectController.class.getDeclaredMethod(
                        "getByArchiveVolume", Long.class, Authentication.class);
        Method history =
                ArchivePhysicalObjectController.class.getDeclaredMethod(
                        "listLocationHistory", Long.class, Authentication.class);

        assertThat(itemObject.getAnnotation(GetMapping.class).value())
                .containsExactly("/api/v1/archive-items/{archiveItemId}/physical-object");
        assertThat(volumeObject.getAnnotation(GetMapping.class).value())
                .containsExactly("/api/v1/archive-volumes/{archiveVolumeId}/physical-object");
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
