package github.luckygc.am.module.authentication;

import java.io.IOException;
import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;

import github.luckygc.am.module.authentication.service.TotpLoginChallengeService;
import github.luckygc.am.module.authentication.service.TotpLoginChallengeService.TotpChallengeConsumptionException;
import github.luckygc.am.module.authentication.service.TotpLoginChallengeService.VerificationResult;

import tools.jackson.databind.json.JsonMapper;

public class TotpChallengeAuthenticationFilter extends AbstractAuthenticationProcessingFilter {

    private static final String VERIFY_PATH = "/api/v1/login-session-challenges:verifyTotp";
    private static final RequestMatcher REQUEST_MATCHER =
            request ->
                    "POST".equalsIgnoreCase(request.getMethod())
                            && VERIFY_PATH.equals(request.getRequestURI());

    private final TotpLoginChallengeService challengeService;
    private final JsonMapper jsonMapper;

    public TotpChallengeAuthenticationFilter(
            AuthenticationManager authenticationManager,
            TotpLoginChallengeService challengeService,
            JsonMapper jsonMapper) {
        super(REQUEST_MATCHER, authenticationManager);
        this.challengeService = challengeService;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Authentication attemptAuthentication(
            HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationException, IOException {
        VerifyTotpRequest body = readBody(request);
        try {
            VerificationResult result = challengeService.verify(body.challengeToken(), body.code());
            if (!result.successful()) {
                throw new TotpChallengeAuthenticationException(
                        result.errorCode(), result.username(), null);
            }
            return Objects.requireNonNull(result.authentication());
        } catch (LoginBlockedException
                | TotpChallengeConsumptionException
                | IllegalStateException ex) {
            throw new TotpChallengeAuthenticationException("TOTP_CHALLENGE_INVALID", null, ex);
        }
    }

    private VerifyTotpRequest readBody(HttpServletRequest request) {
        try {
            VerifyTotpRequest body =
                    jsonMapper.readValue(request.getInputStream(), VerifyTotpRequest.class);
            return body == null
                    ? new VerifyTotpRequest("", "")
                    : new VerifyTotpRequest(
                            body.challengeToken() == null ? "" : body.challengeToken(),
                            body.code() == null ? "" : body.code());
        } catch (IOException ex) {
            return new VerifyTotpRequest("", "");
        }
    }

    private record VerifyTotpRequest(String challengeToken, String code) {}
}
