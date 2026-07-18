package github.luckygc.am.module.authentication.web;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import github.luckygc.am.module.authentication.service.PowChallengeService;

@RestController
public class CapController {

    private final PowChallengeService powChallengeService;

    public CapController(PowChallengeService powChallengeService) {
        this.powChallengeService = powChallengeService;
    }

    @PostMapping("/api/v1/cap-challenges")
    public PowChallengeService.CapChallengeResponse challenge(
            @RequestBody(required = false) PowChallengeService.CapChallengeRequest request) {
        return powChallengeService.createChallenge(request);
    }

    @PostMapping("/api/v1/cap-tokens")
    public Map<String, Object> redeem(@RequestBody PowChallengeService.CapRedeemRequest request) {
        return powChallengeService.redeemChallenge(request);
    }

    @PostMapping("/api/v1/cap-tokens:validate")
    public Map<String, Object> validateToken(
            @RequestBody PowChallengeService.CapValidateRequest request) {
        return powChallengeService.validateToken(
                request.token(), Boolean.TRUE.equals(request.keepToken()));
    }
}
