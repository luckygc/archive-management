package github.luckygc.am.app;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;

import github.luckygc.am.module.authentication.TotpChallengeAuthenticationFailureHandler;
import github.luckygc.am.module.authentication.TotpChallengeAuthenticationFilter;
import github.luckygc.am.module.authentication.TwoStageLoginAuthenticationFilter;
import github.luckygc.am.module.authentication.service.AuthenticationAuditService;
import github.luckygc.am.module.authentication.service.TotpLoginChallengeService;

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
