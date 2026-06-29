package github.luckygc.am.module.authentication;

import java.io.Serial;
import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import github.luckygc.am.common.security.AuthenticatedUser;

public class ArchiveUserDetails extends User implements AuthenticatedUser {

    @Serial private static final long serialVersionUID = -1L;

    private final Long id;
    private final String displayName;

    public ArchiveUserDetails(
            Long id,
            String username,
            String password,
            boolean enabled,
            String displayName,
            Collection<? extends GrantedAuthority> authorities) {
        super(username, password, enabled, true, true, true, authorities);
        this.id = id;
        this.displayName = displayName;
    }

    @Override
    public Long id() {
        return id;
    }

    @Override
    public String displayName() {
        return displayName;
    }
}
