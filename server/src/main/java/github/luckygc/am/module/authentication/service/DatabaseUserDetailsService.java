package github.luckygc.am.module.authentication.service;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import github.luckygc.am.module.authentication.ArchiveUserDetails;
import github.luckygc.am.module.authentication.AuthenticationUser;
import github.luckygc.am.module.authentication.repository.AuthenticationUserDataRepository;
import github.luckygc.am.module.authorization.AuthorizationRole;
import github.luckygc.am.module.authorization.service.AuthorizationUserRoleService;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final AuthenticationUserDataRepository userRepository;
    private final AuthorizationUserRoleService userRoleService;

    public DatabaseUserDetailsService(
            AuthenticationUserDataRepository userRepository,
            AuthorizationUserRoleService userRoleService) {
        this.userRepository = userRepository;
        this.userRoleService = userRoleService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AuthenticationUser user = userRepository.findOptionalByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }

        List<SimpleGrantedAuthority> authorities =
                userRoleService.listAssignedRoles(user.getId()).stream()
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
