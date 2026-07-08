package github.luckygc.am.module.authorization.service;

import jakarta.data.page.CursoredPage;
import jakarta.data.page.PageRequest;
import jakarta.data.restrict.Restrict;
import jakarta.data.restrict.Restriction;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.authorization.AuthorizationRole;
import github.luckygc.am.module.authorization._AuthorizationRole;
import github.luckygc.am.module.authorization.repository.AuthorizationRoleDataRepository;
import github.luckygc.am.module.authorization.repository.AuthorizationRolePermissionRelationDataRepository;
import github.luckygc.am.module.authorization.repository.AuthorizationUserRoleRelationDataRepository;

@Service
public class AuthorizationRoleManagementService {

    private final AuthorizationRoleDataRepository roleRepository;
    private final AuthorizationRolePermissionRelationDataRepository rolePermissionRepository;
    private final AuthorizationUserRoleRelationDataRepository userRoleRelationRepository;
    private final AuthorizationPermissionService permissionService;

    public AuthorizationRoleManagementService(
            AuthorizationRoleDataRepository roleRepository,
            AuthorizationRolePermissionRelationDataRepository rolePermissionRepository,
            AuthorizationUserRoleRelationDataRepository userRoleRelationRepository,
            AuthorizationPermissionService permissionService) {
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.userRoleRelationRepository = userRoleRelationRepository;
        this.permissionService = permissionService;
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<AuthorizationRoleDto> listRoles(
            @Nullable Boolean enabled, PageRequest pageRequest, Long operatorUserId) {
        requireRoleManage(operatorUserId);
        Restriction<AuthorizationRole> restriction = Restrict.unrestricted();
        if (enabled != null) {
            restriction = _AuthorizationRole.enabled.equalTo(enabled);
        }
        CursoredPage<AuthorizationRole> page = roleRepository.filterBy(restriction, pageRequest);
        return CursorPageResponse.from(page, pageRequest, AuthorizationRoleDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public AuthorizationRoleDto getRole(Long id, Long operatorUserId) {
        requireRoleManage(operatorUserId);
        AuthorizationRole role =
                roleRepository
                        .findById(id)
                        .orElseThrow(() -> new BadRequestException("角色不存在", "id", "角色不存在"));
        return AuthorizationRoleDto.fromEntity(role);
    }

    @Transactional
    public AuthorizationRoleDto createRole(
            CreateAuthorizationRoleRequest request, Long operatorUserId) {
        requireRoleManage(operatorUserId);
        if (StringUtils.isBlank(request.roleName())) {
            throw new BadRequestException("角色名称不能为空", "roleName", "角色名称不能为空");
        }
        if (roleRepository.findOptionalByRoleName(request.roleName().trim()) != null) {
            throw new BadRequestException(
                    "角色名称已存在", "roleName", "角色名称 " + request.roleName() + " 已存在");
        }
        AuthorizationRole role = new AuthorizationRole();
        role.setRoleName(request.roleName().trim());
        role.setDescription(
                StringUtils.isNotBlank(request.description())
                        ? request.description().trim()
                        : null);
        role.setEnabled(true);
        role = roleRepository.insert(role);
        return AuthorizationRoleDto.fromEntity(role);
    }

    @Transactional
    public AuthorizationRoleDto updateRole(
            Long id, UpdateAuthorizationRoleRequest request, Long operatorUserId) {
        requireRoleManage(operatorUserId);
        AuthorizationRole role =
                roleRepository
                        .findById(id)
                        .orElseThrow(() -> new BadRequestException("角色不存在", "id", "角色不存在"));
        if (isSuperAdminRole(role)
                && ((request.roleName() != null
                                && !AuthorizationPermissionService.SUPER_ADMIN_ROLE_NAME.equals(
                                        request.roleName().trim()))
                        || (request.enabled() != null && !request.enabled()))) {
            throw new BadRequestException("禁止修改超级管理员角色", "id", "禁止修改超级管理员角色的名称或启用状态");
        }
        if (request.roleName() != null) {
            if (StringUtils.isBlank(request.roleName())) {
                throw new BadRequestException("角色名称不能为空", "roleName", "角色名称不能为空");
            }
            AuthorizationRole existing =
                    roleRepository.findOptionalByRoleName(request.roleName().trim());
            if (existing != null && !existing.getId().equals(id)) {
                throw new BadRequestException(
                        "角色名称已存在", "roleName", "角色名称 " + request.roleName() + " 已存在");
            }
            role.setRoleName(request.roleName().trim());
        }
        if (request.description() != null) {
            role.setDescription(
                    StringUtils.isNotBlank(request.description())
                            ? request.description().trim()
                            : null);
        }
        if (request.enabled() != null) {
            role.setEnabled(request.enabled());
        }
        role = roleRepository.update(role);
        return AuthorizationRoleDto.fromEntity(role);
    }

    @Transactional
    public void deleteRole(Long id, Long operatorUserId) {
        requireRoleManage(operatorUserId);
        AuthorizationRole role =
                roleRepository
                        .findById(id)
                        .orElseThrow(() -> new BadRequestException("角色不存在", "id", "角色不存在"));
        if (isSuperAdminRole(role)) {
            throw new BadRequestException("禁止删除超级管理员角色", "id", "禁止删除超级管理员角色");
        }
        userRoleRelationRepository.findByRoleId(id).forEach(userRoleRelationRepository::delete);
        rolePermissionRepository.deleteByRoleId(id);
        roleRepository.delete(role);
    }

    private boolean isSuperAdminRole(AuthorizationRole role) {
        return AuthorizationPermissionService.SUPER_ADMIN_ROLE_NAME.equals(role.getRoleName());
    }

    private void requireRoleManage(Long operatorUserId) {
        permissionService.requirePermission(
                operatorUserId, AuthorizationPermissionCode.AUTHORIZATION_ROLE_MANAGE);
    }

    public record CreateAuthorizationRoleRequest(String roleName, @Nullable String description) {}

    public record UpdateAuthorizationRoleRequest(
            @Nullable String roleName, @Nullable String description, @Nullable Boolean enabled) {}

    public record AuthorizationRoleDto(
            Long id,
            String roleName,
            @Nullable String description,
            boolean enabled,
            String createdAt) {
        static AuthorizationRoleDto fromEntity(AuthorizationRole role) {
            return new AuthorizationRoleDto(
                    role.getId(),
                    role.getRoleName(),
                    role.getDescription(),
                    role.isEnabled(),
                    role.getCreatedAt() != null ? role.getCreatedAt().toString() : "");
        }
    }
}
