package github.luckygc.am.module.archive.physical.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.infrastructure.web.GlobalExceptionHandler;
import github.luckygc.am.module.archive.physical.ArchivePhysicalCustodyStatus;
import github.luckygc.am.module.archive.physical.service.ArchivePhysicalObjectService;
import github.luckygc.am.module.archive.physical.service.ArchivePhysicalObjectService.ArchivePhysicalObjectResponse;
import github.luckygc.am.module.archive.physical.service.ArchivePhysicalObjectService.UpdateArchivePhysicalObjectRequest;

@DisplayName("档案实物局部更新 HTTP 入口")
class ArchivePhysicalObjectControllerTests {

    private final ArchivePhysicalObjectService service = mock(ArchivePhysicalObjectService.class);
    private final ArchivePhysicalObjectController controller =
            new ArchivePhysicalObjectController(service);

    @Test
    @DisplayName("只改备注时保留其他属性，显式 null 删除备注")
    void patchTracksPresentFieldsAndRemovesOptionalField() throws Exception {
        when(service.update(eq(51L), any(), eq(9L)))
                .thenReturn(
                        new ArchivePhysicalObjectResponse(
                                51L,
                                31L,
                                null,
                                "B001",
                                "PAPER",
                                null,
                                null,
                                null,
                                ArchivePhysicalCustodyStatus.DEPARTMENT_CUSTODY,
                                null,
                                null,
                                null,
                                null));

        MockMvcBuilders.standaloneSetup(controller)
                .build()
                .perform(
                        patch("/archive-physical-objects/51")
                                .principal(auth())
                                .contentType("application/merge-patch+json")
                                .content("{\"remark\":null,\"unknown\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remark").doesNotExist())
                .andExpect(jsonPath("$.barcode").value("B001"));

        ArgumentCaptor<UpdateArchivePhysicalObjectRequest> captor =
                ArgumentCaptor.forClass(UpdateArchivePhysicalObjectRequest.class);
        verify(service).update(eq(51L), captor.capture(), eq(9L));
        assertThat(captor.getValue().fields()).contains("remark").doesNotContain("barcode");
        assertThat(captor.getValue().remark()).isNull();
    }

    @Test
    @DisplayName("普通 JSON、只读位置和错误数量被拒绝")
    void rejectInvalidPatch() throws Exception {
        var mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
        mvc.perform(
                        patch("/archive-physical-objects/51")
                                .principal(auth())
                                .contentType("application/json")
                                .content("{\"remark\":\"完好\"}"))
                .andExpect(status().isUnsupportedMediaType());
        for (String body : List.of("{\"currentLocationId\":null}", "{\"quantity\":\"abc\"}")) {
            mvc.perform(
                            patch("/archive-physical-objects/51")
                                    .principal(auth())
                                    .contentType("application/merge-patch+json")
                                    .content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    private TestingAuthenticationToken auth() {
        return new TestingAuthenticationToken(
                new AuthenticatedUser() {
                    @Override
                    public Long id() {
                        return 9L;
                    }

                    @Override
                    public String displayName() {
                        return "管理员";
                    }
                },
                null);
    }
}
