package github.luckygc.am.module.authentication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.authentication.AuthenticationUser;
import github.luckygc.am.module.authentication.repository.AuthenticationUserDataRepository;
import github.luckygc.am.module.authentication.service.AuthenticationUserManagementService.AuthenticationUserDto;
import github.luckygc.am.module.authentication.service.AuthenticationUserManagementService.CreateAuthenticationUserRequest;
import github.luckygc.am.module.authentication.service.AuthenticationUserManagementService.ResetPasswordRequest;
import github.luckygc.am.module.authentication.service.AuthenticationUserManagementService.RoleSummary;
import github.luckygc.am.module.authentication.service.AuthenticationUserManagementService.SaveUserRolesRequest;
import github.luckygc.am.module.authentication.service.AuthenticationUserManagementService.UpdateAuthenticationUserRequest;
import github.luckygc.am.module.authorization.AuthorizationRole;
import github.luckygc.am.module.authorization.AuthorizationUserRoleRelation;
import github.luckygc.am.module.authorization.repository.AuthorizationRoleDataRepository;
import github.luckygc.am.module.authorization.repository.AuthorizationUserRoleRelationDataRepository;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;
import github.luckygc.am.module.organization.service.OrganizationDepartmentService;
import github.luckygc.am.module.organization.service.OrganizationDepartmentService.OrganizationDepartmentResponse;

@DisplayName("用户管理服务")
class AuthenticationUserManagementServiceTests {

    private AuthenticationUserDataRepository userRepository;
    private AuthorizationRoleDataRepository roleRepository;
    private AuthorizationUserRoleRelationDataRepository userRoleRelationRepository;
    private AuthorizationPermissionService permissionService;
    private OrganizationDepartmentService departmentService;
    private PasswordEncoder passwordEncoder;
    private AuthenticationUserManagementService userService;

    private static final Long OPERATOR_ID = 1L;

