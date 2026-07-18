package github.luckygc.am.module.authentication.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

@DisplayName("认证用户管理 HTTP 入口")
class AuthenticationUserManagementControllerTests {

    @Test
    @DisplayName("PATCH 请求体使用 Jackson 3 JsonNode")
    void updateUserShouldUseJackson3JsonNode() {
        Method method =
                List.of(AuthenticationUserManagementController.class.getDeclaredMethods()).stream()
                        .filter(candidate -> candidate.getName().equals("updateUser"))
                        .findFirst()
                        .orElseThrow();

        assertThat(method.getParameterTypes()[1]).isEqualTo(tools.jackson.databind.JsonNode.class);
    }

    @Test
    @DisplayName("数据范围授权用户选项使用独立集合资源")
    void userOptionsShouldUseDedicatedResource() {
        Method method =
                List.of(AuthenticationUserManagementController.class.getDeclaredMethods()).stream()
                        .filter(candidate -> candidate.getName().equals("listUserOptions"))
                        .findFirst()
                        .orElseThrow();

        assertThat(method.getAnnotation(GetMapping.class).value())
                .containsExactly("/api/v1/authentication-user-options");
    }
}
