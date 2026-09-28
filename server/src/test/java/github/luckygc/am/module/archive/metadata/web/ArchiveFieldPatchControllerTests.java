package github.luckygc.am.module.archive.metadata.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveFondsService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataReferenceService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@DisplayName("档案字段定义局部更新 HTTP 入口")
class ArchiveFieldPatchControllerTests {

    private final ArchiveMetadataService service = mock(ArchiveMetadataService.class);
    private final ArchiveMetadataController controller =
            new ArchiveMetadataController(
                    service,
                    mock(ArchiveMetadataReferenceService.class),
                    mock(ArchiveCategoryService.class),
                    mock(ArchiveFondsService.class),
                    mock(AuthorizationPermissionService.class));

    @Test
    @DisplayName("Merge Patch 请求交给字段定义服务")
    void patchUsesMergePatch() throws Exception {
        MockMvcBuilders.standaloneSetup(controller)
                .build()
                .perform(
                        patch("/archive-categories/12/fields/7")
                                .principal(auth())
                                .contentType("application/merge-patch+json")
                                .content("{\"fieldName\":\"新文号\"}"))
                .andExpect(status().isOk());

        verify(service).patchField(eq(12L), eq(7L), any(), eq(9L));
    }

    @Test
    @DisplayName("普通 JSON 请求被拒绝")
    void patchRejectsOrdinaryJson() throws Exception {
        MockMvcBuilders.standaloneSetup(controller)
                .build()
                .perform(
                        patch("/archive-categories/12/fields/7")
                                .principal(auth())
                                .contentType("application/json")
                                .content("{\"fieldName\":\"新文号\"}"))
                .andExpect(status().isUnsupportedMediaType());

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
