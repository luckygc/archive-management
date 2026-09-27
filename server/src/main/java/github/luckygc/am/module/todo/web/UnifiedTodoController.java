package github.luckygc.am.module.todo.web;

import jakarta.data.page.PageRequest;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.todo.service.UnifiedTodoService;
import github.luckygc.am.module.todo.service.UnifiedTodoService.UnifiedTodoItem;

@RestController
public class UnifiedTodoController {

    private final UnifiedTodoService service;

    public UnifiedTodoController(UnifiedTodoService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/unified-todos")
    public CursorPageResponse<UnifiedTodoItem> listMyTodos(
            @RequestParam(defaultValue = "false") boolean completed,
            PageRequest page,
            @Nullable Authentication authentication) {
        return service.listMy(completed, page, userId(authentication));
    }

    private Long userId(@Nullable Authentication authentication) {
        return AuthenticatedUsers.requireUserId(
                authentication == null ? null : authentication.getPrincipal());
    }
}
