package github.luckygc.am.module.authentication;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CapController {

    private final PowChallengeService powChallengeService;

    public CapController(PowChallengeService powChallengeService) {
        this.powChallengeService = powChallengeService;
    }

    @PostMapping("/api/v1/cap-challenges")
    public PowChallengeService.CapChallengeResponse challenge() {
        return powChallengeService.createChallenge();
    }

    @PostMapping("/api/v1/cap-tokens")
    public Map<String, Object> redeem(@RequestBody PowChallengeService.CapRedeemCommand command) {
        return powChallengeService.redeemChallenge(command);
    }

    @PostMapping("/api/v1/cap-tokens:validate")
    public Map<String, Object> validateToken(
            @RequestBody PowChallengeService.CapValidateCommand command) {
        return powChallengeService.validateToken(
                command.token(), Boolean.TRUE.equals(command.keepToken()));
    }
}
