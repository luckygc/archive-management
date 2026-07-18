package github.luckygc.am.module.authorization.service;

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

import jakarta.data.page.CursoredPage;
import jakarta.data.page.PageRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.authorization.AuthorizationRole;
import github.luckygc.am.module.authorization.AuthorizationUserRoleRelation;
import github.luckygc.am.module.authorization.repository.AuthorizationRoleDataRepository;
import github.luckygc.am.module.authorization.repository.AuthorizationRolePermissionRelationDataRepository;
import github.luckygc.am.module.authorization.repository.AuthorizationUserRoleRelationDataRepository;
import github.luckygc.am.module.authorization.service.AuthorizationRoleManagementService.AuthorizationRoleDto;
import github.luckygc.am.module.authorization.service.AuthorizationRoleManagementService.CreateAuthorizationRoleRequest;
import github.luckygc.am.module.authorization.service.AuthorizationRoleManagementService.UpdateAuthorizationRoleRequest;

@DisplayName("角色管理服务")
class AuthorizationRoleManagementServiceTests {

    private AuthorizationRoleDataRepository roleRepository;
    private AuthorizationRolePermissionRelationDataRepository rolePermissionRepository;
    private AuthorizationUserRoleRelationDataRepository userRoleRelationRepository;
    private AuthorizationPermissionService permissionService;
    private AuthorizationRoleManagementService roleService;

    private static final Long OPERATOR_ID = 1L;

    @BeforeEach
    void setUp() {
        roleRepository = mock(AuthorizationRoleDataRepository.class);
        rolePermissionRepository = mock(AuthorizationRolePermissionRelationDataRepository.class);
        userRoleRelationRepository = mock(AuthorizationUserRoleRelationDataRepository.class);
        permissionService = mock(AuthorizationPermissionService.class);
        roleService =
                new AuthorizationRoleManagementService(
                        roleRepository,
                        rolePermissionRepository,
                        userRoleRelationRepository,
                        permissionService);
    }

    // ── createRole ──

