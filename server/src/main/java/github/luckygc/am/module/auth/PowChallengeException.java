package github.luckygc.am.module.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class PowChallengeException extends ResponseStatusException {

    public PowChallengeException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
