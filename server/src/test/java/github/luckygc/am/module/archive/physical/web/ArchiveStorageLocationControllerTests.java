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

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.infrastructure.web.GlobalExceptionHandler;
import github.luckygc.am.module.archive.physical.service.ArchiveStorageLocationService;
import github.luckygc.am.module.archive.physical.service.ArchiveStorageLocationService.ArchiveStorageLocationResponse;
import github.luckygc.am.module.archive.physical.service.ArchiveStorageLocationService.UpdateArchiveStorageLocationRequest;

@DisplayName("真实库房与存放位置局部更新 HTTP 入口")
class ArchiveStorageLocationControllerTests {

    private final ArchiveStorageLocationService service = mock(ArchiveStorageLocationService.class);
    private final ArchiveStorageLocationController controller =
            new ArchiveStorageLocationController(service);

    @Test
    @DisplayName("移除父位置时省略响应字段，未知 null 不影响资源")
    void removeParentLocation() throws Exception {
        when(service.updateLocation(eq(7L), any(), eq(9L)))
                .thenReturn(
                        new ArchiveStorageLocationResponse(
                                7L,
                                1L,
                                null,
                                "R1",
                                "一号架",
                                "RACK",
                                true,
                                0,
                                LocalDateTime.now(),
                                LocalDateTime.now()));

        MockMvcBuilders.standaloneSetup(controller)
                .build()
                .perform(
                        patch("/archive-storage-locations/7")
                                .principal(auth())
                                .contentType("application/merge-patch+json")
                                .content("{\"parentId\":null,\"unknown\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentId").doesNotExist());

        ArgumentCaptor<UpdateArchiveStorageLocationRequest> captor =
                ArgumentCaptor.forClass(UpdateArchiveStorageLocationRequest.class);
        verify(service).updateLocation(eq(7L), captor.capture(), eq(9L));
        assertThat(captor.getValue().parentIdPresent()).isTrue();
        assertThat(captor.getValue().parentId()).isNull();
        assertThat(captor.getValue().locationName()).isNull();
    }

    @Test
    @DisplayName("普通 JSON、删除必需字段和修改只读字段被拒绝")
    void rejectInvalidPatch() throws Exception {
        var mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
        mvc.perform(
                        patch("/archive-warehouses/7")
                                .principal(auth())
                                .contentType("application/json")
                                .content("{\"warehouseName\":\"新库房\"}"))
                .andExpect(status().isUnsupportedMediaType());
        for (String body :
                List.of("{\"warehouseCode\":null}", "{\"enabled\":null}", "{\"id\":null}")) {
            mvc.perform(
                            patch("/archive-warehouses/7")
                                    .principal(auth())
                                    .contentType("application/merge-patch+json")
                                    .content(body))
                    .andExpect(status().isBadRequest());
        }
        for (String body :
                List.of(
                        "{\"warehouseId\":null}",
                        "{\"locationType\":null}",
                        "{\"updatedAt\":null}")) {
            mvc.perform(
                            patch("/archive-storage-locations/7")
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
