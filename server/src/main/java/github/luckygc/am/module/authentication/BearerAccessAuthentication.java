package github.luckygc.am.module.authentication;

import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import github.luckygc.am.common.security.MachineAccessPrincipal;

public class BearerAccessAuthentication extends AbstractAuthenticationToken {

    private final Object principal;

    public BearerAccessAuthentication(Object principal) {
        super(List.of());
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    @Override
    public String getName() {
        if (principal instanceof MachineAccessPrincipal machine) {
            return "service-account:" + machine.serviceAccountId();
        }
        return super.getName();
    }
}
