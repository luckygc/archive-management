package github.luckygc.am.module.archive.metadata.web;

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
import github.luckygc.am.module.archive.metadata.ArchiveManagementMode;
import github.luckygc.am.module.archive.metadata.ArchiveTableStatus;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveFondsService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataReferenceService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.UpdateArchiveCategoryRequest;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@DisplayName("档案分类局部更新 HTTP 入口")
class ArchiveCategoryPatchControllerTests {

    private final ArchiveCategoryService categoryService = mock(ArchiveCategoryService.class);
    private final ArchiveMetadataController controller =
            new ArchiveMetadataController(
                    mock(ArchiveMetadataService.class),
                    mock(ArchiveMetadataReferenceService.class),
                    categoryService,
                    mock(ArchiveFondsService.class),
                    mock(AuthorizationPermissionService.class));

    @Test
    @DisplayName("显式 null 清除父分类，响应省略 parentId")
    void patchRemovesParent() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 9, 29, 6, 0);
        when(categoryService.updateCategory(eq(12L), any(), eq(9L)))
                .thenReturn(
                        new ArchiveCategoryDto(
                                12L,
                                null,
                                "contract",
                                "合同档案",
                                ArchiveManagementMode.ITEM_ONLY,
                                null,
                                null,
                                null,
                                null,
                                ArchiveTableStatus.NOT_BUILT,
                                null,
                                true,
                                0,
                                now,
                                now));

        MockMvcBuilders.standaloneSetup(controller)
                .build()
                .perform(
                        patch("/archive-categories/12")
                                .principal(auth())
                                .contentType("application/merge-patch+json")
                                .content("{\"parentId\":null,\"unknown\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentId").doesNotExist());

        ArgumentCaptor<UpdateArchiveCategoryRequest> captor =
                ArgumentCaptor.forClass(UpdateArchiveCategoryRequest.class);
        verify(categoryService).updateCategory(eq(12L), captor.capture(), eq(9L));
        assertThat(captor.getValue().parentIdPresent()).isTrue();
        assertThat(captor.getValue().parentId()).isNull();
        assertThat(captor.getValue().categoryName()).isNull();
    }

    @Test
    @DisplayName("普通 JSON、删除必需字段及修改只读字段均被拒绝")
    void patchRejectsInvalidDocuments() throws Exception {
        var mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
        mvc.perform(
                        patch("/archive-categories/12")
                                .principal(auth())
                                .contentType("application/json")
                                .content("{\"categoryName\":\"合同档案\"}"))
                .andExpect(status().isUnsupportedMediaType());
        for (String body :
                List.of("{\"categoryName\":null}", "{\"managementMode\":null}", "{\"id\":null}")) {
            mvc.perform(
                            patch("/archive-categories/12")
                                    .principal(auth())
                                    .contentType("application/merge-patch+json")
                                    .content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(categoryService);
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
