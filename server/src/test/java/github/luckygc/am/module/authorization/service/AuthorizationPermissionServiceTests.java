package github.luckygc.am.module.authorization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.authorization.AuthorizationPermission;
import github.luckygc.am.module.authorization.AuthorizationRole;
import github.luckygc.am.module.authorization.AuthorizationRolePermissionRelation;
import github.luckygc.am.module.authorization.AuthorizationUserRoleRelation;
import github.luckygc.am.module.authorization.repository.AuthorizationPermissionDataRepository;
import github.luckygc.am.module.authorization.repository.AuthorizationRoleDataRepository;
import github.luckygc.am.module.authorization.repository.AuthorizationRolePermissionRelationDataRepository;
import github.luckygc.am.module.authorization.repository.AuthorizationUserRoleRelationDataRepository;

@DisplayName("功能权限服务")
class AuthorizationPermissionServiceTests {

    private AuthorizationPermissionDataRepository permissionRepository;
    private AuthorizationRoleDataRepository roleRepository;
    private AuthorizationRolePermissionRelationDataRepository rolePermissionRepository;
    private AuthorizationUserRoleRelationDataRepository userRoleRelationRepository;
    private AuthorizationPermissionService permissionService;

    @BeforeEach
    void setUp() {
        permissionRepository = mock(AuthorizationPermissionDataRepository.class);
        roleRepository = mock(AuthorizationRoleDataRepository.class);
        rolePermissionRepository = mock(AuthorizationRolePermissionRelationDataRepository.class);
        userRoleRelationRepository = mock(AuthorizationUserRoleRelationDataRepository.class);
        permissionService =
                new AuthorizationPermissionService(
                        permissionRepository,
                        roleRepository,
                        rolePermissionRepository,
                        userRoleRelationRepository);
    }

    @Test
    @DisplayName("枚举权限点时返回稳定目录")
    void listPermissionCatalogShouldExposeStableCodes() {
        List<AuthorizationPermissionService.PermissionDefinition> permissions =
                permissionService.listPermissionCatalog();

        assertThat(permissions)
                .extracting(AuthorizationPermissionService.PermissionDefinition::permissionCode)
                .contains(
                        "archive:item:read",
                        "archive:item:create",
                        "archive:item:update",
                        "archive:file:download",
                        "archive:export",
                        "archive:metadata:manage");
    }

    @Test
    @DisplayName("保存角色权限时拒绝未知权限编码")
    void saveRolePermissionsShouldRejectUnknownCode() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(enabledRole(1L)));

        assertThatThrownBy(
                        () ->
                                permissionService.saveRolePermissions(
                                        1L, List.of("archive:item:read", "unknown:permission")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("未知功能权限");
    }

    @Test
    @DisplayName("保存角色权限时覆盖已有绑定")
    void saveRolePermissionsShouldReplaceRelations() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(enabledRole(1L)));
        when(permissionRepository.findByPermissionCode("archive:item:read"))
                .thenReturn(permission(10L, "archive:item:read"));
        when(permissionRepository.findByPermissionCode("archive:item:update"))
                .thenReturn(permission(11L, "archive:item:update"));

        permissionService.saveRolePermissions(
                1L, List.of("archive:item:read", "archive:item:update"));

        verify(rolePermissionRepository).deleteByRoleId(1L);
        verify(rolePermissionRepository).insert(relation(1L, 10L));
        verify(rolePermissionRepository).insert(relation(1L, 11L));
    }

    @Test
    @DisplayName("计算用户权限时忽略禁用角色")
    void listUserPermissionCodesShouldIgnoreDisabledRoles() {
        when(userRoleRelationRepository.findByUserId(7L))
                .thenReturn(List.of(userRole(7L, 1L), userRole(7L, 2L)));
        when(roleRepository.findById(1L)).thenReturn(Optional.of(enabledRole(1L)));
        when(roleRepository.findById(2L)).thenReturn(Optional.of(disabledRole(2L)));
        when(rolePermissionRepository.findByRoleId(1L)).thenReturn(List.of(relation(1L, 10L)));
        when(permissionRepository.findById(10L))
                .thenReturn(Optional.of(permissionEntity(10L, "archive:item:read")));

        List<String> permissionCodes = permissionService.listUserPermissionCodes(7L);

        assertThat(permissionCodes).containsExactly("archive:item:read");
    }

    @Test
    @DisplayName("超级管理员拥有任意功能权限")
    void superAdminShouldHaveAnyPermission() {
        when(userRoleRelationRepository.findByUserId(7L)).thenReturn(List.of(userRole(7L, 1L)));
        AuthorizationRole role = enabledRole(1L);
        role.setRoleName(AuthorizationPermissionService.SUPER_ADMIN_ROLE_NAME);
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));

        assertThat(permissionService.isSuperAdmin(7L)).isTrue();
        assertThat(permissionService.hasPermission(7L, "custom:any-permission")).isTrue();
        assertThat(permissionService.listUserPermissionCodes(7L))
                .contains(
                        "archive:item:read",
                        "archive:item:create",
                        "archive:metadata:manage",
                        "archive:data-scope:manage");
    }

    private static AuthorizationPermission permissionEntity(Long id, String permissionCode) {
        AuthorizationPermission permission = new AuthorizationPermission();
        permission.setId(id);
        permission.setPermissionCode(permissionCode);
        permission.setPermissionName(permissionCode);
        permission.setModuleCode("archive");
        permission.setEnabled(true);
        return permission;
    }

    private static AuthorizationPermission permission(Long id, String permissionCode) {
        return permissionEntity(id, permissionCode);
    }

    private static AuthorizationRole enabledRole(Long id) {
        AuthorizationRole role = new AuthorizationRole();
        role.setId(id);
        role.setRoleName("角色" + id);
        role.setEnabled(true);
        return role;
    }

    private static AuthorizationRole disabledRole(Long id) {
        AuthorizationRole role = enabledRole(id);
        role.setEnabled(false);
        return role;
    }

    private static AuthorizationUserRoleRelation userRole(Long userId, Long roleId) {
        AuthorizationUserRoleRelation relation = new AuthorizationUserRoleRelation();
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        return relation;
    }

    private static AuthorizationRolePermissionRelation relation(Long roleId, Long permissionId) {
        AuthorizationRolePermissionRelation relation = new AuthorizationRolePermissionRelation();
        relation.setRoleId(roleId);
        relation.setPermissionId(permissionId);
        return relation;
    }
}
