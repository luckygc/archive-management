package github.luckygc.am.infrastructure.security;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final JdbcClient jdbcClient;

    public DatabaseUserDetailsService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        ArchiveUserRecord user = jdbcClient.sql("""
                        select id, username, password, display_name, enabled
                        from am_user
                        where username = :username
                        """)
                .param("username", username)
                .query(this::mapUser)
                .optional()
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));

        List<SimpleGrantedAuthority> authorities = jdbcClient.sql("""
                        select role_code
                        from am_user_role
                        where user_id = :userId
                        order by role_code
                        """)
                .param("userId", user.id())
                .query((rs, rowNum) -> new SimpleGrantedAuthority("ROLE_" + rs.getString("role_code")))
                .list();

        return new ArchiveUserDetails(
                user.id(),
                user.username(),
                user.password(),
                user.enabled(),
                user.displayName(),
                authorities);
    }

    private ArchiveUserRecord mapUser(ResultSet rs, int rowNum) throws SQLException {
        return new ArchiveUserRecord(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("password"),
                rs.getString("display_name"),
                rs.getBoolean("enabled"));
    }

    private record ArchiveUserRecord(Long id, String username, String password, String displayName, boolean enabled) {
    }
}
