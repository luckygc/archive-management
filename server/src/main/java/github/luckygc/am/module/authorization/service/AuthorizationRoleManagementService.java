package github.luckygc.am.module.authorization.service;

import java.util.Objects;

import jakarta.data.page.CursoredPage;
import jakarta.data.page.PageRequest;
import jakarta.data.restrict.Restrict;
import jakarta.data.restrict.Restriction;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.annotation.JsonInclude;

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
        requireRoleDirectoryRead(operatorUserId);
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
        String roleName = role.getRoleName();
        if (request.roleName() != null) {
            roleName = StringUtils.trimToNull(request.roleName());
            if (roleName == null) {
                throw new BadRequestException("角色名称不能为空", "roleName", "角色名称不能为空");
            }
        }
        boolean enabled = request.enabled() == null ? role.isEnabled() : request.enabled();
        if (isSuperAdminRole(role)
                && (!AuthorizationPermissionService.SUPER_ADMIN_ROLE_NAME.equals(roleName)
                        || !enabled)) {
            throw new BadRequestException("禁止修改超级管理员角色", "id", "禁止修改超级管理员角色的名称或启用状态");
        }
        if (!roleName.equals(role.getRoleName())) {
            AuthorizationRole existing = roleRepository.findOptionalByRoleName(roleName);
            if (existing != null && !existing.getId().equals(id)) {
                throw new BadRequestException(
                        "角色名称已存在", "roleName", "角色名称 " + request.roleName() + " 已存在");
            }
        }
        String description =
                request.descriptionChanged()
                        ? StringUtils.trimToNull(request.description())
                        : role.getDescription();
        if (roleName.equals(role.getRoleName())
                && Objects.equals(description, role.getDescription())
                && enabled == role.isEnabled()) {
            return AuthorizationRoleDto.fromEntity(role);
        }
        role.setRoleName(roleName);
        role.setDescription(description);
        role.setEnabled(enabled);
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

    private void requireRoleDirectoryRead(Long operatorUserId) {
        if (permissionService.hasPermission(
                        operatorUserId,
                        AuthorizationPermissionCode.AUTHORIZATION_ROLE_MANAGE.code())
                || permissionService.hasPermission(
                        operatorUserId,
                        AuthorizationPermissionCode.AUTHENTICATION_USER_MANAGE.code())
                || permissionService.hasPermission(
                        operatorUserId,
                        AuthorizationPermissionCode.AUTHORIZATION_PERMISSION_MANAGE.code())
                || permissionService.hasPermission(
                        operatorUserId,
                        AuthorizationPermissionCode.ARCHIVE_DATA_SCOPE_MANAGE.code())) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "权限不足");
    }

    public record CreateAuthorizationRoleRequest(String roleName, @Nullable String description) {}

    public record UpdateAuthorizationRoleRequest(
            @Nullable String roleName,
            boolean descriptionChanged,
            @Nullable String description,
            @Nullable Boolean enabled) {
        public UpdateAuthorizationRoleRequest(
                @Nullable String roleName,
                @Nullable String description,
                @Nullable Boolean enabled) {
            this(roleName, description != null, description, enabled);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
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
