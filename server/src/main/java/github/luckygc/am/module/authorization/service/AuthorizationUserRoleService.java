package github.luckygc.am.module.authorization.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.authorization.AuthorizationRole;
import github.luckygc.am.module.authorization.AuthorizationUserRoleRelation;
import github.luckygc.am.module.authorization.repository.AuthorizationRoleDataRepository;
import github.luckygc.am.module.authorization.repository.AuthorizationUserRoleRelationDataRepository;

/** 用户角色关系的跨模块读写入口；调用用例负责用户存在性与操作权限。 */
@Service
public class AuthorizationUserRoleService {

    private final AuthorizationRoleDataRepository roleRepository;
    private final AuthorizationUserRoleRelationDataRepository relationRepository;

    public AuthorizationUserRoleService(
            AuthorizationRoleDataRepository roleRepository,
            AuthorizationUserRoleRelationDataRepository relationRepository) {
        this.roleRepository = roleRepository;
        this.relationRepository = relationRepository;
    }

    @Transactional(readOnly = true)
    public Optional<AuthorizationRole> findRole(Long roleId) {
        return roleRepository.findById(roleId);
    }

    @Transactional(readOnly = true)
    public List<AuthorizationRole> listUserRoles(Long userId) {
        List<Long> roleIds =
                relationRepository.findByUserId(userId).stream()
                        .map(AuthorizationUserRoleRelation::getRoleId)
                        .distinct()
                        .toList();
        return roleIds.isEmpty() ? List.of() : roleRepository.findByIdIn(roleIds);
    }

    /** 保留关系顺序，认证授权与数据范围计算共用同一角色有效性来源。 */
    @Transactional(readOnly = true)
    public List<AuthorizationRole> listAssignedRoles(Long userId) {
        List<Long> roleIds =
                relationRepository.findByUserId(userId).stream()
                        .map(AuthorizationUserRoleRelation::getRoleId)
                        .toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        Map<Long, AuthorizationRole> rolesById =
                roleRepository.findByIdIn(roleIds.stream().distinct().toList()).stream()
                        .collect(Collectors.toMap(AuthorizationRole::getId, Function.identity()));
        return roleIds.stream().map(rolesById::get).filter(java.util.Objects::nonNull).toList();
    }

    @Transactional
    public void replaceUserRoles(Long userId, List<Long> roleIds) {
        relationRepository.deleteByUserId(userId);
        for (Long roleId : roleIds) {
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
            insertRelation(userId, role.getId());
        }
    }

    /** 启动初始化按已有角色名幂等授权，保留未配置角色的忽略语义。 */
    @Transactional
    public void grantConfiguredRole(Long userId, String roleName) {
        AuthorizationRole role = roleRepository.findOptionalByRoleName(roleName);
        if (role == null
                || relationRepository.findByUserId(userId).stream()
                        .anyMatch(relation -> role.getId().equals(relation.getRoleId()))) {
            return;
        }
        insertRelation(userId, role.getId());
    }

    private void insertRelation(Long userId, Long roleId) {
        AuthorizationUserRoleRelation relation = new AuthorizationUserRoleRelation();
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        relationRepository.insert(relation);
    }
}
