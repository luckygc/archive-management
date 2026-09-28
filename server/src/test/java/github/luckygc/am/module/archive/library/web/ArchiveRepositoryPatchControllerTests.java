package github.luckygc.am.module.archive.library.web;

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
import github.luckygc.am.infrastructure.web.GlobalExceptionHandler;
import github.luckygc.am.module.archive.library.ArchiveRepositoryRole;
import github.luckygc.am.module.archive.library.service.ArchiveRepositoryService;
import github.luckygc.am.module.archive.library.service.ArchiveRepositoryService.UpdateArchiveRepositoryRequest;

@DisplayName("档案业务库局部更新 HTTP 入口")
class ArchiveRepositoryPatchControllerTests {

    private final ArchiveRepositoryService service = mock(ArchiveRepositoryService.class);
    private final ArchiveRepositoryController controller = new ArchiveRepositoryController(service);

    @Test
    @DisplayName("Merge Patch 保留未出现字段并解析角色和布尔值")
    void patchParsesProvidedFields() throws Exception {
        MockMvcBuilders.standaloneSetup(controller)
                .build()
                .perform(
                        patch("/archive-repositories/2")
                                .principal(auth())
                                .contentType("application/merge-patch+json")
                                .content(
                                        "{\"repositoryRole\":\"HOLDING\",\"enabled\":false,\"unknown\":null}"))
                .andExpect(status().isOk());

        verify(service)
                .update(
                        2L,
                        new UpdateArchiveRepositoryRequest(
                                null, null, ArchiveRepositoryRole.HOLDING, false, null),
                        9L);
    }

    @Test
    @DisplayName("普通 JSON、必需字段删除和只读字段修改均被拒绝")
    void patchRejectsInvalidDocuments() throws Exception {
        var mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();

        mvc.perform(
                        patch("/archive-repositories/2")
                                .principal(auth())
                                .contentType("application/json")
                                .content("{\"repositoryName\":\"新名称\"}"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(
                        patch("/archive-repositories/2")
                                .principal(auth())
                                .contentType("application/merge-patch+json")
                                .content("{\"repositoryName\":null}"))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        patch("/archive-repositories/2")
                                .principal(auth())
                                .contentType("application/merge-patch+json")
                                .content("{\"systemFlag\":false}"))
                .andExpect(status().isBadRequest());

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
                        return "档案管理员";
                    }
                },
                null);
    }
}
