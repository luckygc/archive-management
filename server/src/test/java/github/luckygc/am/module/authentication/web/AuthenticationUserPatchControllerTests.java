package github.luckygc.am.module.authentication.web;

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
import github.luckygc.am.module.authentication.service.AuthenticationUserManagementService;
import github.luckygc.am.module.authentication.service.AuthenticationUserManagementService.AuthenticationUserDto;
import github.luckygc.am.module.authentication.service.AuthenticationUserManagementService.UpdateAuthenticationUserRequest;
import github.luckygc.am.module.authentication.service.TotpCredentialService;

@DisplayName("认证用户局部更新 HTTP 入口")
class AuthenticationUserPatchControllerTests {

    private final AuthenticationUserManagementService userService =
            mock(AuthenticationUserManagementService.class);
    private final AuthenticationUserManagementController controller =
            new AuthenticationUserManagementController(
                    userService, mock(TotpCredentialService.class));

    @Test
    @DisplayName("Merge Patch 删除可选字段，保留未提交字段并省略响应中的空字段")
    void patchRemovesOptionalFields() throws Exception {
        when(userService.updateUser(eq(7L), any(), eq(9L)))
                .thenReturn(
                        new AuthenticationUserDto(
                                7L, "zhangsan", "张三", null, null, null, null, null, true, "now"));

        MockMvcBuilders.standaloneSetup(controller)
                .build()
                .perform(
                        patch("/authentication-users/7")
                                .principal(auth())
                                .contentType("application/merge-patch+json")
                                .content("{\"email\":null,\"departmentId\":null,\"unknown\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.departmentId").doesNotExist());

        ArgumentCaptor<UpdateAuthenticationUserRequest> captor =
                ArgumentCaptor.forClass(UpdateAuthenticationUserRequest.class);
        verify(userService).updateUser(eq(7L), captor.capture(), eq(9L));
        assertThat(captor.getValue().email()).isEmpty();
        assertThat(captor.getValue().mobilePhone()).isNull();
        assertThat(captor.getValue().departmentUpdate().changing()).isTrue();
        assertThat(captor.getValue().departmentUpdate().departmentId()).isNull();
    }

    @Test
    @DisplayName("普通 JSON、删除必需字段和修改只读字段均被拒绝")
    void patchRejectsInvalidDocuments() throws Exception {
        var mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();

        mvc.perform(
                        patch("/authentication-users/7")
                                .principal(auth())
                                .contentType("application/json")
                                .content("{\"displayName\":\"李四\"}"))
                .andExpect(status().isUnsupportedMediaType());
        for (String body :
                java.util.List.of(
                        "{\"displayName\":null}", "{\"enabled\":null}", "{\"username\":null}")) {
            mvc.perform(
                            patch("/authentication-users/7")
                                    .principal(auth())
                                    .contentType("application/merge-patch+json")
                                    .content(body))
                    .andExpect(status().isBadRequest());
        }

        verifyNoInteractions(userService);
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
