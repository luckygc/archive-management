package github.luckygc.am.module.archive.metadata.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.metadata.ArchiveFieldScope;
import github.luckygc.am.module.archive.metadata.ArchiveLayoutSurface;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveFondsService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataReferenceService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFieldLayoutDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFieldLayoutRequest;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@DisplayName("档案字段布局整体替换 HTTP 入口")
class ArchiveFieldLayoutControllerTests {

    private final ArchiveMetadataService service = mock(ArchiveMetadataService.class);
    private final ArchiveMetadataController controller =
            new ArchiveMetadataController(
                    service,
                    mock(ArchiveMetadataReferenceService.class),
                    mock(ArchiveCategoryService.class),
                    mock(ArchiveFondsService.class),
                    mock(AuthorizationPermissionService.class));

    @Test
    @DisplayName("PUT 提交完整布局列表，PATCH 不再作为整体替换入口")
    void putReplacesLayout() throws Exception {
        when(service.savePublicFieldLayout(
                        eq(12L),
                        eq(ArchiveLevel.ITEM),
                        eq(ArchiveFieldScope.METADATA),
                        eq(ArchiveLayoutSurface.TABLE),
                        any(ArchiveFieldLayoutRequest.class),
                        eq(9L)))
                .thenReturn(
                        new ArchiveFieldLayoutDto(ArchiveLayoutSurface.TABLE, "public", List.of()));
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(
                        put("/archive-categories/12/layouts/TABLE")
                                .param("archiveLevel", "ITEM")
                                .param("fieldScope", "METADATA")
                                .principal(auth())
                                .contentType("application/json")
                                .content("{\"items\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
        mvc.perform(
                        patch("/archive-categories/12/layouts/TABLE")
                                .principal(auth())
                                .contentType("application/merge-patch+json")
                                .content("{\"items\":[]}"))
                .andExpect(status().isMethodNotAllowed());

        verify(service)
                .savePublicFieldLayout(
                        eq(12L),
                        eq(ArchiveLevel.ITEM),
                        eq(ArchiveFieldScope.METADATA),
                        eq(ArchiveLayoutSurface.TABLE),
                        any(ArchiveFieldLayoutRequest.class),
                        eq(9L));
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
