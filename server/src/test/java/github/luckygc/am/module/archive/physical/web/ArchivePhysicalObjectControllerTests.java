package github.luckygc.am.module.archive.physical.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.data.page.PageRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.infrastructure.web.CursorPageArgumentResolver;
import github.luckygc.am.infrastructure.web.CursorPageResponseAdvice;
import github.luckygc.am.infrastructure.web.GlobalExceptionHandler;
import github.luckygc.am.module.archive.physical.ArchivePhysicalCustodyStatus;
import github.luckygc.am.module.archive.physical.service.ArchivePhysicalObjectService;
import github.luckygc.am.module.archive.physical.service.ArchivePhysicalObjectService.ArchivePhysicalLocationHistoryResponse;
import github.luckygc.am.module.archive.physical.service.ArchivePhysicalObjectService.ArchivePhysicalObjectResponse;
import github.luckygc.am.module.archive.physical.service.ArchivePhysicalObjectService.UpdateArchivePhysicalObjectRequest;

@DisplayName("档案实物局部更新 HTTP 入口")
class ArchivePhysicalObjectControllerTests {

    private final ArchivePhysicalObjectService service = mock(ArchivePhysicalObjectService.class);
    private final ArchivePhysicalObjectController controller =
            new ArchivePhysicalObjectController(service);

    @Test
    @DisplayName("位置历史按 URL limit 分页且不默认返回总数")
    void locationHistoryUsesCursorPage() throws Exception {
        when(service.listLocationHistory(eq(51L), any(PageRequest.class), eq(9L)))
                .thenReturn(
                        CursorPageResponse.withLinks(
                                List.of(
                                        new ArchivePhysicalLocationHistoryResponse(
                                                7L,
                                                51L,
                                                null,
                                                21L,
                                                null,
                                                null,
                                                "位置调整",
                                                9L,
                                                LocalDateTime.parse("2026-09-29T08:00:00"))),
                                null,
                                null,
                                null,
                                null,
                                null));
        var mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setCustomArgumentResolvers(new CursorPageArgumentResolver())
                        .setControllerAdvice(
                                new GlobalExceptionHandler(), new CursorPageResponseAdvice())
                        .build();

        mvc.perform(get("/archive-physical-objects/51/location-history?limit=2").principal(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(7))
                .andExpect(jsonPath("$.total").doesNotExist());
        ArgumentCaptor<PageRequest> page = ArgumentCaptor.forClass(PageRequest.class);
        verify(service).listLocationHistory(eq(51L), page.capture(), eq(9L));
        assertThat(page.getValue().size()).isEqualTo(2);
        assertThat(page.getValue().requestTotal()).isFalse();

        mvc.perform(
                        get("/archive-physical-objects/51/location-history?limit=1001")
                                .principal(auth()))
                .andExpect(status().isBadRequest());
    }

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
