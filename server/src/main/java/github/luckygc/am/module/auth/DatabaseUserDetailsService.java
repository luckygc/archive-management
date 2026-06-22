package github.luckygc.am.module.auth;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final AuthUserDataRepository userRepository;
    private final AuthRoleDataRepository roleRepository;
    private final AuthUserRoleRelationDataRepository userRoleRelationRepository;

    public DatabaseUserDetailsService(
            AuthUserDataRepository userRepository,
            AuthRoleDataRepository roleRepository,
            AuthUserRoleRelationDataRepository userRoleRelationRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRelationRepository = userRoleRelationRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AuthUser user = userRepository.findOptionalByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }

        List<SimpleGrantedAuthority> authorities =
                userRoleRelationRepository.findByUserId(user.getId()).stream()
                        .map(AuthUserRoleRelation::getRoleId)
                        .map(roleRepository::findById)
                        .flatMap(role -> role.stream())
                        .filter(AuthRole::isEnabled)
                        .map(AuthRole::getRoleName)
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
