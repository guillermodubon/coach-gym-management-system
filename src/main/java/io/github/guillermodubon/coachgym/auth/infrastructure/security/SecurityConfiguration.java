package io.github.guillermodubon.coachgym.auth.infrastructure.security;

import io.github.guillermodubon.coachgym.auth.SessionSecurityPolicy;
import io.github.guillermodubon.coachgym.shared.web.CorrelationIdFilter;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties({
        CorsProperties.class,
        SessionProperties.class,
        SecurityHeadersProperties.class,
        LoginRateLimitProperties.class,
        RequestBodyLimitProperties.class})
class SecurityConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(
            CoachGymUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy() {
        return new ChangeSessionIdAuthenticationStrategy();
    }

    @Bean
    SessionSecurityPolicy sessionSecurityPolicy(Clock clock, SessionProperties properties) {
        return new SessionSecurityPolicy(clock, properties.absoluteTimeout());
    }

    @Bean
    AbsoluteSessionTimeoutFilter absoluteSessionTimeoutFilter(
            SessionSecurityPolicy sessionSecurityPolicy) {
        return new AbsoluteSessionTimeoutFilter(sessionSecurityPolicy);
    }

    @Bean
    LoginAttemptRateLimiter loginAttemptRateLimiter(
            LoginRateLimitProperties properties,
            Clock clock) {
        return new LoginAttemptRateLimiter(properties, clock);
    }

    @Bean
    RequestBodyLimitFilter requestBodyLimitFilter(
            RequestBodyLimitProperties properties,
            tools.jackson.databind.json.JsonMapper jsonMapper) {
        return new RequestBodyLimitFilter(properties, jsonMapper);
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of(
                HttpMethod.GET.name(),
                HttpMethod.HEAD.name(),
                HttpMethod.POST.name(),
                HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.OPTIONS.name()));
        configuration.setAllowedHeaders(List.of(
                "Accept",
                "Content-Type",
                "X-XSRF-TOKEN",
                CorrelationIdFilter.HEADER,
                "X-Requested-With"));
        configuration.setExposedHeaders(List.of(
                "Location",
                "Content-Disposition",
                CorrelationIdFilter.HEADER));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(properties.maxAge().toSeconds());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    SecurityFilterChain applicationSecurityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            ProblemDetailAuthenticationEntryPoint authenticationEntryPoint,
            ProblemDetailAccessDeniedHandler accessDeniedHandler,
            CorsConfigurationSource corsConfigurationSource,
            SessionSecurityPolicy sessionSecurityPolicy,
            SecurityHeadersProperties securityHeadersProperties,
            AbsoluteSessionTimeoutFilter absoluteSessionTimeoutFilter,
            RequestBodyLimitFilter requestBodyLimitFilter,
            CorrelationIdFilter correlationIdFilter) throws Exception {
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookiePath("/");
        RequestMatcher stripeWebhook = request ->
                HttpMethod.POST.matches(request.getMethod())
                        && "/api/v1/payment-provider/stripe/webhook"
                                .equals(request.getRequestURI());

        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .ignoringRequestMatchers(stripeWebhook))
                .securityContext(context -> context
                        .requireExplicitSave(true)
                        .securityContextRepository(securityContextRepository))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(sessionFixation -> sessionFixation.migrateSession()))
                .headers(headers -> {
                    headers.contentTypeOptions(contentTypeOptions -> { });
                    headers.frameOptions(frame -> frame.deny());
                    headers.referrerPolicy(referrer -> referrer.policy(
                            ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER));
                    headers.cacheControl(cache -> { });
                    headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                            "frame-ancestors 'none'"));
                    if (securityHeadersProperties.hstsEnabled()) {
                        headers.httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(securityHeadersProperties.hstsMaxAge().toSeconds()));
                    }
                })
                .addFilterBefore(correlationIdFilter, SecurityContextHolderFilter.class)
                .addFilterAfter(absoluteSessionTimeoutFilter, SecurityContextHolderFilter.class)
                .addFilterBefore(requestBodyLimitFilter, SecurityContextHolderFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/api/v1/auth/csrf",
                                "/api/v1/auth/login",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html")
                        .permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/payment-provider/stripe/webhook")
                        .permitAll()
                        .requestMatchers("/api/v1/auth/me", "/api/v1/auth/logout")
                        .authenticated()
                        .requestMatchers("/api/v1/me/**")
                        .authenticated()
                        .requestMatchers("/api/v1/settings/access-payment-policy")
                        .authenticated()
                        .requestMatchers("/api/v1/clients/**")
                        .authenticated()
                        .requestMatchers("/api/v1/plans/**")
                        .authenticated()
                        .requestMatchers("/api/v1/promotions/**")
                        .authenticated()
                        .requestMatchers("/api/v1/memberships/**")
                        .authenticated()
                        .requestMatchers("/api/v1/payments/**")
                        .authenticated()
                        .requestMatchers("/api/v1/payment-attempts/**")
                        .authenticated()
                        .requestMatchers("/api/v1/access/**")
                        .authenticated()
                        .requestMatchers("/api/v1/equipment-categories/**")
                        .authenticated()
                        .requestMatchers("/api/v1/equipment/**")
                        .authenticated()
                        .requestMatchers("/api/v1/incidents/**")
                        .authenticated()
                        .requestMatchers("/api/v1/maintenances/**")
                        .authenticated()
                        .requestMatchers("/api/v1/notifications/**")
                        .authenticated()
                        .requestMatchers("/api/v1/email-deliveries/**")
                        .authenticated()
                        .requestMatchers("/api/v1/reporting/**")
                                .authenticated()
                        .requestMatchers("/api/v1/audit-entries/**")
                                .authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/organization")
                                .hasRole("ADMIN")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/branches",
                                "/api/v1/branches/*/activate",
                                "/api/v1/branches/*/deactivate")
                                .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/branches/*")
                                .hasRole("ADMIN")
                        .requestMatchers("/api/v1/organization/**", "/api/v1/branches/**")
                                .authenticated()
                        .anyRequest()
                        .denyAll())
                .build();
    }
}
