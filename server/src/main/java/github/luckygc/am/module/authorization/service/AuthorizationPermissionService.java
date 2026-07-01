package github.luckygc.am.module.authorization.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.TreeMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.authorization.AuthorizationPermission;
import github.luckygc.am.module.authorization.AuthorizationRole;
import github.luckygc.am.module.authorization.AuthorizationRolePermissionRelation;
import github.luckygc.am.module.authorization.AuthorizationUserRoleRelation;
import github.luckygc.am.module.authorization.repository.AuthorizationPermissionDataRepository;
import github.luckygc.am.module.authorization.repository.AuthorizationRoleDataRepository;
import github.luckygc.am.module.authorization.repository.AuthorizationRolePermissionRelationDataRepository;
import github.luckygc.am.module.authorization.repository.AuthorizationUserRoleRelationDataRepository;

@Service
public class AuthorizationPermissionService {

    public static final String SUPER_ADMIN_ROLE_NAME = "超级管理员";

    private static final List<PermissionDefinition> PERMISSION_CATALOG =
            List.of(
                    new PermissionDefinition(
                            "archive:item:read", "读取档案", "archive", "读取档案列表、详情和全文发现结果"),
                    new PermissionDefinition("archive:item:create", "创建档案", "archive", "创建档案条目和案卷"),
                    new PermissionDefinition("archive:item:update", "修改档案", "archive", "修改档案条目和案卷"),
                    new PermissionDefinition(
                            "archive:item:delete", "删除档案", "archive", "逻辑删除档案条目和案卷"),
                    new PermissionDefinition("archive:item:lock", "锁定档案", "archive", "锁定和解锁档案条目"),
                    new PermissionDefinition(
                            "archive:file:bind", "绑定档案文件", "archive", "绑定和解绑档案电子文件"),
                    new PermissionDefinition(
                            "archive:file:download", "下载档案文件", "archive", "下载档案电子文件"),
                    new PermissionDefinition("archive:audit:read", "查询档案审计", "archive", "查询档案操作审计"),
                    new PermissionDefinition("archive:export", "导出档案", "archive", "按查询条件导出档案数据"),
                    new PermissionDefinition(
                            "archive:metadata:manage",
                            "管理档案元数据",
                            "archive",
                            "维护全宗、分类、字段、布局、密级和保管期限"),
                    new PermissionDefinition(
                            "authorization:permission:manage",
                            "管理功能权限",
                            "authorization",
                            "查看权限点并维护角色功能权限"),
                    new PermissionDefinition(
                            "archive:data-scope:manage", "管理档案数据范围", "archive", "维护档案数据范围和主体范围绑定"));

    private final AuthorizationPermissionDataRepository permissionRepository;
    private final AuthorizationRoleDataRepository roleRepository;
    private final AuthorizationRolePermissionRelationDataRepository rolePermissionRepository;
    private final AuthorizationUserRoleRelationDataRepository userRoleRelationRepository;

    public AuthorizationPermissionService(
            AuthorizationPermissionDataRepository permissionRepository,
            AuthorizationRoleDataRepository roleRepository,
            AuthorizationRolePermissionRelationDataRepository rolePermissionRepository,
            AuthorizationUserRoleRelationDataRepository userRoleRelationRepository) {
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.userRoleRelationRepository = userRoleRelationRepository;
    }

    public List<PermissionDefinition> listPermissionCatalog() {
        return PERMISSION_CATALOG;
    }

    @Transactional
    public void saveRolePermissions(Long roleId, List<String> permissionCodes) {
        AuthorizationRole role =
                roleRepository
                        .findById(roleId)
                        .orElseThrow(() -> new BadRequestException("角色不存在", "roleId", "角色不存在"));
        if (!role.isEnabled()) {
            throw new BadRequestException("角色已停用", "roleId", "角色已停用");
        }
        SequencedMap<String, AuthorizationPermission> permissionsByCode = new TreeMap<>();
        for (String permissionCode : permissionCodes) {
            AuthorizationPermission permission =
                    Optional.ofNullable(permissionRepository.findByPermissionCode(permissionCode))
                            .filter(AuthorizationPermission::isEnabled)
                            .orElseThrow(
                                    () ->
                                            new BadRequestException(
                                                    "未知功能权限",
                                                    "permissionCodes",
                                                    "未知功能权限: " + permissionCode));
            permissionsByCode.put(permission.getPermissionCode(), permission);
        }
        rolePermissionRepository.deleteByRoleId(roleId);
        for (AuthorizationPermission permission : permissionsByCode.values()) {
            AuthorizationRolePermissionRelation relation =
                    new AuthorizationRolePermissionRelation();
            relation.setRoleId(roleId);
            relation.setPermissionId(permission.getId());
            rolePermissionRepository.insert(relation);
        }
    }

    @Transactional(readOnly = true)
    public List<String> listRolePermissionCodes(Long roleId) {
        AuthorizationRole role =
                roleRepository
                        .findById(roleId)
                        .orElseThrow(() -> new BadRequestException("角色不存在", "roleId", "角色不存在"));
        if (!role.isEnabled()) {
            return List.of();
        }
        return rolePermissionRepository.findByRoleId(roleId).stream()
                .map(AuthorizationRolePermissionRelation::getPermissionId)
                .map(permissionRepository::findById)
                .flatMap(Optional::stream)
                .filter(AuthorizationPermission::isEnabled)
                .map(AuthorizationPermission::getPermissionCode)
                .distinct()
                .sorted()
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> listUserPermissionCodes(Long userId) {
        if (isSuperAdmin(userId)) {
            return PERMISSION_CATALOG.stream()
                    .map(PermissionDefinition::permissionCode)
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }
        Map<String, String> permissionCodes = new TreeMap<>();
        for (AuthorizationUserRoleRelation userRole :
                userRoleRelationRepository.findByUserId(userId)) {
            AuthorizationRole role = roleRepository.findById(userRole.getRoleId()).orElse(null);
            if (role == null || !role.isEnabled()) {
                continue;
            }
            for (AuthorizationRolePermissionRelation relation :
                    rolePermissionRepository.findByRoleId(role.getId())) {
                permissionRepository
                        .findById(relation.getPermissionId())
                        .filter(AuthorizationPermission::isEnabled)
                        .ifPresent(
                                permission ->
                                        permissionCodes.put(
                                                permission.getPermissionCode(),
                                                permission.getPermissionCode()));
            }
        }
        return permissionCodes.values().stream().sorted(Comparator.naturalOrder()).toList();
    }

    @Transactional(readOnly = true)
    public boolean isSuperAdmin(Long userId) {
        for (AuthorizationUserRoleRelation userRole :
                userRoleRelationRepository.findByUserId(userId)) {
            AuthorizationRole role = roleRepository.findById(userRole.getRoleId()).orElse(null);
            if (role != null
                    && role.isEnabled()
                    && SUPER_ADMIN_ROLE_NAME.equals(role.getRoleName())) {
                return true;
            }
        }
        return false;
    }

    public boolean hasPermission(Long userId, String permissionCode) {
        return isSuperAdmin(userId) || listUserPermissionCodes(userId).contains(permissionCode);
    }

    public void requirePermission(Long userId, AuthorizationPermissionCode permissionCode) {
        userId = AuthenticatedUsers.requireResolvedUserId(userId);
        if (!hasPermission(userId, permissionCode.code())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "权限不足");
        }
    }

    public record PermissionDefinition(
            String permissionCode, String permissionName, String moduleCode, String description) {}
}
