package github.luckygc.am.infrastructure.security.config;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.annotation.web.configurers.CorsConfigurer;
import org.springframework.security.config.annotation.web.configurers.ExceptionHandlingConfigurer;
import org.springframework.security.config.annotation.web.configurers.FormLoginConfigurer;
import org.springframework.security.config.annotation.web.configurers.LogoutConfigurer;
import org.springframework.security.config.annotation.web.configurers.SecurityContextConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import github.luckygc.am.infrastructure.security.ApiRequestSignatureFilter;
import github.luckygc.am.infrastructure.security.util.FormLoginAuthenticationFailureHandler;
import github.luckygc.am.infrastructure.security.util.HttpStatusLogoutSuccessHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final SecurityContextRepository securityContextRepository;
    private final ApiRequestSignatureFilter apiRequestSignatureFilter;
    private final OncePerRequestFilter powLoginFilter;
    private final AuthenticationSuccessHandler authenticationSuccessHandler;
    private final FormLoginAuthenticationFailureHandler authenticationFailureHandler;
    private final HttpStatusLogoutSuccessHandler logoutSuccessHandler;
    private final SecurityCorsProperties corsProperties;
    private final SecurityAuthorizationProperties authorizationProperties;

    public SecurityConfig(
            SecurityContextRepository securityContextRepository,
            ApiRequestSignatureFilter apiRequestSignatureFilter,
            @Qualifier("powLoginFilter") OncePerRequestFilter powLoginFilter,
            @Qualifier("formLoginAuthenticationSuccessHandler") AuthenticationSuccessHandler authenticationSuccessHandler,
            FormLoginAuthenticationFailureHandler authenticationFailureHandler,
            HttpStatusLogoutSuccessHandler logoutSuccessHandler,
            SecurityCorsProperties corsProperties,
            SecurityAuthorizationProperties authorizationProperties) {
        this.securityContextRepository = securityContextRepository;
        this.apiRequestSignatureFilter = apiRequestSignatureFilter;
        this.powLoginFilter = powLoginFilter;
        this.authenticationSuccessHandler = authenticationSuccessHandler;
        this.authenticationFailureHandler = authenticationFailureHandler;
        this.logoutSuccessHandler = logoutSuccessHandler;
        this.corsProperties = corsProperties;
        this.authorizationProperties = authorizationProperties;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.cors(this::configureCors)
                .csrf(csrf -> csrf.spa().ignoringRequestMatchers("/api/v1/auth/cap/**"))
                .securityContext(this::configureSecurityContext)
                .authorizeHttpRequests(this::configureAuthorization)
                .addFilterBefore(
                        apiRequestSignatureFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(powLoginFilter, UsernamePasswordAuthenticationFilter.class)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(this::configureFormLogin)
                .logout(this::configureLogout)
                .exceptionHandling(this::configureExceptionHandling)
                .build();
    }

    private void configureCors(CorsConfigurer<HttpSecurity> cors) {
        cors.configurationSource(corsConfigurationSource());
    }

    private void configureSecurityContext(SecurityContextConfigurer<HttpSecurity> securityContext) {
        securityContext.securityContextRepository(securityContextRepository);
    }

    private void configureAuthorization(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry
                    authorize) {
        authorize
                .requestMatchers(HttpMethod.OPTIONS, "/**")
                .permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth:login")
                .permitAll()
                .requestMatchers("/api/v1/auth/cap/**")
                .permitAll()
                .requestMatchers("/actuator/**")
                .hasRole(authorizationProperties.getActuatorRoleName())
                .anyRequest()
                .authenticated();
    }

    private void configureFormLogin(FormLoginConfigurer<HttpSecurity> formLogin) {
        formLogin
                .loginPage("/login")
                .loginProcessingUrl("/api/v1/auth:login")
                .securityContextRepository(securityContextRepository)
                .successHandler(authenticationSuccessHandler)
                .failureHandler(authenticationFailureHandler)
                .permitAll();
    }

    private void configureLogout(LogoutConfigurer<HttpSecurity> logout) {
        logout.logoutUrl("/api/v1/auth:logout").logoutSuccessHandler(logoutSuccessHandler);
    }

    private void configureExceptionHandling(
            ExceptionHandlingConfigurer<HttpSecurity> exceptionHandling) {
        exceptionHandling.defaultAuthenticationEntryPointFor(
                unauthorizedEntryPoint(), new ApiRequestMatcher());
    }

    private AuthenticationEntryPoint unauthorizedEntryPoint() {
        return new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.getAllowedOrigins());
        configuration.setAllowedOriginPatterns(corsProperties.getAllowedOriginPatterns());
        configuration.setAllowedMethods(corsProperties.getAllowedMethods());
        configuration.setAllowedHeaders(corsProperties.getAllowedHeaders());
        configuration.setExposedHeaders(corsProperties.getExposedHeaders());
        configuration.setAllowCredentials(corsProperties.isAllowCredentials());
        configuration.setMaxAge(corsProperties.getMaxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private static class ApiRequestMatcher implements RequestMatcher {

        @Override
        public boolean matches(HttpServletRequest request) {
            return request.getRequestURI().startsWith("/api/");
        }
    }
}