    @BeforeEach
    void setUp() {
        userRepository = mock(AuthenticationUserDataRepository.class);
        roleRepository = mock(AuthorizationRoleDataRepository.class);
        userRoleRelationRepository = mock(AuthorizationUserRoleRelationDataRepository.class);
        permissionService = mock(AuthorizationPermissionService.class);
        departmentService = mock(OrganizationDepartmentService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        userService =
                new AuthenticationUserManagementService(
                        userRepository,
                        roleRepository,
                        userRoleRelationRepository,
                        permissionService,
                        departmentService,
                        passwordEncoder);
    }

    // ── createUser ──

    @Test
    @DisplayName("创建用户成功并返回用户信息")
    void createUserShouldSucceed() {
        CreateAuthenticationUserRequest request =
                new CreateAuthenticationUserRequest(
                        "zhangsan", "secret123", "张三", "zhangsan@example.com", "13800138000");
        when(userRepository.findOptionalByUsername("zhangsan")).thenReturn(null);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-password");
        when(userRepository.insert(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthenticationUserDto result = userService.createUser(request, OPERATOR_ID);

        assertThat(result.id()).isNull(); // ID is assigned by DB, not set in the mock
        assertThat(result.username()).isEqualTo("zhangsan");
        assertThat(result.displayName()).isEqualTo("张三");
        assertThat(result.email()).isEqualTo("zhangsan@example.com");
        verify(userRepository).insert(any());
    }

    @Test
    @DisplayName("创建用户时用户名为空则拒绝")
    void createUserShouldRejectBlankUsername() {
        CreateAuthenticationUserRequest request =
                new CreateAuthenticationUserRequest("  ", "secret123", null, null, null);

        assertThatThrownBy(() -> userService.createUser(request, OPERATOR_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("用户名不能为空");

        verify(userRepository, never()).insert(any());
    }

    @Test
    @DisplayName("创建用户时密码为空则拒绝")
    void createUserShouldRejectBlankPassword() {
        CreateAuthenticationUserRequest request =
                new CreateAuthenticationUserRequest("zhangsan", "", null, null, null);

        assertThatThrownBy(() -> userService.createUser(request, OPERATOR_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("密码不能为空");

        verify(userRepository, never()).insert(any());
    }

    @Test
    @DisplayName("创建用户时用户名已存在则拒绝")
    void createUserShouldRejectDuplicateUsername() {
        CreateAuthenticationUserRequest request =
                new CreateAuthenticationUserRequest("zhangsan", "secret123", "张三", null, null);
        when(userRepository.findOptionalByUsername("zhangsan"))
                .thenReturn(userEntity(10L, "zhangsan"));

        assertThatThrownBy(() -> userService.createUser(request, OPERATOR_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("用户名已存在");

        verify(userRepository, never()).insert(any());
    }

    @Test
    @DisplayName("创建用户时 displayName 为空则拒绝")
    void createUserShouldRejectBlankDisplayName() {
        CreateAuthenticationUserRequest request =
                new CreateAuthenticationUserRequest("zhangsan", "secret123", null, null, null);
        when(userRepository.findOptionalByUsername("zhangsan")).thenReturn(null);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-password");
        when(userRepository.insert(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> userService.createUser(request, OPERATOR_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("显示名称不能为空");

        verify(userRepository, never()).insert(any());
    }

    @Test
    @DisplayName("创建用户时文本字段裁剪首尾空白")
    void createUserShouldTrimTextFields() {
        CreateAuthenticationUserRequest request =
                new CreateAuthenticationUserRequest(
                        " zhangsan ",
                        "secret123",
                        " 张三 ",
                        " zhangsan@example.com ",
                        " 13800138000 ");
        when(userRepository.findOptionalByUsername("zhangsan")).thenReturn(null);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-password");
        when(userRepository.insert(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthenticationUserDto result = userService.createUser(request, OPERATOR_ID);

        assertThat(result.username()).isEqualTo("zhangsan");
        assertThat(result.displayName()).isEqualTo("张三");
        assertThat(result.email()).isEqualTo("zhangsan@example.com");
        assertThat(result.mobilePhone()).isEqualTo("13800138000");
    }

    @Test
    @DisplayName("创建用户时密码已加密存储")
    void createUserShouldEncodePassword() {
        CreateAuthenticationUserRequest request =
                new CreateAuthenticationUserRequest("zhangsan", "secret123", "张三", null, null);
        when(userRepository.findOptionalByUsername("zhangsan")).thenReturn(null);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-password");
        when(userRepository.insert(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.createUser(request, OPERATOR_ID);

        verify(passwordEncoder).encode("secret123");
    }

    @Test
    @DisplayName("创建用户保存所属部门并返回部门展示字段")
    void createUserShouldSaveDepartment() {
        CreateAuthenticationUserRequest request =
                new CreateAuthenticationUserRequest("zhangsan", "secret123", "张三", null, null, 3L);
        when(userRepository.findOptionalByUsername("zhangsan")).thenReturn(null);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-password");
        when(departmentService.requireEnabledDepartment(3L)).thenReturn(department(3L, true));
        when(departmentService.getDepartment(3L)).thenReturn(department(3L, true));
        when(userRepository.insert(any()))
                .thenAnswer(
                        inv -> {
                            AuthenticationUser u = inv.getArgument(0);
                            u.setId(10L);
                            return u;
                        });

        AuthenticationUserDto result = userService.createUser(request, OPERATOR_ID);

        assertThat(result.departmentId()).isEqualTo(3L);
        assertThat(result.departmentCode()).isEqualTo("D003");
        assertThat(result.departmentName()).isEqualTo("档案部");
        verify(departmentService).requireEnabledDepartment(3L);
    }

    @Test
    @DisplayName("创建用户拒绝停用部门")
    void createUserShouldRejectDisabledDepartment() {
        CreateAuthenticationUserRequest request =
                new CreateAuthenticationUserRequest("zhangsan", "secret123", "张三", null, null, 3L);
        when(userRepository.findOptionalByUsername("zhangsan")).thenReturn(null);
        doThrow(new BadRequestException("部门已停用", "departmentId", "部门已停用"))
                .when(departmentService)
                .requireEnabledDepartment(3L);

        assertThatThrownBy(() -> userService.createUser(request, OPERATOR_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("部门已停用");

        verify(userRepository, never()).insert(any());
    }

    // ── updateUser ──

    @Test
    @DisplayName("更新用户时用户不存在则拒绝")
    void updateUserShouldRejectIfNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                userService.updateUser(
                                        99L,
                                        new UpdateAuthenticationUserRequest(null, null, null, null),
                                        OPERATOR_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("用户不存在");
    }

    @Test
    @DisplayName("更新用户 displayName 为空时拒绝")
    void updateUserShouldRejectBlankDisplayName() {
        AuthenticationUser user = userEntity(10L, "zhangsan");
        user.setDisplayName("张三");
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(userRepository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(
                        () ->
                                userService.updateUser(
                                        10L,
                                        new UpdateAuthenticationUserRequest("", null, null, null),
                                        OPERATOR_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("显示名称不能为空");

        verify(userRepository, never()).update(any());
    }

    @Test
    @DisplayName("更新用户支持部分字段修改")
    void updateUserShouldSupportPartialUpdate() {
        AuthenticationUser user = userEntity(10L, "zhangsan");
        user.setDisplayName("张三");
        user.setEmail("old@example.com");
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(userRepository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthenticationUserDto result =
                userService.updateUser(
                        10L,
                        new UpdateAuthenticationUserRequest(null, "new@example.com", null, null),
                        OPERATOR_ID);

        assertThat(result.email()).isEqualTo("new@example.com");
        assertThat(result.displayName()).isEqualTo("张三"); // unchanged
    }

    @Test
    @DisplayName("更新用户时空白可选文本字段归一化为 null")
    void updateUserShouldNormalizeBlankOptionalTextFieldsToNull() {
        AuthenticationUser user = userEntity(10L, "zhangsan");
        user.setDisplayName("张三");
        user.setEmail("old@example.com");
        user.setMobilePhone("13800138000");
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(userRepository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthenticationUserDto result =
                userService.updateUser(
                        10L,
                        new UpdateAuthenticationUserRequest(null, "", "   ", null),
                        OPERATOR_ID);

        assertThat(result.email()).isNull();
        assertThat(result.mobilePhone()).isNull();
    }

    @Test
    @DisplayName("更新用户 enabled 字段")
    void updateUserShouldUpdateEnabled() {
        AuthenticationUser user = userEntity(10L, "zhangsan");
        user.setEnabled(true);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(userRepository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthenticationUserDto result =
                userService.updateUser(
                        10L,
                        new UpdateAuthenticationUserRequest(null, null, null, false),
                        OPERATOR_ID);

        assertThat(result.enabled()).isFalse();
    }

    @Test
    @DisplayName("更新用户可以设置所属部门")
    void updateUserShouldSetDepartment() {
        AuthenticationUser user = userEntity(10L, "zhangsan");
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(departmentService.requireEnabledDepartment(3L)).thenReturn(department(3L, true));
        when(departmentService.getDepartment(3L)).thenReturn(department(3L, true));
        when(userRepository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthenticationUserDto result =
                userService.updateUser(
                        10L,
                        new UpdateAuthenticationUserRequest(null, null, null, null, 3L),
                        OPERATOR_ID);

        assertThat(result.departmentId()).isEqualTo(3L);
        assertThat(result.departmentCode()).isEqualTo("D003");
        verify(departmentService).requireEnabledDepartment(3L);
    }

    @Test
    @DisplayName("更新用户显式清空所属部门")
    void updateUserShouldClearDepartment() {
        AuthenticationUser user = userEntity(10L, "zhangsan");
        user.setDepartmentId(3L);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(userRepository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthenticationUserDto result =
                userService.updateUser(
                        10L,
                        new UpdateAuthenticationUserRequest(null, null, null, null, null),
                        OPERATOR_ID);

        assertThat(result.departmentId()).isNull();
        assertThat(result.departmentCode()).isNull();
        assertThat(result.departmentName()).isNull();
    }

    // ── resetPassword ──

    @Test
    @DisplayName("重置密码时新密码为空则拒绝")
    void resetPasswordShouldRejectBlankPassword() {
        assertThatThrownBy(
                        () ->
                                userService.resetPassword(
                                        10L, new ResetPasswordRequest(""), OPERATOR_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("新密码不能为空");

        verify(userRepository, never()).update(any());
    }

    @Test
    @DisplayName("重置密码时用户不存在则拒绝")
    void resetPasswordShouldRejectIfUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                userService.resetPassword(
                                        99L, new ResetPasswordRequest("new-secret"), OPERATOR_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("用户不存在");
    }

    @Test
    @DisplayName("重置密码成功更新已加密密码")
    void resetPasswordShouldEncodeNewPassword() {
        AuthenticationUser user = userEntity(10L, "zhangsan");
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("new-secret")).thenReturn("encoded-new");

        userService.resetPassword(10L, new ResetPasswordRequest("new-secret"), OPERATOR_ID);

        verify(passwordEncoder).encode("new-secret");
        verify(userRepository).update(any());
    }

    // ── saveUserRoles ──

    @Test
    @DisplayName("分配角色时用户不存在则拒绝")
    void saveUserRolesShouldRejectIfUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                userService.saveUserRoles(
                                        99L, new SaveUserRolesRequest(List.of(1L)), OPERATOR_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("用户不存在");
    }

    @Test
    @DisplayName("分配角色时角色不存在则拒绝")
    void saveUserRolesShouldRejectIfRoleNotFound() {
        AuthenticationUser user = userEntity(10L, "zhangsan");
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(roleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                userService.saveUserRoles(
                                        10L, new SaveUserRolesRequest(List.of(99L)), OPERATOR_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("角色不存在");
    }

    @Test
    @DisplayName("分配角色时角色已停用则拒绝")
    void saveUserRolesShouldRejectIfRoleDisabled() {
        AuthenticationUser user = userEntity(10L, "zhangsan");
        AuthorizationRole role = disabledRole(1L, "审核员");
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));

        assertThatThrownBy(
                        () ->
                                userService.saveUserRoles(
                                        10L, new SaveUserRolesRequest(List.of(1L)), OPERATOR_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("角色已停用");
    }

    @Test
    @DisplayName("分配角色成功覆盖已有绑定")
    void saveUserRolesShouldReplaceExistingBindings() {
        AuthenticationUser user = userEntity(10L, "zhangsan");
        AuthorizationRole role = enabledRole(1L, "管理员");
        AuthorizationUserRoleRelation relation = new AuthorizationUserRoleRelation();
        relation.setUserId(10L);
        relation.setRoleId(1L);

        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));
        when(userRoleRelationRepository.findByUserId(10L)).thenReturn(List.of(relation));
        when(roleRepository.findByIdIn(List.of(1L))).thenReturn(List.of(role));

        List<RoleSummary> result =
                userService.saveUserRoles(10L, new SaveUserRolesRequest(List.of(1L)), OPERATOR_ID);

        verify(userRoleRelationRepository).deleteByUserId(10L);
        verify(userRoleRelationRepository).insert(any());
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().roleName()).isEqualTo("管理员");
    }

    // ── listUserRoles ──

    @Test
    @DisplayName("查询用户角色时用户不存在则拒绝")
    void listUserRolesShouldRejectIfUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.listUserRoles(99L, OPERATOR_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("用户不存在");
    }

    // ── 权限校验 ──

    @Test
    @DisplayName("createUser 需要 authentication:user:manage 权限")
    void createUserShouldRequirePermission() {
        doThrowPermissionDenied();
        CreateAuthenticationUserRequest request =
                new CreateAuthenticationUserRequest("test", "pass", null, null, null);

        assertThatThrownBy(() -> userService.createUser(request, OPERATOR_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("权限不足");
    }

    @Test
    @DisplayName("saveUserRoles 需要 authentication:user:manage 权限")
    void saveUserRolesShouldRequirePermission() {
        doThrowPermissionDenied();

        assertThatThrownBy(
                        () ->
                                userService.saveUserRoles(
                                        10L, new SaveUserRolesRequest(List.of(1L)), OPERATOR_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("权限不足");
    }

    // ── 辅助方法 ──

    private void doThrowPermissionDenied() {
        doThrow(new BadRequestException("权限不足", null, "权限不足"))
                .when(permissionService)
                .requirePermission(
                        OPERATOR_ID, AuthorizationPermissionCode.AUTHENTICATION_USER_MANAGE);
    }

    private static AuthenticationUser userEntity(Long id, String username) {
        AuthenticationUser user = new AuthenticationUser();
        user.setId(id);
        user.setUsername(username);
        user.setPassword("encoded");
        user.setDisplayName(username);
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        user.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return user;
    }

    private static AuthorizationRole enabledRole(Long id, String roleName) {
        AuthorizationRole role = new AuthorizationRole();
        role.setId(id);
        role.setRoleName(roleName);
        role.setEnabled(true);
        return role;
    }

    private static AuthorizationRole disabledRole(Long id, String roleName) {
        AuthorizationRole role = enabledRole(id, roleName);
        role.setEnabled(false);
        return role;
    }

    private static OrganizationDepartmentResponse department(Long id, boolean enabled) {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        return new OrganizationDepartmentResponse(id, "D003", "档案部", null, enabled, 0, now, now);
    }
}
