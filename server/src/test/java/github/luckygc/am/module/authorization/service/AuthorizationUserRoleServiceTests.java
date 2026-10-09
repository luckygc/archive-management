package github.luckygc.am.module.authorization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import github.luckygc.am.module.authorization.AuthorizationRole;
import github.luckygc.am.module.authorization.AuthorizationUserRoleRelation;
import github.luckygc.am.module.authorization.repository.AuthorizationRoleDataRepository;
import github.luckygc.am.module.authorization.repository.AuthorizationUserRoleRelationDataRepository;

class AuthorizationUserRoleServiceTests {
    private final AuthorizationRoleDataRepository roles =
            mock(AuthorizationRoleDataRepository.class);
    private final AuthorizationUserRoleRelationDataRepository relations =
            mock(AuthorizationUserRoleRelationDataRepository.class);
    private final AuthorizationUserRoleService service =
            new AuthorizationUserRoleService(roles, relations);

    @Test
    void assignedRolesUseOneBatchAndPreserveRelationOrderAndDisabledRoles() {
        var disabled = role(2L, false);
        var enabled = role(1L, true);
        when(relations.findByUserId(9L))
                .thenReturn(List.of(relation(2L), relation(99L), relation(1L), relation(2L)));
        when(roles.findByIdIn(List.of(2L, 99L, 1L))).thenReturn(List.of(enabled, disabled));

        assertThat(service.listAssignedRoles(9L)).containsExactly(disabled, enabled, disabled);
        verify(roles).findByIdIn(List.of(2L, 99L, 1L));
        verifyNoMoreInteractions(roles);
    }

    @Test
    void emptyAssignmentsDoNotQueryRoles() {
        when(relations.findByUserId(9L)).thenReturn(List.of());
        assertThat(service.listAssignedRoles(9L)).isEmpty();
        verifyNoInteractions(roles);
    }

    private AuthorizationRole role(long id, boolean enabled) {
        var role = new AuthorizationRole();
        role.setId(id);
        role.setEnabled(enabled);
        return role;
    }

    private AuthorizationUserRoleRelation relation(long roleId) {
        var relation = new AuthorizationUserRoleRelation();
        relation.setRoleId(roleId);
        return relation;
    }
}
