package github.luckygc.am.module.authorization.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.authorization.AuthorizationRole;
import github.luckygc.am.module.authorization.AuthorizationServiceAccountRoleRelation;
import github.luckygc.am.module.authorization.repository.AuthorizationRoleDataRepository;
import github.luckygc.am.module.authorization.repository.AuthorizationServiceAccountRoleRelationDataRepository;

/** 服务账号角色关系。调用方负责服务账号存在性与操作权限。 */
@Service
public class AuthorizationServiceAccountRoleService {

    private final AuthorizationRoleDataRepository roleRepository;
    private final AuthorizationServiceAccountRoleRelationDataRepository relationRepository;

    public AuthorizationServiceAccountRoleService(
            AuthorizationRoleDataRepository roleRepository,
            AuthorizationServiceAccountRoleRelationDataRepository relationRepository) {
        this.roleRepository = roleRepository;
        this.relationRepository = relationRepository;
    }

    @Transactional(readOnly = true)
    public List<AuthorizationRole> listAssignedRoles(Long serviceAccountId) {
        List<Long> roleIds =
                relationRepository.findByServiceAccountId(serviceAccountId).stream()
                        .map(AuthorizationServiceAccountRoleRelation::getRoleId)
                        .toList();
        return orderedRoles(roleIds);
    }

    @Transactional(readOnly = true)
    public Map<Long, List<Long>> listRoleIdsByServiceAccount(List<Long> serviceAccountIds) {
        if (serviceAccountIds.isEmpty()) {
            return Map.of();
        }
        return relationRepository.findByServiceAccountIdIn(serviceAccountIds).stream()
                .collect(
                        Collectors.groupingBy(
                                AuthorizationServiceAccountRoleRelation::getServiceAccountId,
                                Collectors.mapping(
                                        AuthorizationServiceAccountRoleRelation::getRoleId,
                                        Collectors.toList())));
    }

    @Transactional
    public void replaceRoles(Long serviceAccountId, List<Long> roleIds) {
        relationRepository.deleteByServiceAccountId(serviceAccountId);
        for (Long roleId : new LinkedHashSet<>(roleIds)) {
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
            AuthorizationServiceAccountRoleRelation relation =
                    new AuthorizationServiceAccountRoleRelation();
            relation.setServiceAccountId(serviceAccountId);
            relation.setRoleId(role.getId());
            relationRepository.insert(relation);
        }
    }

    @Transactional
    public void clearRoles(Long serviceAccountId) {
        relationRepository.deleteByServiceAccountId(serviceAccountId);
    }

    private List<AuthorizationRole> orderedRoles(List<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return List.of();
        }
        Map<Long, AuthorizationRole> rolesById =
                roleRepository.findByIdIn(roleIds.stream().distinct().toList()).stream()
                        .collect(Collectors.toMap(AuthorizationRole::getId, Function.identity()));
        return roleIds.stream().map(rolesById::get).filter(java.util.Objects::nonNull).toList();
    }
}
