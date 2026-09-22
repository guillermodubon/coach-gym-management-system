package io.github.guillermodubon.coachgym.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    static final String SESSION_SECURITY_SCHEME =
            "sessionCookie";

    static final String STRIPE_WEBHOOK_SECURITY_SCHEME =
            "stripeWebhookSignature";

    @Bean
    OpenAPI coachGymOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title(
                                        "Coach Gym Management API")
                                .version("v1")
                                .description("""
                                        Administrative backend for Coach Gym.

                                        Authentication uses a server-side HTTP
                                        session. Authenticated requests send the
                                        session cookie established by the login
                                        endpoint.

                                        State-changing requests also require a
                                        valid CSRF token. Obtain the token from
                                        GET /api/v1/auth/csrf and send it using
                                        the X-XSRF-TOKEN request header.

                                        Authenticated ADMIN and RECEPTIONIST users
                                        can manage only their own self-profile at
                                        /api/v1/me/profile. Only firstName and
                                        lastName are editable. Profile mutations,
                                        private photo upload/removal, and password
                                        changes require CSRF. Password changes
                                        require reauthentication; organization and
                                        branch administration is available through
                                        the canonical organization and branch catalog
                                        endpoints. GET /api/v1/auth/me exposes the
                                        authenticated scope, safe available-branch
                                        summaries, and the server-side active-branch
                                        preference. Organization-scoped ADMINs can manage
                                        staff scopes and branch assignments through
                                        /api/v1/staff; authenticated staff can read and
                                        select their validated context through
                                        /api/v1/me/branch-context. Branch selection never
                                        grants authorization, and no client, payment, or
                                        access resource is branch-filtered yet. The frontend
                                        selector itself is outside this backend contract.

                                        Equipment and equipment categories are
                                        never physically deleted through the
                                        public API. Lifecycle and active-state
                                        endpoints must be used instead.
                                        """))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        SESSION_SECURITY_SCHEME,
                                        new SecurityScheme()
                                                .name("JSESSIONID")
                                                .type(
                                                        SecurityScheme.Type
                                                                .APIKEY)
                                                .in(
                                                        SecurityScheme.In
                                                                .COOKIE)
                                                .description("""
                                                        Server-side authenticated
                                                        session established by
                                                        POST /api/v1/auth/login.
                                                        """))
                                .addSecuritySchemes(
                                        STRIPE_WEBHOOK_SECURITY_SCHEME,
                                        new SecurityScheme()
                                                .name("Stripe-Signature")
                                                .type(SecurityScheme.Type.APIKEY)
                                                .in(SecurityScheme.In.HEADER)
                                                .description("Verified Stripe Test Mode webhook signature.")));
    }
}
