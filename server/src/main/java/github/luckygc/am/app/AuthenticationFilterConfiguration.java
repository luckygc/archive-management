package github.luckygc.am.app;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;

import github.luckygc.am.module.authentication.AccessTokenRateLimiter;
import github.luckygc.am.module.authentication.service.AccessTokenAuthenticator;
import github.luckygc.am.module.authentication.service.AccessTokenCallService;
import github.luckygc.am.module.authentication.service.AccessTokenUsageService;
import github.luckygc.am.module.authentication.service.AuthenticationAuditService;
import github.luckygc.am.module.authentication.service.TotpLoginChallengeService;
import github.luckygc.am.module.authentication.web.security.BearerAccessAuthenticationFilter;
import github.luckygc.am.module.authentication.web.security.TotpChallengeAuthenticationFailureHandler;
import github.luckygc.am.module.authentication.web.security.TotpChallengeAuthenticationFilter;
import github.luckygc.am.module.authentication.web.security.TwoStageLoginAuthenticationFilter;

import tools.jackson.databind.json.JsonMapper;

@Configuration
class AuthenticationFilterConfiguration {

    @Bean("loginSessionAuthenticationFilter")
    TwoStageLoginAuthenticationFilter loginSessionAuthenticationFilter(
            AuthenticationManager authenticationManager,
            TotpLoginChallengeService challengeService,
            JsonMapper jsonMapper) {
        return new TwoStageLoginAuthenticationFilter(
                authenticationManager, challengeService, jsonMapper);
    }

    @Bean("totpChallengeAuthenticationFailureHandler")
    TotpChallengeAuthenticationFailureHandler totpChallengeAuthenticationFailureHandler(
            JsonMapper jsonMapper, AuthenticationAuditService auditService) {
        return new TotpChallengeAuthenticationFailureHandler(jsonMapper, auditService);
    }

    @Bean("totpChallengeAuthenticationFilter")
    TotpChallengeAuthenticationFilter totpChallengeAuthenticationFilter(
            AuthenticationManager authenticationManager,
            TotpLoginChallengeService challengeService,
            JsonMapper jsonMapper) {
        return new TotpChallengeAuthenticationFilter(
                authenticationManager, challengeService, jsonMapper);
    }

    @Bean("bearerAccessAuthenticationFilter")
    BearerAccessAuthenticationFilter bearerAccessAuthenticationFilter(
            AccessTokenAuthenticator authenticator,
            AccessTokenCallService callService,
            AccessTokenUsageService usageService,
            AccessTokenRateLimiter rateLimiter,
            JsonMapper jsonMapper) {
        return new BearerAccessAuthenticationFilter(
                authenticator, callService, usageService, rateLimiter, jsonMapper);
    }

    @Bean
    FilterRegistrationBean<BearerAccessAuthenticationFilter>
            disableBearerAccessAuthenticationFilterRegistration(
                    BearerAccessAuthenticationFilter filter) {
        FilterRegistrationBean<BearerAccessAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<TwoStageLoginAuthenticationFilter>
            disableLoginSessionAuthenticationFilterRegistration(
                    TwoStageLoginAuthenticationFilter filter) {
        FilterRegistrationBean<TwoStageLoginAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<TotpChallengeAuthenticationFilter>
            disableTotpChallengeAuthenticationFilterRegistration(
                    TotpChallengeAuthenticationFilter filter) {
        FilterRegistrationBean<TotpChallengeAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
