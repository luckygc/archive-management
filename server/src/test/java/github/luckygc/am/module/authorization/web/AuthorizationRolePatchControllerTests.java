package github.luckygc.am.module.authorization.web;

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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.infrastructure.web.GlobalExceptionHandler;
import github.luckygc.am.module.authorization.service.AuthorizationRoleManagementService;
import github.luckygc.am.module.authorization.service.AuthorizationRoleManagementService.AuthorizationRoleDto;
import github.luckygc.am.module.authorization.service.AuthorizationRoleManagementService.UpdateAuthorizationRoleRequest;

@DisplayName("授权角色局部更新 HTTP 入口")
class AuthorizationRolePatchControllerTests {

    private final AuthorizationRoleManagementService roleService =
            mock(AuthorizationRoleManagementService.class);
    private final AuthorizationRoleManagementController controller =
            new AuthorizationRoleManagementController(roleService);

    @Test
    @DisplayName("Merge Patch 显式删除说明且响应省略已删除字段")
    void patchRemovesDescription() throws Exception {
        when(roleService.updateRole(eq(7L), any(), eq(9L)))
                .thenReturn(new AuthorizationRoleDto(7L, "档案管理员", null, true, "now"));

        MockMvcBuilders.standaloneSetup(controller)
                .build()
                .perform(
                        patch("/authorization-roles/7")
                                .principal(auth())
                                .contentType("application/merge-patch+json")
                                .content("{\"description\":null,\"unknown\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").doesNotExist());

        ArgumentCaptor<UpdateAuthorizationRoleRequest> captor =
                ArgumentCaptor.forClass(UpdateAuthorizationRoleRequest.class);
        verify(roleService).updateRole(eq(7L), captor.capture(), eq(9L));
        assertThat(captor.getValue().descriptionChanged()).isTrue();
        assertThat(captor.getValue().description()).isNull();
        assertThat(captor.getValue().roleName()).isNull();
    }

    @Test
    @DisplayName("普通 JSON、删除必需字段和修改只读字段均被拒绝")
    void patchRejectsInvalidDocuments() throws Exception {
        var mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();

        mvc.perform(
                        patch("/authorization-roles/7")
                                .principal(auth())
                                .contentType("application/json")
                                .content("{\"roleName\":\"档案管理员\"}"))
                .andExpect(status().isUnsupportedMediaType());
        for (String body :
                java.util.List.of("{\"roleName\":null}", "{\"enabled\":null}", "{\"id\":null}")) {
            mvc.perform(
                            patch("/authorization-roles/7")
                                    .principal(auth())
                                    .contentType("application/merge-patch+json")
                                    .content(body))
                    .andExpect(status().isBadRequest());
        }

        verifyNoInteractions(roleService);
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
