package github.luckygc.am.module.archive.physical.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import github.luckygc.am.module.archive.physical.service.ArchivePhysicalTransferService.AcceptArchivePhysicalTransferRequest;
import github.luckygc.am.module.archive.physical.service.ArchivePhysicalTransferService.CreateArchivePhysicalTransferRequest;
import github.luckygc.am.module.archive.physical.service.ArchivePhysicalTransferService.RejectArchivePhysicalTransferRequest;

@DisplayName("实物移交接收 HTTP 入口")
class ArchivePhysicalTransferControllerTests {

    @Test
    @DisplayName("移交批次使用资源路径和接收退回动作")
    void transferUsesResourceAndCustomMethods() throws Exception {
        Method create =
                ArchivePhysicalTransferController.class.getDeclaredMethod(
                        "create", CreateArchivePhysicalTransferRequest.class, Authentication.class);
        Method get =
                ArchivePhysicalTransferController.class.getDeclaredMethod(
                        "get", Long.class, Authentication.class);
        Method accept =
                ArchivePhysicalTransferController.class.getDeclaredMethod(
                        "accept",
                        Long.class,
                        AcceptArchivePhysicalTransferRequest.class,
                        Authentication.class);
        Method reject =
                ArchivePhysicalTransferController.class.getDeclaredMethod(
                        "reject",
                        Long.class,
                        RejectArchivePhysicalTransferRequest.class,
                        Authentication.class);

        assertThat(create.getAnnotation(PostMapping.class).value())
                .containsExactly("/api/v1/archive-physical-transfers");
        assertThat(create.getAnnotation(ResponseStatus.class).value())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(get.getAnnotation(GetMapping.class).value())
                .containsExactly("/api/v1/archive-physical-transfers/{id}");
        assertThat(accept.getAnnotation(PostMapping.class).value())
                .containsExactly("/api/v1/archive-physical-transfers/{id}:accept");
        assertThat(reject.getAnnotation(PostMapping.class).value())
                .containsExactly("/api/v1/archive-physical-transfers/{id}:reject");
    }
}
