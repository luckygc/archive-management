package github.luckygc.am.module.auth;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.apache.commons.lang3.Strings;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import github.luckygc.am.common.security.AuthenticatedUser;

@RestController
public class AuthController {

    @GetMapping("/api/v1/auth/session")
    public CurrentUserDto me(Authentication authentication) {
        return CurrentUserDto.from(authentication);
    }

    @PostMapping("/api/v1/auth:logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    public record CurrentUserDto(String username, String displayName, List<String> roles) {

        public static CurrentUserDto from(Authentication authentication) {
            List<String> roles =
                    authentication.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .map(authority -> Strings.CS.removeStart(authority, "ROLE_"))
                            .toList();
            String username = authentication.getName();
            String displayName =
                    authentication.getPrincipal() instanceof AuthenticatedUser userDetails
                            ? userDetails.displayName()
                            : username;
            return new CurrentUserDto(username, displayName, roles);
        }
    }
}
