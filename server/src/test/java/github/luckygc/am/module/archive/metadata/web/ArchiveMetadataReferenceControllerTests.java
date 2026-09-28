package github.luckygc.am.module.archive.metadata.web;

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
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveFondsService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataReferenceService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.UpdateArchiveRetentionPeriodRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.UpdateArchiveSecurityLevelRequest;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@DisplayName("档案元数据参照值 HTTP 入口")
class ArchiveMetadataReferenceControllerTests {

    private final ArchiveMetadataReferenceService referenceService =
            mock(ArchiveMetadataReferenceService.class);
    private final ArchiveMetadataController controller =
            new ArchiveMetadataController(
                    mock(ArchiveMetadataService.class),
                    referenceService,
                    mock(ArchiveCategoryService.class),
                    mock(ArchiveFondsService.class),
                    mock(AuthorizationPermissionService.class));

    @Test
    @DisplayName("参照值更新按 Merge Patch 保留缺失名称和未知空字段")
    void referencePatchesPreserveMissingName() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(
                        patch("/archive-security-levels/3")
                                .principal(auth())
                                .contentType("application/merge-patch+json")
                                .content("{\"unrecognized\":null}"))
                .andExpect(status().isOk());
        mvc.perform(
                        patch("/archive-retention-periods/4")
                                .principal(auth())
                                .contentType("application/merge-patch+json")
                                .content("{\"periodName\":\"永久\"}"))
                .andExpect(status().isOk());

        verify(referenceService)
                .updateSecurityLevel(3L, new UpdateArchiveSecurityLevelRequest(null));
        verify(referenceService)
                .updateRetentionPeriod(4L, new UpdateArchiveRetentionPeriodRequest("永久"));
    }

    @Test
    @DisplayName("参照值更新拒绝普通 JSON、必需名称删除和只读字段修改")
    void referencePatchesRejectInvalidDocuments() throws Exception {
        var mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();

        mvc.perform(
                        patch("/archive-security-levels/3")
                                .principal(auth())
                                .contentType("application/json")
                                .content("{\"levelName\":\"秘密\"}"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(
                        patch("/archive-security-levels/3")
                                .principal(auth())
                                .contentType("application/merge-patch+json")
                                .content("{\"levelName\":null}"))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        patch("/archive-retention-periods/4")
                                .principal(auth())
                                .contentType("application/merge-patch+json")
                                .content("{\"enabled\":false}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(referenceService);
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
