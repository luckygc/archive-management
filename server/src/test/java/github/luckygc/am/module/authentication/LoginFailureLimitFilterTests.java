package github.luckygc.am.module.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import github.luckygc.am.module.authentication.service.LoginFailureLimitService;
import github.luckygc.am.module.authentication.web.security.LoginFailureLimitFilter;

@DisplayName("登录失败限制过滤器")
class LoginFailureLimitFilterTests {

    private final LoginFailureLimitService failureLimitService =
            mock(LoginFailureLimitService.class);
    private final LoginFailureLimitFilter filter = new LoginFailureLimitFilter(failureLimitService);

    @Test
    @DisplayName("账号未受限时直接放行账号密码登录")
    void allowsPasswordLoginWithoutCapToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login-sessions");
        request.setParameter("username", "admin");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean proceeded = new AtomicBoolean();
        FilterChain chain = (ignoredRequest, ignoredResponse) -> proceeded.set(true);

        filter.doFilter(request, response, chain);

        verify(failureLimitService).assertLoginAllowed("admin");
        assertThat(proceeded).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("账号受限时拒绝登录")
    void blocksLimitedUsername() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login-sessions");
        request.setParameter("username", "admin");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean proceeded = new AtomicBoolean();
        doThrow(new LoginBlockedException(LocalDateTime.now().plusMinutes(5)))
                .when(failureLimitService)
                .assertLoginAllowed("admin");

        filter.doFilter(
                request, response, (ignoredRequest, ignoredResponse) -> proceeded.set(true));

        assertThat(proceeded).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
    }
}
