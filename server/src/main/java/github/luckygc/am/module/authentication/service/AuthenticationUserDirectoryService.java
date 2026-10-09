package github.luckygc.am.module.authentication.service;

import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.module.authentication.repository.AuthenticationUserDataRepository;

/** 跨模块用户目录只提供身份、部门与启用状态，不暴露凭证。 */
@Service
public class AuthenticationUserDirectoryService {

    private final AuthenticationUserDataRepository repository;

    public AuthenticationUserDirectoryService(AuthenticationUserDataRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<UserSummary> findUser(Long userId) {
        return repository
                .findById(userId)
                .map(
                        user ->
                                new UserSummary(
                                        user.getId(), user.getDepartmentId(), user.isEnabled()));
    }

    public record UserSummary(Long id, @Nullable Long departmentId, boolean enabled) {}
}
