package github.luckygc.am.module.authentication.service;

import java.util.List;

import jakarta.data.page.CursoredPage;
import jakarta.data.restrict.Restrict;
import jakarta.data.restrict.Restriction;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.api.CursorPageRequest;
import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.authentication.AuthenticationUser;
import github.luckygc.am.module.authentication._AuthenticationUser;
import github.luckygc.am.module.authentication.repository.AuthenticationUserDataRepository;
import github.luckygc.am.module.authorization.AuthorizationRole;
import github.luckygc.am.module.authorization.AuthorizationUserRoleRelation;
import github.luckygc.am.module.authorization.repository.AuthorizationRoleDataRepository;
import github.luckygc.am.module.authorization.repository.AuthorizationUserRoleRelationDataRepository;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@Service
public class AuthenticationUserManagementService {

    private final AuthenticationUserDataRepository userRepository;
    private final AuthorizationRoleDataRepository roleRepository;
    private final AuthorizationUserRoleRelationDataRepository userRoleRelationRepository;
    private final AuthorizationPermissionService permissionService;
    private final PasswordEncoder passwordEncoder;

    public AuthenticationUserManagementService(
            AuthenticationUserDataRepository userRepository,
            AuthorizationRoleDataRepository roleRepository,
            AuthorizationUserRoleRelationDataRepository userRoleRelationRepository,
            AuthorizationPermissionService permissionService,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRelationRepository = userRoleRelationRepository;
        this.permissionService = permissionService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<AuthenticationUserDto> listUsers(
            @Nullable String keyword, CursorPageRequest pageRequest, Long operatorUserId) {
        requireUserManage(operatorUserId);
        Restriction<AuthenticationUser> restriction = Restrict.unrestricted();
        if (StringUtils.isNotBlank(keyword)) {
            String lowered = keyword.toLowerCase().trim();
            restriction =
                    Restrict.any(
                            _AuthenticationUser.username.lower().contains(lowered),
                            _AuthenticationUser.displayName.lower().contains(lowered));
        }
        CursoredPage<AuthenticationUser> page =
                userRepository.filterBy(restriction, pageRequest.pageRequest());
        return CursorPageResponse.from(page, pageRequest, AuthenticationUserDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public AuthenticationUserDetailDto getUserDetail(Long id, Long operatorUserId) {
        requireUserManage(operatorUserId);
        AuthenticationUser user =
                userRepository
                        .findById(id)
                        .orElseThrow(() -> new BadRequestException("用户不存在", "id", "用户不存在"));
        List<Long> roleIds =
                userRoleRelationRepository.findByUserId(user.getId()).stream()
                        .map(AuthorizationUserRoleRelation::getRoleId)
                        .distinct()
                        .toList();
        List<AuthorizationRole> roles =
                roleIds.isEmpty() ? List.of() : roleRepository.findByIdIn(roleIds);
        List<RoleSummary> roleSummaries =
                roles.stream()
                        .filter(AuthorizationRole::isEnabled)
                        .map(r -> new RoleSummary(r.getId(), r.getRoleName()))
                        .toList();
        return AuthenticationUserDetailDto.fromEntity(user, roleSummaries);
    }

    @Transactional
    public AuthenticationUserDto createUser(
            CreateAuthenticationUserRequest request, Long operatorUserId) {
        requireUserManage(operatorUserId);
        if (StringUtils.isBlank(request.username())) {
            throw new BadRequestException("用户名不能为空", "username", "用户名不能为空");
        }
        if (StringUtils.isBlank(request.password())) {
            throw new BadRequestException("密码不能为空", "password", "密码不能为空");
        }
        if (userRepository.findOptionalByUsername(request.username().trim()) != null) {
            throw new BadRequestException(
                    "用户名已存在", "username", "用户名 " + request.username() + " 已存在");
        }
        AuthenticationUser user = new AuthenticationUser();
        user.setUsername(request.username().trim());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setDisplayName(
                StringUtils.isNotBlank(request.displayName())
                        ? request.displayName().trim()
                        : request.username().trim());
        user.setEmail(StringUtils.isNotBlank(request.email()) ? request.email().trim() : null);
        user.setMobilePhone(
                StringUtils.isNotBlank(request.mobilePhone())
                        ? request.mobilePhone().trim()
                        : null);
        user.setEnabled(true);
        user = userRepository.insert(user);
        return AuthenticationUserDto.fromEntity(user);
    }

    @Transactional
    public AuthenticationUserDto updateUser(
            Long id, UpdateAuthenticationUserRequest request, Long operatorUserId) {
        requireUserManage(operatorUserId);
        AuthenticationUser user =
                userRepository
                        .findById(id)
                        .orElseThrow(() -> new BadRequestException("用户不存在", "id", "用户不存在"));
        if (request.displayName() != null) {
            user.setDisplayName(
                    StringUtils.isNotBlank(request.displayName())
                            ? request.displayName().trim()
                            : user.getUsername());
        }
        if (request.email() != null) {
            user.setEmail(StringUtils.isNotBlank(request.email()) ? request.email().trim() : null);
        }
        if (request.mobilePhone() != null) {
            user.setMobilePhone(
                    StringUtils.isNotBlank(request.mobilePhone())
                            ? request.mobilePhone().trim()
                            : null);
        }
        if (request.enabled() != null) {
            user.setEnabled(request.enabled());
        }
        user = userRepository.update(user);
        return AuthenticationUserDto.fromEntity(user);
    }

    @Transactional
    public void resetPassword(Long id, ResetPasswordRequest request, Long operatorUserId) {
        requireUserManage(operatorUserId);
        if (StringUtils.isBlank(request.newPassword())) {
            throw new BadRequestException("新密码不能为空", "newPassword", "新密码不能为空");
        }
        AuthenticationUser user =
                userRepository
                        .findById(id)
                        .orElseThrow(() -> new BadRequestException("用户不存在", "id", "用户不存在"));
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.update(user);
    }

    @Transactional(readOnly = true)
    public List<RoleSummary> listUserRoles(Long id, Long operatorUserId) {
        requireUserManage(operatorUserId);
        AuthenticationUser user =
                userRepository
                        .findById(id)
                        .orElseThrow(() -> new BadRequestException("用户不存在", "id", "用户不存在"));
        List<Long> roleIds =
                userRoleRelationRepository.findByUserId(user.getId()).stream()
                        .map(AuthorizationUserRoleRelation::getRoleId)
                        .distinct()
                        .toList();
        return roleIds.isEmpty()
                ? List.of()
                : roleRepository.findByIdIn(roleIds).stream()
                        .filter(AuthorizationRole::isEnabled)
                        .map(r -> new RoleSummary(r.getId(), r.getRoleName()))
                        .toList();
    }

    @Transactional
    public List<RoleSummary> saveUserRoles(
            Long id, SaveUserRolesRequest request, Long operatorUserId) {
        requireUserManage(operatorUserId);
        AuthenticationUser user =
                userRepository
                        .findById(id)
                        .orElseThrow(() -> new BadRequestException("用户不存在", "id", "用户不存在"));
        userRoleRelationRepository.deleteByUserId(user.getId());
        for (Long roleId : request.roleIds()) {
            AuthorizationRole role =
                    roleRepository
                            .findById(roleId)
                            .orElseThrow(
                                    () ->
                                            new BadRequestException(
                                                    "角色不存在", "roleIds", "角色 " + roleId + " 不存在"));
            if (!role.isEnabled()) {
                throw new BadRequestException(
                        "角色已停用", "roleIds", "角色 " + role.getRoleName() + " 已停用");
            }
            AuthorizationUserRoleRelation relation = new AuthorizationUserRoleRelation();
            relation.setUserId(user.getId());
            relation.setRoleId(role.getId());
            userRoleRelationRepository.insert(relation);
        }
        return listUserRoles(id, operatorUserId);
    }

    private void requireUserManage(Long operatorUserId) {
        permissionService.requirePermission(
                operatorUserId, AuthorizationPermissionCode.AUTHENTICATION_USER_MANAGE);
    }

    public record CreateAuthenticationUserRequest(
            String username,
            String password,
            @Nullable String displayName,
            @Nullable String email,
            @Nullable String mobilePhone) {}

    public record UpdateAuthenticationUserRequest(
            @Nullable String displayName,
            @Nullable String email,
            @Nullable String mobilePhone,
            @Nullable Boolean enabled) {}

    public record ResetPasswordRequest(String newPassword) {}

    public record SaveUserRolesRequest(List<Long> roleIds) {}

    public record AuthenticationUserDto(
            Long id,
            String username,
            String displayName,
            @Nullable String email,
            @Nullable String mobilePhone,
            boolean enabled,
            String createdAt) {
        static AuthenticationUserDto fromEntity(AuthenticationUser user) {
            return new AuthenticationUserDto(
                    user.getId(),
                    user.getUsername(),
                    user.getDisplayName(),
                    user.getEmail(),
                    user.getMobilePhone(),
                    user.isEnabled(),
                    user.getCreatedAt() != null ? user.getCreatedAt().toString() : "");
        }
    }

    public record AuthenticationUserDetailDto(
            Long id,
            String username,
            String displayName,
            @Nullable String email,
            @Nullable String mobilePhone,
            boolean enabled,
            String createdAt,
            List<RoleSummary> roles) {
        static AuthenticationUserDetailDto fromEntity(
                AuthenticationUser user, List<RoleSummary> roles) {
            return new AuthenticationUserDetailDto(
                    user.getId(),
                    user.getUsername(),
                    user.getDisplayName(),
                    user.getEmail(),
                    user.getMobilePhone(),
                    user.isEnabled(),
                    user.getCreatedAt() != null ? user.getCreatedAt().toString() : "",
                    List.copyOf(roles));
        }
    }

    public record RoleSummary(Long id, String roleName) {}
}
