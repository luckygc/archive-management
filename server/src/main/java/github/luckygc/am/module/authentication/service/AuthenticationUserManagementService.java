package github.luckygc.am.module.authentication.service;

import java.util.List;

import jakarta.data.page.CursoredPage;
import jakarta.data.page.PageRequest;
import jakarta.data.restrict.Restrict;
import jakarta.data.restrict.Restriction;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
import github.luckygc.am.module.organization.service.OrganizationDepartmentService;
import github.luckygc.am.module.organization.service.OrganizationDepartmentService.OrganizationDepartmentResponse;

@Service
public class AuthenticationUserManagementService {

    private final AuthenticationUserDataRepository userRepository;
    private final AuthorizationRoleDataRepository roleRepository;
    private final AuthorizationUserRoleRelationDataRepository userRoleRelationRepository;
    private final AuthorizationPermissionService permissionService;
    private final OrganizationDepartmentService departmentService;
    private final PasswordEncoder passwordEncoder;

    public AuthenticationUserManagementService(
            AuthenticationUserDataRepository userRepository,
            AuthorizationRoleDataRepository roleRepository,
            AuthorizationUserRoleRelationDataRepository userRoleRelationRepository,
            AuthorizationPermissionService permissionService,
            OrganizationDepartmentService departmentService,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRelationRepository = userRoleRelationRepository;
        this.permissionService = permissionService;
        this.departmentService = departmentService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<AuthenticationUserDto> listUsers(
            @Nullable String keyword, PageRequest pageRequest, Long operatorUserId) {
        requireUserDirectoryRead(operatorUserId);
        Restriction<AuthenticationUser> restriction = Restrict.unrestricted();
        if (StringUtils.isNotBlank(keyword)) {
            String lowered = keyword.toLowerCase().trim();
            restriction =
                    Restrict.any(
                            _AuthenticationUser.username.lower().contains(lowered),
                            _AuthenticationUser.displayName.lower().contains(lowered));
        }
        CursoredPage<AuthenticationUser> page = userRepository.filterBy(restriction, pageRequest);
        return CursorPageResponse.from(page, pageRequest, this::toUserDto);
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
        return toUserDetailDto(user, roleSummaries);
    }

    @Transactional
    public AuthenticationUserDto createUser(
            CreateAuthenticationUserRequest request, Long operatorUserId) {
        requireUserManage(operatorUserId);
        String username = StringUtils.trimToNull(request.username());
        String displayName = StringUtils.trimToNull(request.displayName());
        if (username == null) {
            throw new BadRequestException("用户名不能为空", "username", "用户名不能为空");
        }
        if (StringUtils.isBlank(request.password())) {
            throw new BadRequestException("密码不能为空", "password", "密码不能为空");
        }
        if (displayName == null) {
            throw new BadRequestException("显示名称不能为空", "displayName", "显示名称不能为空");
        }
        if (userRepository.findOptionalByUsername(username) != null) {
            throw new BadRequestException("用户名已存在", "username", "用户名 " + username + " 已存在");
        }
        AuthenticationUser user = new AuthenticationUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setDisplayName(displayName);
        user.setEmail(StringUtils.trimToNull(request.email()));
        user.setMobilePhone(StringUtils.trimToNull(request.mobilePhone()));
        user.setDepartmentId(validateDepartmentForWrite(request.departmentId()));
        user.setEnabled(true);
        user = userRepository.insert(user);
        return toUserDto(user);
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
            String displayName = StringUtils.trimToNull(request.displayName());
            if (displayName == null) {
                throw new BadRequestException("显示名称不能为空", "displayName", "显示名称不能为空");
            }
            user.setDisplayName(displayName);
        }
        if (request.email() != null) {
            user.setEmail(StringUtils.trimToNull(request.email()));
        }
        if (request.mobilePhone() != null) {
            user.setMobilePhone(StringUtils.trimToNull(request.mobilePhone()));
        }
        if (request.enabled() != null) {
            user.setEnabled(request.enabled());
        }
        DepartmentUpdate departmentUpdate = request.departmentUpdate();
        if (departmentUpdate.changing()) {
            user.setDepartmentId(validateDepartmentForWrite(departmentUpdate.departmentId()));
        }
        user = userRepository.update(user);
        return toUserDto(user);
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
        return listUserRolesInternal(id, operatorUserId);
    }

    private List<RoleSummary> listUserRolesInternal(Long id, Long operatorUserId) {
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
        return listUserRolesInternal(id, operatorUserId);
    }

    private void requireUserManage(Long operatorUserId) {
        permissionService.requirePermission(
                operatorUserId, AuthorizationPermissionCode.AUTHENTICATION_USER_MANAGE);
    }

