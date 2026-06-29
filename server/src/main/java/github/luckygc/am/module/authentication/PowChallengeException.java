package github.luckygc.am.module.authentication;

import java.io.Serial;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class PowChallengeException extends ResponseStatusException {

    @Serial private static final long serialVersionUID = -1L;

    public PowChallengeException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
