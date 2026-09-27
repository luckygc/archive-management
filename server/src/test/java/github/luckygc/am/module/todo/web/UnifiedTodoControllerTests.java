package github.luckygc.am.module.todo.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import jakarta.data.page.PageRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;

import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.module.todo.service.UnifiedTodoService;
import github.luckygc.am.module.todo.service.UnifiedTodoService.UnifiedTodoItem;

@DisplayName("统一待办接口")
class UnifiedTodoControllerTests {

    @Test
    @DisplayName("列表始终使用当前认证用户")
    void listShouldUseAuthenticatedUser() {
        UnifiedTodoService service = mock(UnifiedTodoService.class);
        UnifiedTodoController controller = new UnifiedTodoController(service);
        PageRequest page = PageRequest.ofSize(100);
        CursorPageResponse<UnifiedTodoItem> response =
                CursorPageResponse.withCursorValues(List.of(), 100, null, null, null, null, null);
        when(service.listMy(false, page, 8L)).thenReturn(response);

        assertThat(controller.listMyTodos(false, page, auth())).isSameAs(response);
        verify(service).listMy(false, page, 8L);
    }

    private TestingAuthenticationToken auth() {
        return new TestingAuthenticationToken(
                new AuthenticatedUser() {
                    @Override
                    public Long id() {
                        return 8L;
                    }

                    @Override
                    public String displayName() {
                        return "管理员";
                    }
                },
                null);
    }
}