    private void requireUserDirectoryRead(Long operatorUserId) {
        if (permissionService.hasPermission(
                        operatorUserId,
                        AuthorizationPermissionCode.AUTHENTICATION_USER_MANAGE.code())
                || permissionService.hasPermission(
                        operatorUserId,
                        AuthorizationPermissionCode.ARCHIVE_DATA_SCOPE_MANAGE.code())) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "权限不足");
    }

    private @Nullable Long validateDepartmentForWrite(@Nullable Long departmentId) {
        if (departmentId == null) {
            return null;
        }
        if (departmentId <= 0) {
            throw new BadRequestException("部门不合法", "departmentId", "部门不合法");
        }
        return departmentService.requireEnabledDepartment(departmentId).id();
    }

    private AuthenticationUserDto toUserDto(AuthenticationUser user) {
        DepartmentDisplay department = departmentDisplay(user.getDepartmentId());
        return AuthenticationUserDto.fromEntity(user, department);
    }

    private AuthenticationUserDetailDto toUserDetailDto(
            AuthenticationUser user, List<RoleSummary> roles) {
        DepartmentDisplay department = departmentDisplay(user.getDepartmentId());
        return AuthenticationUserDetailDto.fromEntity(user, roles, department);
    }

    private DepartmentDisplay departmentDisplay(@Nullable Long departmentId) {
        if (departmentId == null) {
            return new DepartmentDisplay(null, null, null);
        }
        OrganizationDepartmentResponse department = departmentService.getDepartment(departmentId);
        return new DepartmentDisplay(
                department.id(), department.departmentCode(), department.departmentName());
    }

    public record CreateAuthenticationUserRequest(
            String username,
            String password,
            @Nullable String displayName,
            @Nullable String email,
            @Nullable String mobilePhone,
            @Nullable Long departmentId) {

        public CreateAuthenticationUserRequest(
                String username,
                String password,
                @Nullable String displayName,
                @Nullable String email,
                @Nullable String mobilePhone) {
            this(username, password, displayName, email, mobilePhone, null);
        }
    }

    public static final class UpdateAuthenticationUserRequest {

        private final @Nullable String displayName;
        private final @Nullable String email;
        private final @Nullable String mobilePhone;
        private final @Nullable Boolean enabled;
        private final DepartmentUpdate departmentUpdate;

        public UpdateAuthenticationUserRequest(
                @Nullable String displayName,
                @Nullable String email,
                @Nullable String mobilePhone,
                @Nullable Boolean enabled) {
            this(displayName, email, mobilePhone, enabled, DepartmentUpdate.withoutChange());
        }

        public UpdateAuthenticationUserRequest(
                @Nullable String displayName,
                @Nullable String email,
                @Nullable String mobilePhone,
                @Nullable Boolean enabled,
                @Nullable Long departmentId) {
            this(displayName, email, mobilePhone, enabled, DepartmentUpdate.changeTo(departmentId));
        }

        private UpdateAuthenticationUserRequest(
                @Nullable String displayName,
                @Nullable String email,
                @Nullable String mobilePhone,
                @Nullable Boolean enabled,
                DepartmentUpdate departmentUpdate) {
            this.displayName = displayName;
            this.email = email;
            this.mobilePhone = mobilePhone;
            this.enabled = enabled;
            this.departmentUpdate = departmentUpdate;
        }

        public static UpdateAuthenticationUserRequest withoutDepartmentChange(
                @Nullable String displayName,
                @Nullable String email,
                @Nullable String mobilePhone,
                @Nullable Boolean enabled) {
            return new UpdateAuthenticationUserRequest(
                    displayName, email, mobilePhone, enabled, DepartmentUpdate.withoutChange());
        }

        public @Nullable String displayName() {
            return displayName;
        }

        public @Nullable String email() {
            return email;
        }

        public @Nullable String mobilePhone() {
            return mobilePhone;
        }

        public @Nullable Boolean enabled() {
            return enabled;
        }

        public DepartmentUpdate departmentUpdate() {
            return departmentUpdate;
        }
    }

    public record DepartmentUpdate(boolean changing, @Nullable Long departmentId) {

        public static DepartmentUpdate changeTo(@Nullable Long departmentId) {
            return new DepartmentUpdate(true, departmentId);
        }

        public static DepartmentUpdate withoutChange() {
            return new DepartmentUpdate(false, null);
        }
    }

    public record ResetPasswordRequest(String newPassword) {}

    public record SaveUserRolesRequest(List<Long> roleIds) {}

    public record AuthenticationUserDto(
            Long id,
            String username,
            String displayName,
            @Nullable String email,
            @Nullable String mobilePhone,
            @Nullable Long departmentId,
            @Nullable String departmentCode,
            @Nullable String departmentName,
            boolean enabled,
            String createdAt) {
        static AuthenticationUserDto fromEntity(
                AuthenticationUser user, DepartmentDisplay department) {
            return new AuthenticationUserDto(
                    user.getId(),
                    user.getUsername(),
                    user.getDisplayName(),
                    user.getEmail(),
                    user.getMobilePhone(),
                    department.departmentId(),
                    department.departmentCode(),
                    department.departmentName(),
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
            @Nullable Long departmentId,
            @Nullable String departmentCode,
            @Nullable String departmentName,
            boolean enabled,
            String createdAt,
            List<RoleSummary> roles) {
        static AuthenticationUserDetailDto fromEntity(
                AuthenticationUser user, List<RoleSummary> roles, DepartmentDisplay department) {
            return new AuthenticationUserDetailDto(
                    user.getId(),
                    user.getUsername(),
                    user.getDisplayName(),
                    user.getEmail(),
                    user.getMobilePhone(),
                    department.departmentId(),
                    department.departmentCode(),
                    department.departmentName(),
                    user.isEnabled(),
                    user.getCreatedAt() != null ? user.getCreatedAt().toString() : "",
                    List.copyOf(roles));
        }
    }

    private record DepartmentDisplay(
            @Nullable Long departmentId,
            @Nullable String departmentCode,
            @Nullable String departmentName) {}

    public record RoleSummary(Long id, String roleName) {}
}
