package github.luckygc.am.common;

import github.luckygc.am.module.auth.AuthRole;
import github.luckygc.am.module.auth.AuthRoleDataRepository;
import github.luckygc.am.module.auth._AuthRole;
import jakarta.data.Order;
import jakarta.data.page.Page;
import jakarta.data.page.PageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TestController {

    private final AuthRoleDataRepository authRoleDataRepository;

    @GetMapping("/api/test")
    public Object test() {
        Page<AuthRole> all = authRoleDataRepository.findAll(PageRequest.ofSize(10), Order.by(_AuthRole.id.asc()));
        return all.content();
    }
}
