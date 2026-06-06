package github.luckygc.am.common.security;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/cap")
public class PowChallengeController {

    private final PowChallengeService powChallengeService;

    public PowChallengeController(PowChallengeService powChallengeService) {
        this.powChallengeService = powChallengeService;
    }

    @PostMapping("/challenge")
    public PowChallengeService.CapChallengeResponse challenge() {
        return powChallengeService.createChallenge();
    }

    @PostMapping("/redeem")
    public Map<String, Object> redeem(@RequestBody PowChallengeService.CapRedeemCommand command) {
        return powChallengeService.redeemChallenge(command);
    }

    @PostMapping("/validateToken")
    public Map<String, Object> validateToken(
            @RequestBody PowChallengeService.CapValidateCommand command) {
        return powChallengeService.validateToken(command.token(), Boolean.TRUE.equals(command.keepToken()));
    }

    @ExceptionHandler(PowChallengeException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public String handlePowChallengeException(PowChallengeException ex) {
        return ex.getMessage();
    }
}