    @Test
    @DisplayName("创建角色成功并返回角色信息")
    void createRoleShouldSucceed() {
        CreateAuthorizationRoleRequest request =
                new CreateAuthorizationRoleRequest("档案管理员", "负责档案日常管理");
        when(roleRepository.findOptionalByRoleName("档案管理员")).thenReturn(null);
        when(roleRepository.insert(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthorizationRoleDto result = roleService.createRole(request, OPERATOR_ID);

        assertThat(result.roleName()).isEqualTo("档案管理员");
        assertThat(result.description()).isEqualTo("负责档案日常管理");
        assertThat(result.enabled()).isTrue();
        verify(roleRepository).insert(any());
    }

    @Test
    @DisplayName("创建角色时角色名称为空则拒绝")
    void createRoleShouldRejectBlankName() {
        CreateAuthorizationRoleRequest request = new CreateAuthorizationRoleRequest("  ", null);

        assertThatThrownBy(() -> roleService.createRole(request, OPERATOR_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("角色名称不能为空");

        verify(roleRepository, never()).insert(any());
    }

    @Test
    @DisplayName("创建角色时角色名称已存在则拒绝")
    void createRoleShouldRejectDuplicateName() {
        CreateAuthorizationRoleRequest request = new CreateAuthorizationRoleRequest("档案管理员", null);
        when(roleRepository.findOptionalByRoleName("档案管理员")).thenReturn(roleEntity(10L, "档案管理员"));

        assertThatThrownBy(() -> roleService.createRole(request, OPERATOR_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("角色名称已存在");

        verify(roleRepository, never()).insert(any());
    }

    @Test
    @DisplayName("创建角色时 description 为空则设为 null")
    void createRoleShouldSetNullDescriptionWhenBlank() {
        CreateAuthorizationRoleRequest request = new CreateAuthorizationRoleRequest("档案管理员", "");
        when(roleRepository.findOptionalByRoleName("档案管理员")).thenReturn(null);
        when(roleRepository.insert(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthorizationRoleDto result = roleService.createRole(request, OPERATOR_ID);

        assertThat(result.description()).isNull();
    }

    // ── updateRole ──

    @Test
    @DisplayName("更新角色时角色不存在则拒绝")
    void updateRoleShouldRejectIfNotFound() {
        when(roleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                roleService.updateRole(
                                        99L,
                                        new UpdateAuthorizationRoleRequest(null, null, null),
                                        OPERATOR_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("角色不存在");
    }

    @Test
    @DisplayName("更新角色名称时新名称为空则拒绝")
    void updateRoleShouldRejectBlankName() {
        AuthorizationRole role = roleEntity(10L, "档案管理员");
        when(roleRepository.findById(10L)).thenReturn(Optional.of(role));

        assertThatThrownBy(
                        () ->
                                roleService.updateRole(
                                        10L,
                                        new UpdateAuthorizationRoleRequest("  ", null, null),
                                        OPERATOR_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("角色名称不能为空");
    }

    @Test
    @DisplayName("更新角色名称时新名称与其他角色重复则拒绝")
    void updateRoleShouldRejectNameConflictWithOtherRole() {
        AuthorizationRole role = roleEntity(10L, "档案管理员");
        when(roleRepository.findById(10L)).thenReturn(Optional.of(role));
        when(roleRepository.findOptionalByRoleName("超级管理员")).thenReturn(roleEntity(20L, "超级管理员"));

        assertThatThrownBy(
                        () ->
                                roleService.updateRole(
                                        10L,
                                        new UpdateAuthorizationRoleRequest("超级管理员", null, null),
                                        OPERATOR_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("角色名称已存在");
    }

    @Test
    @DisplayName("更新角色名称时允许同名（同一角色）")
    void updateRoleShouldAllowSameNameForSameRole() {
        AuthorizationRole role = roleEntity(10L, "档案管理员");
        when(roleRepository.findById(10L)).thenReturn(Optional.of(role));
        when(roleRepository.findOptionalByRoleName("档案管理员")).thenReturn(role);
        when(roleRepository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthorizationRoleDto result =
                roleService.updateRole(
                        10L, new UpdateAuthorizationRoleRequest("档案管理员", null, null), OPERATOR_ID);

        assertThat(result.roleName()).isEqualTo("档案管理员");
    }

    @Test
    @DisplayName("更新角色支持部分字段修改")
    void updateRoleShouldSupportPartialUpdate() {
        AuthorizationRole role = roleEntity(10L, "档案管理员");
        role.setDescription("旧说明");
        when(roleRepository.findById(10L)).thenReturn(Optional.of(role));
        when(roleRepository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthorizationRoleDto result =
                roleService.updateRole(
                        10L, new UpdateAuthorizationRoleRequest(null, "新说明", null), OPERATOR_ID);

        assertThat(result.description()).isEqualTo("新说明");
        assertThat(result.roleName()).isEqualTo("档案管理员"); // unchanged
    }

    @Test
    @DisplayName("更新角色时 description 为空字符串则设为 null")
    void updateRoleShouldClearDescriptionWhenBlank() {
        AuthorizationRole role = roleEntity(10L, "档案管理员");
        role.setDescription("旧说明");
        when(roleRepository.findById(10L)).thenReturn(Optional.of(role));
        when(roleRepository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthorizationRoleDto result =
                roleService.updateRole(
                        10L, new UpdateAuthorizationRoleRequest(null, "", null), OPERATOR_ID);

        assertThat(result.description()).isNull();
    }

    @Test
    @DisplayName("更新角色 enabled 字段")
    void updateRoleShouldUpdateEnabled() {
        AuthorizationRole role = roleEntity(10L, "档案管理员");
        role.setEnabled(true);
        when(roleRepository.findById(10L)).thenReturn(Optional.of(role));
        when(roleRepository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthorizationRoleDto result =
                roleService.updateRole(
                        10L, new UpdateAuthorizationRoleRequest(null, null, false), OPERATOR_ID);

        assertThat(result.enabled()).isFalse();
    }

    @Test
    @DisplayName("禁止改名内置超级管理员角色")
    void updateRoleShouldRejectRenamingSuperAdminRole() {
        AuthorizationRole role =
                roleEntity(1L, AuthorizationPermissionService.SUPER_ADMIN_ROLE_NAME);
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));

        assertThatThrownBy(
                        () ->
                                roleService.updateRole(
                                        1L,
                                        new UpdateAuthorizationRoleRequest("管理员", null, null),
                                        OPERATOR_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("禁止修改超级管理员角色");

        verify(roleRepository, never()).update(any());
    }

    @Test
    @DisplayName("禁止停用内置超级管理员角色")
    void updateRoleShouldRejectDisablingSuperAdminRole() {
        AuthorizationRole role =
                roleEntity(1L, AuthorizationPermissionService.SUPER_ADMIN_ROLE_NAME);
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));

        assertThatThrownBy(
                        () ->
                                roleService.updateRole(
                                        1L,
                                        new UpdateAuthorizationRoleRequest(null, null, false),
                                        OPERATOR_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("禁止修改超级管理员角色");

        verify(roleRepository, never()).update(any());
    }

    // ── deleteRole ──

    @Test
    @DisplayName("删除角色时角色不存在则拒绝")
    void deleteRoleShouldRejectIfNotFound() {
        when(roleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.deleteRole(99L, OPERATOR_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("角色不存在");
    }

    @Test
    @DisplayName("删除超级管理员角色时拒绝")
    void deleteRoleShouldRejectSuperAdmin() {
        AuthorizationRole role = roleEntity(1L, "超级管理员");
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));

        assertThatThrownBy(() -> roleService.deleteRole(1L, OPERATOR_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("禁止删除超级管理员角色");

        verify(roleRepository, never()).delete(any());
    }

    @Test
    @DisplayName("删除普通角色成功并清理关联数据")
    void deleteRoleShouldCleanupRelations() {
        AuthorizationRole role = roleEntity(10L, "档案管理员");
        AuthorizationUserRoleRelation urRelation = new AuthorizationUserRoleRelation();
        urRelation.setUserId(7L);
        urRelation.setRoleId(10L);

        when(roleRepository.findById(10L)).thenReturn(Optional.of(role));
        when(userRoleRelationRepository.findByRoleId(10L)).thenReturn(List.of(urRelation));

        roleService.deleteRole(10L, OPERATOR_ID);

        verify(userRoleRelationRepository).delete(urRelation);
        verify(rolePermissionRepository).deleteByRoleId(10L);
        verify(roleRepository).delete(role);
    }

    // ── getRole ──

    @Test
    @DisplayName("查询角色时角色不存在则拒绝")
    void getRoleShouldRejectIfNotFound() {
        when(roleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.getRole(99L, OPERATOR_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("角色不存在");
    }

    // ── 权限校验 ──

    @Test
    @DisplayName("角色、用户、功能权限和数据范围管理员可读取角色目录")
    void listRolesShouldAllowRequiredDirectoryPermissions() {
        @SuppressWarnings("unchecked")
        CursoredPage<AuthorizationRole> page = mock(CursoredPage.class);
        when(page.content()).thenReturn(List.of());
        when(page.numberOfElements()).thenReturn(0);
        when(page.hasTotals()).thenReturn(false);
        when(roleRepository.filterBy(any(), any())).thenReturn(page);
        doThrowPermissionDenied();

        for (AuthorizationPermissionCode permissionCode :
                List.of(
                        AuthorizationPermissionCode.AUTHORIZATION_ROLE_MANAGE,
                        AuthorizationPermissionCode.AUTHENTICATION_USER_MANAGE,
                        AuthorizationPermissionCode.AUTHORIZATION_PERMISSION_MANAGE,
                        AuthorizationPermissionCode.ARCHIVE_DATA_SCOPE_MANAGE)) {
            when(permissionService.hasPermission(OPERATOR_ID, permissionCode.code()))
                    .thenReturn(true);

            roleService.listRoles(true, PageRequest.ofSize(100), OPERATOR_ID);

            when(permissionService.hasPermission(OPERATOR_ID, permissionCode.code()))
                    .thenReturn(false);
        }
    }

    @Test
    @DisplayName("没有相关管理权限时拒绝读取角色目录")
    void listRolesShouldRejectWithoutRequiredDirectoryPermission() {
        assertThatThrownBy(() -> roleService.listRoles(true, PageRequest.ofSize(100), OPERATOR_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("权限不足");

        verify(roleRepository, never()).filterBy(any(), any());
    }

    @Test
    @DisplayName("createRole 需要 authorization:role:manage 权限")
    void createRoleShouldRequirePermission() {
        doThrowPermissionDenied();
        CreateAuthorizationRoleRequest request = new CreateAuthorizationRoleRequest("test", null);

        assertThatThrownBy(() -> roleService.createRole(request, OPERATOR_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("权限不足");
    }

    @Test
    @DisplayName("deleteRole 需要 authorization:role:manage 权限")
    void deleteRoleShouldRequirePermission() {
        doThrowPermissionDenied();

        assertThatThrownBy(() -> roleService.deleteRole(10L, OPERATOR_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("权限不足");
    }

    @Test
    @DisplayName("updateRole 需要 authorization:role:manage 权限")
    void updateRoleShouldRequirePermission() {
        doThrowPermissionDenied();

        assertThatThrownBy(
                        () ->
                                roleService.updateRole(
                                        10L,
                                        new UpdateAuthorizationRoleRequest(null, null, null),
                                        OPERATOR_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("权限不足");
    }

    // ── 辅助方法 ──

    private void doThrowPermissionDenied() {
        doThrow(new BadRequestException("权限不足", null, "权限不足"))
                .when(permissionService)
                .requirePermission(
                        OPERATOR_ID, AuthorizationPermissionCode.AUTHORIZATION_ROLE_MANAGE);
    }

    private static AuthorizationRole roleEntity(Long id, String roleName) {
        AuthorizationRole role = new AuthorizationRole();
        role.setId(id);
        role.setRoleName(roleName);
        role.setDescription(null);
        role.setEnabled(true);
        role.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        role.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return role;
    }
}
