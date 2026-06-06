package github.luckygc.am.infrastructure.security;

import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

public class ArchiveUserDetails extends User {

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

    public Long id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }
}
