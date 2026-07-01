package github.luckygc.am.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("当前登录用户工具")
class AuthenticatedUsersTests {

    @Test
    @DisplayName("从认证主体中读取当前用户 ID")
    void currentUserIdShouldReturnAuthenticatedUserId() {
        assertThat(AuthenticatedUsers.currentUserId(user())).isEqualTo(9L);
        assertThat(AuthenticatedUsers.requireUserId(user())).isEqualTo(9L);
    }

    @Test
    @DisplayName("未登录时要求当前用户 ID 返回 401")
    void requireUserIdShouldRejectMissingAuthentication() {
        assertThat(AuthenticatedUsers.currentUserId(null)).isNull();
        assertThatThrownBy(() -> AuthenticatedUsers.requireUserId(null))
                .isInstanceOf(UnauthenticatedException.class)
                .hasMessage("请先登录");
    }

    @Test
    @DisplayName("认证主体不是系统用户时要求当前用户 ID 返回 401")
    void requireUserIdShouldRejectUnsupportedPrincipal() {
        assertThat(AuthenticatedUsers.currentUserId("admin")).isNull();
        assertThatThrownBy(() -> AuthenticatedUsers.requireUserId("admin"))
                .isInstanceOf(UnauthenticatedException.class)
                .hasMessage("请先登录");
    }

    @Test
    @DisplayName("已取得的用户 ID 为空时要求当前用户 ID 返回 401")
    void requireUserIdShouldRejectNullableUserId() {
        assertThat(AuthenticatedUsers.requireResolvedUserId(9L)).isEqualTo(9L);
        assertThatThrownBy(() -> AuthenticatedUsers.requireResolvedUserId(null))
                .isInstanceOf(UnauthenticatedException.class)
                .hasMessage("请先登录");
    }

    private AuthenticatedUser user() {
        return new AuthenticatedUser() {
            @Override
            public Long id() {
                return 9L;
            }

            @Override
            public String displayName() {
                return "管理员";
            }
        };
    }
}
