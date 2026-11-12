package github.luckygc.am.module.authorization.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.TreeMap;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.common.security.AccessTokenGrant;
import github.luckygc.am.common.security.AccessTokenGrantHolder;
import github.luckygc.am.common.security.AccessTokenSubjectType;
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
                            "archive:item:download-electronic-file",
                            "下载档案电子文件",
                            "archive",
                            "下载档案电子文件或创建下载短链"),
                    new PermissionDefinition("archive:export", "导出档案", "archive", "按查询条件导出档案数据"),
                    new PermissionDefinition(
                            "archive:metadata:manage",
                            "管理档案元数据",
                            "archive",
                            "维护全宗、分类、字段、布局、密级和保管期限"),
                    new PermissionDefinition(
                            "archive:rule:manage", "管理档案规则", "archive", "维护运行时规则和约束"),
                    new PermissionDefinition(
                            "authorization:permission:manage",
                            "管理功能权限",
                            "authorization",
                            "查看权限点并维护角色功能权限"),
                    new PermissionDefinition(
                            "authentication:session:manage",
                            "管理登录会话",
                            "authentication",
                            "查看登录会话并踢出其他会话"),
                    new PermissionDefinition(
                            "authentication:audit:read",
                            "查询认证审计",
                            "authentication",
                            "查询登录、退出和踢下线审计事件"),
                    new PermissionDefinition(
                            "archive:data-scope:manage", "管理档案数据范围", "archive", "维护档案数据范围和主体范围绑定"),
                    new PermissionDefinition(
                            "authentication:user:manage", "管理用户", "authentication", "创建、编辑用户和分配角色"),
                    new PermissionDefinition(
                            "authentication:service-account:manage",
                            "管理服务账号",
                            "authentication",
                            "创建、停用和删除服务账号并绑定角色"),
                    new PermissionDefinition(
                            "authentication:access-token:manage",
                            "管理访问令牌",
                            "authentication",
                            "为服务账号签发和吊销访问令牌"),
                    new PermissionDefinition(
                            "authentication:api-call:read",
                            "查询调用审计",
                            "authentication",
                            "查询访问令牌调用审计"),
                    new PermissionDefinition(
                            "authorization:role:manage", "管理角色", "authorization", "创建、编辑和删除角色"),
                    new PermissionDefinition(
                            "organization:department:manage",
                            "管理组织架构",
                            "organization",
                            "维护组织架构部门树"),
                    new PermissionDefinition(
                            "approval:definition:manage",
                            "管理审批流定义",
                            "approval",
                            "创建、修改、停用和发布审批流定义"),
                    new PermissionDefinition(
                            "approval:instance:start", "发起审批流程", "approval", "使用已发布审批流定义发起流程实例"),
                    new PermissionDefinition(
                            "approval:instance:manage", "管理审批流程实例", "approval", "查询和终止审批流程实例"));

    private final AuthorizationPermissionDataRepository permissionRepository;
    private final AuthorizationRoleDataRepository roleRepository;
    private final AuthorizationRolePermissionRelationDataRepository rolePermissionRepository;
    private final AuthorizationUserRoleRelationDataRepository userRoleRelationRepository;
    private final AuthorizationServiceAccountRoleService serviceAccountRoleService;

    public AuthorizationPermissionService(
            AuthorizationPermissionDataRepository permissionRepository,
            AuthorizationRoleDataRepository roleRepository,
            AuthorizationRolePermissionRelationDataRepository rolePermissionRepository,
            AuthorizationUserRoleRelationDataRepository userRoleRelationRepository,
            AuthorizationServiceAccountRoleService serviceAccountRoleService) {
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.userRoleRelationRepository = userRoleRelationRepository;
        this.serviceAccountRoleService = serviceAccountRoleService;
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
        AccessTokenGrant grant = AccessTokenGrantHolder.current();
        if (grant != null && grant.subjectType() == AccessTokenSubjectType.SERVICE_ACCOUNT) {
            return applyGrantScopes(
                    listServiceAccountPermissionCodesInternal(grant.subjectId()), grant);
        }
        return applyGrantScopes(listUserPermissionCodesInternal(userId), grant);
    }

    @Transactional(readOnly = true)
    public List<String> listServiceAccountPermissionCodes(Long serviceAccountId) {
        return listServiceAccountPermissionCodesInternal(serviceAccountId);
    }

    private List<String> listUserPermissionCodesInternal(Long userId) {
        if (isSuperAdminInternal(userId)) {
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
        AccessTokenGrant grant = AccessTokenGrantHolder.current();
        boolean superAdmin =
                grant != null && grant.subjectType() == AccessTokenSubjectType.SERVICE_ACCOUNT
                        ? isServiceAccountSuperAdmin(grant.subjectId())
                        : isSuperAdminInternal(userId);
        if (grant == null) {
            return superAdmin;
        }
        return superAdmin && grant.scopes().containsAll(catalogCodes());
    }

    private boolean isSuperAdminInternal(Long userId) {
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
        return hasPermissionInternal(userId, permissionCode);
    }

    private boolean hasPermissionInternal(Long userId, String permissionCode) {
        AccessTokenGrant grant = AccessTokenGrantHolder.current();
        if (grant != null && grant.subjectType() == AccessTokenSubjectType.SERVICE_ACCOUNT) {
            return hasServiceAccountPermission(grant.subjectId(), permissionCode)
                    && grant.scopes().contains(permissionCode);
        }
        boolean allowed =
                isSuperAdminInternal(userId)
                        || listUserPermissionCodesInternal(userId).contains(permissionCode);
        return grant == null || (allowed && grant.scopes().contains(permissionCode));
    }

    private boolean hasServiceAccountPermission(Long serviceAccountId, String permissionCode) {
        return isServiceAccountSuperAdmin(serviceAccountId)
                || listServiceAccountPermissionCodesInternal(serviceAccountId)
                        .contains(permissionCode);
    }

    private boolean isServiceAccountSuperAdmin(Long serviceAccountId) {
        for (AuthorizationRole role :
                serviceAccountRoleService.listAssignedRoles(serviceAccountId)) {
            if (isEnabledSuperAdmin(role)) {
                return true;
            }
        }
        return false;
    }

    private List<String> listServiceAccountPermissionCodesInternal(Long serviceAccountId) {
        if (isServiceAccountSuperAdmin(serviceAccountId)) {
            return catalogCodes();
        }
        Map<String, String> permissionCodes = new TreeMap<>();
        for (AuthorizationRole role :
                serviceAccountRoleService.listAssignedRoles(serviceAccountId)) {
            collectRolePermissions(role, permissionCodes);
        }
        return List.copyOf(permissionCodes.values());
    }

    private void collectRolePermissions(
            @Nullable AuthorizationRole role, Map<String, String> permissionCodes) {
        if (role == null || !role.isEnabled()) {
            return;
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

    private boolean isEnabledSuperAdmin(@Nullable AuthorizationRole role) {
        return role != null && role.isEnabled() && SUPER_ADMIN_ROLE_NAME.equals(role.getRoleName());
    }

    private List<String> catalogCodes() {
        return PERMISSION_CATALOG.stream()
                .map(PermissionDefinition::permissionCode)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private List<String> applyGrantScopes(List<String> codes, @Nullable AccessTokenGrant grant) {
        if (grant == null) {
            return codes;
        }
        return codes.stream().filter(grant.scopes()::contains).sorted().toList();
    }

    public void requirePermission(Long userId, AuthorizationPermissionCode permissionCode) {
        userId = AuthenticatedUsers.requireResolvedUserId(userId);
        if (!hasPermissionInternal(userId, permissionCode.code())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "权限不足");
        }
    }

    public record PermissionDefinition(
            String permissionCode, String permissionName, String moduleCode, String description) {}
}
