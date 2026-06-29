package github.luckygc.am.module.authentication;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import github.luckygc.am.module.authorization.AuthorizationRole;
import github.luckygc.am.module.authorization.AuthorizationRoleDataRepository;
import github.luckygc.am.module.authorization.AuthorizationUserRoleRelation;
import github.luckygc.am.module.authorization.AuthorizationUserRoleRelationDataRepository;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final AuthenticationUserDataRepository userRepository;
    private final AuthorizationRoleDataRepository roleRepository;
    private final AuthorizationUserRoleRelationDataRepository userRoleRelationRepository;

    public DatabaseUserDetailsService(
            AuthenticationUserDataRepository userRepository,
            AuthorizationRoleDataRepository roleRepository,
            AuthorizationUserRoleRelationDataRepository userRoleRelationRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRelationRepository = userRoleRelationRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AuthenticationUser user = userRepository.findOptionalByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }

        List<SimpleGrantedAuthority> authorities =
                userRoleRelationRepository.findByUserId(user.getId()).stream()
                        .map(AuthorizationUserRoleRelation::getRoleId)
                        .map(roleRepository::findById)
                        .flatMap(Optional::stream)
                        .filter(AuthorizationRole::isEnabled)
                        .map(AuthorizationRole::getRoleName)
                        .filter(StringUtils::isNotBlank)
                        .sorted()
                        .map(roleName -> new SimpleGrantedAuthority("ROLE_" + roleName))
                        .toList();

        return new ArchiveUserDetails(
                user.getId(),
                user.getUsername(),
                user.getPassword(),
                user.isEnabled(),
                user.getDisplayName(),
                authorities);
    }
}
