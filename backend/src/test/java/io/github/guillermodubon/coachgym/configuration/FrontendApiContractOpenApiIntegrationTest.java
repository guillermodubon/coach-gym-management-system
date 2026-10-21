package io.github.guillermodubon.coachgym.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Verifies that the generated OpenAPI document is a complete, safe frontend
 * inventory rather than an informal list of selected controllers.
 */
class FrontendApiContractOpenApiIntegrationTest extends AbstractIncidentApiIntegrationTest {

    @Test
    void credentialedCorsPreflightAllowsOnlyConfiguredBrowserOrigin() throws Exception {
        for (String csrfHeaderName : List.of("X-XSRF-TOKEN", "X-CSRF-TOKEN")) {
            mockMvc.perform(options("/api/v1/auth/login")
                            .header("Origin", "http://localhost:5173")
                            .header("Access-Control-Request-Method", "POST")
                            .header("Access-Control-Request-Headers",
                                    "content-type," + csrfHeaderName.toLowerCase(java.util.Locale.ROOT)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                    .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        }

        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "https://untrusted.invalid")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,x-xsrf-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void f01OpenApiMetadataMatchesCsrfAndNullableBranchRuntimeResponses() throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.headerName").isNotEmpty())
                .andExpect(jsonPath("$.parameterName").value("_csrf"))
                .andReturn();
        String csrfHeaderName = JsonPath.read(
                csrfResult.getResponse().getContentAsString(), "$.headerName");
        String csrfToken = JsonPath.read(
                csrfResult.getResponse().getContentAsString(), "$.token");
        assertThat(csrfHeaderName).isIn("X-XSRF-TOKEN", "X-CSRF-TOKEN");
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        MockHttpSession csrfSession = (MockHttpSession) csrfResult.getRequest().getSession(false);
        assertThat(csrfCookie != null || csrfSession != null).isTrue();

        UUID branchId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        String branchCode = "F01B07_" + branchId.toString().replace("-", "")
                .substring(0, 8).toUpperCase(java.util.Locale.ROOT);
        jdbcTemplate.update("""
                insert into gym.gym_branches
                    (id, organization_id, code, name, timezone, status, version)
                select ?, id, ?, 'OpenAPI B07 context branch',
                       'America/El_Salvador', 'ACTIVE', 0
                  from gym.organizations
                 where is_canonical = true
                """, branchId, branchCode);
        jdbcTemplate.update("""
                insert into gym.staff_branch_assignments
                    (id, user_id, branch_id, status, assigned_at, assigned_by_user_id, version)
                values (?, ?, ?, 'ACTIVE', current_timestamp, ?, 0)
                """, assignmentId, receptionistId, branchId, adminId);

        var loginRequest = post("/api/v1/auth/login")
                .header(csrfHeaderName, csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"" + RECEPTIONIST_USERNAME
                        + "\",\"password\":\"" + RECEPTIONIST_PASSWORD + "\"}");
        if (csrfCookie != null) {
            loginRequest.cookie(csrfCookie);
        }
        if (csrfSession != null) {
            loginRequest.session(csrfSession);
        }

        MvcResult loginResult = mockMvc.perform(loginRequest)
                .andExpect(status().isNoContent())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeBranch").value(nullValue()))
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.availableBranches").isArray())
                .andExpect(jsonPath("$.availableBranches.length()").value(2));
        mockMvc.perform(get("/api/v1/me/branch-context").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeBranch").value(nullValue()))
                .andExpect(jsonPath("$.availableBranches").isArray())
                .andExpect(jsonPath("$.availableBranches.length()").value(2));
        mockMvc.perform(post("/api/v1/staff/branch-assignments/" + assignmentId + "/end")
                        .session(loginAsAdmin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"B07 context fixture cleanup\",\"expectedVersion\":0}"))
                .andExpect(status().isOk());

        String document = openApiDocument();
        Map<String, Object> csrfOperation = JsonPath.read(
                document, "$.paths['/api/v1/auth/csrf'].get");
        assertThat(csrfOperation).doesNotContainKey("parameters");
        Object csrfResponses = csrfOperation.get("responses");
        assertThat(csrfResponses).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) csrfResponses).containsKey("200")).isTrue();
        assertThat(((Map<?, ?>) csrfResponses).size()).isEqualTo(1);

        assertResponseCodes(document, "/api/v1/auth/login", "post",
                Set.of("204", "400", "401", "403", "413", "429"));
        assertResponseCodes(document, "/api/v1/auth/me", "get",
                Set.of("200", "401", "409"));
        assertResponseCodes(document, "/api/v1/auth/logout", "post",
                Set.of("204", "401", "403"));
        assertResponseCodes(document, "/api/v1/me/profile/password", "post",
                Set.of("200", "400", "401", "403", "404", "413", "500"));
        assertResponseCodes(document, "/api/v1/me/branch-context", "get",
                Set.of("200", "401", "403", "409"));
        assertProblemResponses(document, "/api/v1/auth/login", "post",
                Set.of("400", "401", "403", "413", "429"));
        assertProblemResponses(document, "/api/v1/auth/me", "get", Set.of("401", "409"));
        assertProblemResponses(document, "/api/v1/auth/logout", "post", Set.of("401", "403"));
        assertProblemResponses(document, "/api/v1/me/profile/password", "post",
                Set.of("400", "401", "403", "404", "413", "500"));
        assertProblemResponses(document, "/api/v1/me/branch-context", "get",
                Set.of("401", "403", "409"));
        assertJsonResponse(document, "/api/v1/me/profile/password", "post", "200");
        assertJsonResponse(document, "/api/v1/me/branch-context", "get", "200");

        assertRequiredProperties(document, "CsrfTokenResponse",
                Set.of("token", "headerName", "parameterName"));
        assertRequiredProperties(document, "LoginRequest", Set.of("identifier", "password"));
        assertRequiredProperties(document, "CurrentUserResponse",
                Set.of("id", "username", "fullName", "roles", "organizationScope",
                        "activeBranch", "availableBranches", "passwordChangeRequired"));
        assertRequiredProperties(document, "StaffBranchContextResponse",
                Set.of("organizationId", "scopeType", "activeBranch", "availableBranches"));
        assertRequiredProperties(document, "AuthorizedBranchSummary",
                Set.of("id", "organizationId", "code", "name", "timezone", "initialBranch"));
        assertRequiredProperties(document, "StaffPasswordChangeResponse",
                Set.of("userId", "profileVersion", "reauthenticationRequired"));
        assertRequiredProperties(document, "ApiProblemResponse",
                Set.of("type", "title", "status", "detail", "code", "timestamp"));
        assertThat(JsonPath.<Boolean>read(document,
                "$.components.schemas.ApiProblemResponse.additionalProperties"))
                .isTrue();
        assertRequiredProperties(document, "StaffDirectoryPageResponse",
                Set.of("items", "page", "size", "totalElements", "totalPages"));
        assertRequiredProperties(document, "StaffDirectoryEntryResponse",
                Set.of("userId", "username", "firstName", "lastName", "status", "roles",
                        "scope", "version", "scopeVersion"));
        assertRequiredProperties(document, "StaffDirectoryDetailsResponse",
                Set.of("userId", "username", "firstName", "lastName", "status", "roles",
                        "scope", "version", "scopeVersion", "email", "createdAt", "updatedAt"));
        assertRequiredProperties(document, "MembershipPageResponse",
                Set.of("items", "page", "size", "totalElements", "totalPages"));
        assertRequiredProperties(document, "MembershipResponse",
                Set.of("id", "membershipCode", "clientId", "status", "currentPeriod",
                        "createdAt", "updatedAt", "version", "registeredAtBranchId"));
        assertRequiredProperties(document, "MembershipPeriodPageResponse",
                Set.of("items", "page", "size", "totalElements", "totalPages"));
        assertRequiredProperties(document, "MembershipPeriodResponse",
                Set.of("id", "periodNumber", "source", "pricing", "startsOn", "baseEndsOn",
                        "effectiveEndsOn", "createdAt", "version", "registeredAtBranchId", "coverage"));
        assertRequiredProperties(document, "MembershipPricingResponse",
                Set.of("membershipPlanId", "planCode", "planName", "durationValue", "durationUnit",
                        "listPrice", "currency", "promotion", "discountAmount", "finalPrice"));
        assertRequiredProperties(document, "MembershipPromotionResponse",
                Set.of("promotionId", "promotionCode", "promotionName", "discountType",
                        "discountValue", "currency"));
        assertRequiredProperties(document, "MembershipPeriodCoverageResponse",
                Set.of("scopeSnapshot", "coveredBranchCount"));
        assertRequiredProperties(document, "PaymentAttemptPageResponse",
                Set.of("items", "page", "size", "totalElements", "totalPages"));
        assertRequiredProperties(document, "PaymentAttemptResponse",
                Set.of("id", "clientId", "membershipId", "membershipPeriodId", "provider", "status",
                        "expectedAmount", "currency", "failureCode", "confirmedPaymentId",
                        "createdByUserId", "createdAt", "updatedAt", "completedAt", "version",
                        "initiatedAtBranchId"));

        Map<String, Object> nullableBranchSchema = JsonPath.read(
                document, "$.components.schemas.NullableAuthorizedBranchSummary");
        Map<String, Object> nullValueSchema = JsonPath.read(
                document, "$.components.schemas.OpenApiNullValue");
        assertThat(nullableBranchSchema).containsKey("anyOf");
        assertThat(nullValueSchema).containsEntry("type", "null");
        assertNullableProperty(document, "CurrentUserResponse", "activeBranch");
        assertNullableProperty(document, "StaffBranchContextResponse", "activeBranch");
        assertNullableProperty(document, "MembershipResponse", "currentPeriod");
        assertNullableProperty(document, "MembershipPeriodResponse", "coverage");
        assertNullableProperty(document, "MembershipPricingResponse", "promotion");
        assertNullableProperty(document, "PaymentAttemptResponse", "failureCode");
        assertNullableProperty(document, "PaymentAttemptResponse", "confirmedPaymentId");
        assertNullableProperty(document, "PaymentAttemptResponse", "completedAt");
        assertLocalReferencesResolve(JsonPath.read(document, "$"),
                JsonPath.read(document, "$.components"));
    }

    @Test
    void inventoriesEveryPublicModuleAndUsesOnlyVersionedApplicationPaths() throws Exception {
        String document = openApiDocument();
        Map<String, Object> paths = JsonPath.read(document, "$.paths");

        assertThat(paths.keySet()).contains(
                "/api/v1/auth/csrf",
                "/api/v1/auth/password-recovery-requests",
                "/api/v1/auth/password-recovery/complete",
                "/api/v1/me/profile",
                "/api/v1/me/profile/photo",
                "/api/v1/me/profile/password",
                "/api/v1/clients",
                "/api/v1/plans",
                "/api/v1/promotions",
                "/api/v1/memberships",
                "/api/v1/memberships/{membershipId}/periods",
                "/api/v1/memberships/{membershipId}/periods/{periodId}",
                "/api/v1/payments",
                "/api/v1/payment-attempts/stripe-checkout",
                "/api/v1/payment-provider/stripe/webhook",
                "/api/v1/payments/{paymentId}/receipt.pdf",
                "/api/v1/clients/{clientId}/access-credential",
                "/api/v1/access/check-in",
                "/api/v1/settings/access-payment-policy",
                "/api/v1/email-deliveries",
                "/api/v1/notifications",
                "/api/v1/audit-entries",
                "/api/v1/audit-entries/export.csv",
                "/api/v1/reporting/dashboard",
                "/api/v1/organization",
                "/api/v1/branches",
                "/api/v1/branches/{id}",
                "/api/v1/branches/{id}/activate",
                "/api/v1/branches/{id}/deactivate",
                "/api/v1/staff",
                "/api/v1/staff/{userId}",
                "/api/v1/staff/branch-assignments",
                "/api/v1/staff/branch-assignments/{assignmentId}/end",
                "/api/v1/staff/{userId}/branch-assignments",
                "/api/v1/staff/{userId}/scope",
                "/api/v1/staff/{userId}/suspend",
                "/api/v1/staff/{userId}/reactivate",
                "/api/v1/staff/{userId}/deactivate",
                "/api/v1/staff/{userId}/role-scope",
                "/api/v1/staff-invitations",
                "/api/v1/staff-invitations/{invitationId}",
                "/api/v1/staff-invitations/{invitationId}/resend",
                "/api/v1/staff-invitations/{invitationId}/revoke",
                "/api/v1/staff-invitations/inspect",
                "/api/v1/staff-invitations/accept",
                "/api/v1/me/branch-context",
                "/api/v1/equipment-categories",
                "/api/v1/equipment",
                "/api/v1/incidents",
                "/api/v1/maintenances");
        assertThat(paths.keySet())
                .allMatch(path -> path.startsWith("/api/v1/"));
    }

    @Test
    void exposesBothSecuritySchemesAndProtectsEveryNonPublicBrowserBoundary() throws Exception {
        String document = openApiDocument();
        Map<String, Object> schemes = JsonPath.read(
                document, "$.components.securitySchemes");

        assertThat(schemes).containsKeys("sessionCookie", "stripeWebhookSignature");
        assertSessionSecurity(document, "/api/v1/auth/me", "get");
        assertSessionSecurity(document, "/api/v1/auth/logout", "post");
        assertSessionSecurity(document, "/api/v1/me/profile", "get");
        assertSessionSecurity(document, "/api/v1/me/profile", "put");
        assertSessionSecurity(document, "/api/v1/me/profile/photo", "get");
        assertSessionSecurity(document, "/api/v1/me/profile/photo", "put");
        assertSessionSecurity(document, "/api/v1/me/profile/photo", "delete");
        assertSessionSecurity(document, "/api/v1/me/profile/password", "post");
        assertSessionSecurity(document, "/api/v1/clients", "post");
        assertSessionSecurity(document, "/api/v1/plans", "get");
        assertSessionSecurity(document, "/api/v1/promotions", "get");
        assertSessionSecurity(document, "/api/v1/organization", "get");
        assertSessionSecurity(document, "/api/v1/organization", "put");
        assertSessionSecurity(document, "/api/v1/branches", "get");
        assertSessionSecurity(document, "/api/v1/branches", "post");
        assertSessionSecurity(document, "/api/v1/branches/{id}", "get");
        assertSessionSecurity(document, "/api/v1/branches/{id}", "put");
        assertSessionSecurity(document, "/api/v1/branches/{id}/activate", "post");
        assertSessionSecurity(document, "/api/v1/branches/{id}/deactivate", "post");
        assertSessionSecurity(document, "/api/v1/staff", "get");
        assertSessionSecurity(document, "/api/v1/staff/{userId}", "get");
        assertSessionSecurity(document, "/api/v1/staff/branch-assignments", "get");
        assertSessionSecurity(document, "/api/v1/staff/branch-assignments", "post");
        assertSessionSecurity(document, "/api/v1/staff/branch-assignments/{assignmentId}/end", "post");
        assertSessionSecurity(document, "/api/v1/staff/{userId}/branch-assignments", "get");
        assertSessionSecurity(document, "/api/v1/staff/{userId}/scope", "get");
        assertSessionSecurity(document, "/api/v1/staff/{userId}/scope", "put");
        assertSessionSecurity(document, "/api/v1/staff/{userId}/suspend", "post");
        assertSessionSecurity(document, "/api/v1/staff/{userId}/reactivate", "post");
        assertSessionSecurity(document, "/api/v1/staff/{userId}/deactivate", "post");
        assertSessionSecurity(document, "/api/v1/staff/{userId}/role-scope", "put");
        assertSessionSecurity(document, "/api/v1/staff-invitations", "get");
        assertSessionSecurity(document, "/api/v1/staff-invitations", "post");
        assertSessionSecurity(document, "/api/v1/staff-invitations/{invitationId}", "get");
        assertSessionSecurity(document, "/api/v1/staff-invitations/{invitationId}/resend", "post");
        assertSessionSecurity(document, "/api/v1/staff-invitations/{invitationId}/revoke", "post");
        assertSessionSecurity(document, "/api/v1/me/branch-context", "get");
        assertSessionSecurity(document, "/api/v1/me/branch-context", "put");
        assertSessionSecurity(document, "/api/v1/me/branch-context", "delete");
        assertSessionSecurity(document, "/api/v1/memberships", "get");
        assertSessionSecurity(document, "/api/v1/memberships/{membershipId}/periods", "get");
        assertSessionSecurity(document, "/api/v1/memberships/{membershipId}/periods/{periodId}", "get");
        assertSessionSecurity(document, "/api/v1/memberships/{id}", "get");
        assertSessionSecurity(document, "/api/v1/payments", "get");
        assertSessionSecurity(document, "/api/v1/audit-entries", "get");
        assertSessionSecurity(document, "/api/v1/audit-entries/{auditEntryId}", "get");
        assertSessionSecurity(document, "/api/v1/audit-entries/export.csv", "get");
        assertSessionSecurity(document, "/api/v1/reporting/dashboard", "get");
        assertSessionSecurity(document, "/api/v1/reporting/summary", "get");
        assertSessionSecurity(document, "/api/v1/reporting/financial-trend", "get");
        assertSessionSecurity(document, "/api/v1/reporting/access-trend", "get");
        assertSessionSecurity(document, "/api/v1/reporting/branches/comparison", "get");
        assertSessionSecurity(document, "/api/v1/notifications", "get");
        assertSessionSecurity(document, "/api/v1/notifications/{id}", "get");
        assertSessionSecurity(document, "/api/v1/notifications/unread-count", "get");
        Object webhookSecurity = JsonPath.read(
                document,
                "$.paths['/api/v1/payment-provider/stripe/webhook'].post"
                        + ".security[0].stripeWebhookSignature");
        assertThat((Object) webhookSecurity)
                .isNotNull();

        Map<String, Object> loginOperation = JsonPath.read(
                document, "$.paths['/api/v1/auth/login'].post");
        assertThat(loginOperation).doesNotContainKey("security");

        for (String path : List.of(
                "/api/v1/staff-invitations/inspect",
                "/api/v1/staff-invitations/accept",
                "/api/v1/auth/password-recovery-requests",
                "/api/v1/auth/password-recovery/complete")) {
            Map<String, Object> operation = JsonPath.read(
                    document, "$.paths['" + path + "'].post");
            assertThat(operation).as("public token/recovery operation %s", path)
                    .doesNotContainKey("security");
        }
    }

    @Test
    void openApiDescriptionDocumentsFinalBranchAndNotificationPolicies() throws Exception {
        String document = openApiDocument();
        String description = JsonPath.read(document, "$.info.description");
        String normalizedDescription = description.replaceAll("\\s+", " ");

        assertThat(normalizedDescription)
                .contains("persisted organization or branch scope")
                .contains("Notification reads remain limited to the authenticated recipient")
                .contains("single-organization system, not a multi-tenant API")
                .doesNotContain("Reporting filters and the frontend selector remain outside this API contract");
    }

    @Test
    void documentsStaffIdentityLifecycleExpiryPasswordBoundsAndGenericAbuseResponses()
            throws Exception {
        String document = openApiDocument();
        Map<String, Object> paths = JsonPath.read(document, "$.paths");
        Map<String, Object> invitationInspect = JsonPath.read(
                document, "$.paths['/api/v1/staff-invitations/inspect'].post");
        Map<String, Object> invitationAccept = JsonPath.read(
                document, "$.paths['/api/v1/staff-invitations/accept'].post");
        Map<String, Object> recoveryRequest = JsonPath.read(
                document, "$.paths['/api/v1/auth/password-recovery-requests'].post");
        Map<String, Object> recoveryComplete = JsonPath.read(
                document, "$.paths['/api/v1/auth/password-recovery/complete'].post");
        Map<String, Object> createInvitation = JsonPath.read(
                document, "$.paths['/api/v1/staff-invitations'].post");

        assertThat(invitationInspect.get("responses")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsKeys("200", "409")
                .doesNotContainKey("429");
        assertThat(invitationAccept.get("responses")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsKeys("201", "409")
                .doesNotContainKey("429");
        assertThat(recoveryRequest.get("responses")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsKey("202");
        assertThat(recoveryComplete.get("responses")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsKeys("204", "409")
                .doesNotContainKey("429");
        assertThat(createInvitation.get("responses")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsKey("429");

        assertThat(document)
                .contains("PENDING, ACCEPTED, EXPIRED, and REVOKED")
                .contains("ADMIN invitations expire after 24 hours")
                .contains("RECEPTIONIST invitations after 48 hours")
                .contains("Recovery states are PENDING, USED, EXPIRED, and REVOKED")
                .contains("expire after 15 minutes by default")
                .contains("never exceed 30 minutes")
                .contains("passwords must be 12–256 characters")
                .contains("New passwords must be 12–256")
                .contains("current-password reauthentication")
                .contains("Abuse controls do not alter the public response");

        Map<String, Object> acceptancePassword = JsonPath.read(
                document, "$.components.schemas.AcceptStaffInvitationRequest.properties.password");
        assertThat(acceptancePassword)
                .containsEntry("minLength", 12)
                .containsEntry("maxLength", 256);
        assertThat(paths.keySet()).noneMatch(path -> path.toLowerCase(java.util.Locale.ROOT)
                .contains("register") || path.toLowerCase(java.util.Locale.ROOT).contains("signup"));
    }

    @Test
    void documentsCommonPageShapeAndDoesNotExposeInternalOrUnsupportedContracts()
            throws Exception {
        String document = openApiDocument();
        Map<String, Object> paths = JsonPath.read(document, "$.paths");

        for (String schema : Set.of(
                "PlanPageResponse",
                "PromotionPageResponse",
                "AuditEntryPageResponse",
                "MembershipPageResponse",
                "MembershipPeriodPageResponse")) {
            for (String property : Set.of(
                    "items", "page", "size", "totalElements", "totalPages")) {
                Object schemaProperty = JsonPath.read(
                        document,
                        "$.components.schemas." + schema
                                + ".properties." + property);
                assertThat((Object) schemaProperty)
                        .as("schema %s property %s", schema, property)
                        .isNotNull();
            }
        }

        assertThat(document)
                .doesNotContain("ROLE_MAINTENANCE", "passwordHash", "secretKey", "serviceRoleKey", "storageKey")
                .contains("public staff registration is not supported", "generic acknowledgement");
        assertThat(paths.keySet()).noneMatch(path -> path.toLowerCase(java.util.Locale.ROOT)
                .contains("register") || path.toLowerCase(java.util.Locale.ROOT).contains("signup"));
        Map<String, Object> profileSchema = JsonPath.read(
                document, "$.components.schemas.StaffSelfProfile.properties");
        assertThat(profileSchema.keySet())
                .contains("displayName", "roles", "status", "photoPresent", "photoUrl", "version")
                .doesNotContain("organizationId", "organizationScope", "branchId", "branchAssignments");
        Map<String, Object> passwordSchema = JsonPath.read(
                document, "$.components.schemas.ChangeStaffPasswordRequest.properties");
        assertThat(passwordSchema.keySet())
                .containsExactlyInAnyOrder(
                        "currentPassword", "newPassword", "newPasswordConfirmation")
                .doesNotContain("userId", "role", "status", "passwordHash", "sessionId");

        Map<String, Object> acceptanceProperties = JsonPath.read(
                document, "$.components.schemas.AcceptStaffInvitationRequest.properties");
        assertThat(acceptanceProperties.keySet()).containsExactlyInAnyOrder(
                "token", "password", "passwordConfirmation", "firstName", "lastName");
        assertThat(acceptanceProperties.keySet()).doesNotContain(
                "email", "role", "scope", "branchIds", "status", "permissions", "actorUserId");
        assertThat(document).doesNotContain("A".repeat(43));
    }

    @Test
    void documentsMembershipListFiltersScopeAndSafeResponses() throws Exception {
        String document = openApiDocument();
        List<Map<String, Object>> parameters = JsonPath.read(
                document, "$.paths['/api/v1/memberships'].get.parameters");
        Map<String, Object> responses = JsonPath.read(
                document, "$.paths['/api/v1/memberships'].get.responses");

        assertThat(parameters.stream().map(parameter -> parameter.get("name")))
                .contains("search", "clientId", "status", "branchId", "page", "size", "sort", "direction");
        assertThat(responses)
                .containsKeys("200", "400", "401", "403", "404", "409", "500");
        assertThat(document)
                .contains("membership codes")
                .contains("omitting branchId never requests all branches");
        Map<String, Object> pageProperties = JsonPath.read(
                document, "$.components.schemas.MembershipPageResponse.properties");
        assertThat(pageProperties.keySet())
                .contains("items", "page", "size", "totalElements", "totalPages");
        Map<String, Object> membershipProperties = JsonPath.read(
                document, "$.components.schemas.MembershipResponse.properties");
        assertThat(membershipProperties.keySet()).doesNotContain("clientName", "email", "phone");
    }

    @Test
    void documentsMembershipPeriodHistoryAndScopedDetailContracts() throws Exception {
        String document = openApiDocument();
        List<Map<String, Object>> pageParameters = JsonPath.read(
                document,
                "$.paths['/api/v1/memberships/{membershipId}/periods'].get.parameters");
        Map<String, Object> pageResponses = JsonPath.read(
                document,
                "$.paths['/api/v1/memberships/{membershipId}/periods'].get.responses");
        Map<String, Object> detailResponses = JsonPath.read(
                document,
                "$.paths['/api/v1/memberships/{membershipId}/periods/{periodId}'].get.responses");

        assertThat(pageParameters.stream().map(parameter -> parameter.get("name")))
                .contains("membershipId", "page", "size", "direction");
        assertThat(pageResponses).containsKeys("200", "400", "401", "403", "404", "409", "500");
        assertThat(detailResponses).containsKeys("200", "401", "403", "404", "409", "500");
        Map<String, Object> coverageProperties = JsonPath.read(
                document, "$.components.schemas.MembershipPeriodCoverageResponse.properties");
        assertThat(coverageProperties.keySet())
                .contains("scopeSnapshot", "coveredBranchCount")
                .doesNotContain("branchIds");
        assertThat(document)
                .contains("period number", "A period identifier alone grants no access");
    }

    @Test
    void exposesOrganizationAndBranchAdministrationWithoutPrematureScopeContracts()
            throws Exception {
        String document = openApiDocument();
        Map<String, Object> schemas = JsonPath.read(
                document, "$.components.schemas");

        assertThat(schemas).containsKeys(
                "OrganizationResponse",
                "BranchResponse",
                "BranchPageResponse",
                "CreateBranchRequest",
                "UpdateBranchRequest",
                "BranchStatusChangeRequest");

        Map<String, Object> createBranchProperties = JsonPath.read(
                document, "$.components.schemas.CreateBranchRequest.properties");
        assertThat(createBranchProperties.keySet())
                .contains("code", "name", "timezone")
                .doesNotContain("organizationId", "status", "initialBranch", "version");

        Map<String, Object> updateBranchProperties = JsonPath.read(
                document, "$.components.schemas.UpdateBranchRequest.properties");
        assertThat(updateBranchProperties.keySet())
                .contains("name", "version")
                .doesNotContain("code", "organizationId", "status", "initialBranch");

        Map<String, Object> currentUserProperties = JsonPath.read(
                document, "$.components.schemas.CurrentUserResponse.properties");
        assertThat(currentUserProperties.keySet())
                .contains("id", "username", "roles", "organizationScope",
                        "activeBranch", "availableBranches")
                .doesNotContain("tenantId", "password", "token");

        Map<String, Object> branchResponseProperties = JsonPath.read(
                document, "$.components.schemas.BranchResponse.properties");
        assertThat(branchResponseProperties.keySet())
                .contains("id", "code", "name", "status", "version")
                .doesNotContain("tenantId", "branchAssignments", "activeBranch");

        Map<String, Object> branchCollection = JsonPath.read(
                document, "$.paths['/api/v1/branches']");
        Map<String, Object> branchItem = JsonPath.read(
                document, "$.paths['/api/v1/branches/{id}']");
        assertThat(branchCollection.keySet())
                .doesNotContain("delete");
        assertThat(branchItem.keySet())
                .doesNotContain("delete");
        assertThat(document)
                .contains("canonical organization", "RECEPTIONIST", "Requires CSRF")
                .doesNotContain("branchAssignments", "tenantId");

        Map<String, Object> assignmentRequest = JsonPath.read(
                document, "$.components.schemas.StaffBranchAssignmentRequest.properties");
        assertThat(assignmentRequest.keySet())
                .containsExactlyInAnyOrder("targetUserId", "branchId", "reason")
                .doesNotContain("status", "role", "organizationId", "actorUserId");
    }

    @Test
    void branchOwnedSearchesExposeOnlyAnExplicitOptionalBranchFilterAndSafeOwnershipIds()
            throws Exception {
        String document = openApiDocument();
        for (String path : List.of(
                "/api/v1/clients",
                "/api/v1/payments",
                "/api/v1/access/records",
                "/api/v1/equipment",
                "/api/v1/incidents",
                "/api/v1/maintenances",
                "/api/v1/email-deliveries")) {
            List<Map<String, Object>> parameters = JsonPath.read(
                    document, "$.paths['" + path + "'].get.parameters");
            assertThat(parameters)
                    .anySatisfy(parameter -> assertThat(parameter)
                            .containsEntry("name", "branchId")
                            .containsEntry("in", "query"));
        }

        Map<String, Object> schemas = JsonPath.read(
                document, "$.components.schemas");
        assertThat(schemas).containsKeys(
                "ClientResponse", "PaymentResponse", "AccessRecordResponse",
                "EquipmentResponse", "IncidentResponse", "MaintenanceResponse",
                "PaymentAttemptResponse", "PaymentReceiptResponse", "EmailDeliveryResponse",
                "PaymentCorrectionResponse", "MembershipResponse",
                "MembershipPeriodResponse", "NotificationResponse");
        for (String schemaName : List.of(
                "ClientResponse", "PaymentResponse", "AccessRecordResponse",
                "EquipmentResponse", "IncidentResponse", "MaintenanceResponse",
                "PaymentAttemptResponse", "PaymentReceiptResponse", "EmailDeliveryResponse",
                "PaymentCorrectionResponse", "MembershipResponse",
                "MembershipPeriodResponse", "NotificationResponse")) {
            Map<String, Object> properties = JsonPath.read(
                    document, "$.components.schemas." + schemaName + ".properties");
            assertThat(properties.keySet()).anyMatch(name -> name.equals("branchId")
                    || name.equals("homeBranchId")
                    || name.equals("registeredAtBranchId")
                    || name.equals("initiatedAtBranchId"));
        }
    }

    @Test
    void exposesStableProblemDetailFieldsAndCorrelationHeaderToTheFrontend()
            throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("X-Correlation-ID", "ui-contract-001"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Correlation-ID", "ui-contract-001"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.instance").value("/api/v1/auth/me"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.correlationId").value("ui-contract-001"))
                .andExpect(jsonPath("$.detail").value("Authentication is required."));
    }

    @Test
    void generatesTheSameBoundedCorrelationIdInHeaderAndProblemBody() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String headerValue = result.getResponse().getHeader("X-Correlation-ID");
        String bodyValue = JsonPath.read(
                result.getResponse().getContentAsString(), "$.correlationId");

        assertThat(headerValue)
                .isNotBlank()
                .hasSize(36)
                .matches("[0-9a-f-]+");
        assertThat(bodyValue).isEqualTo(headerValue);
    }

    private String openApiDocument() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    private static void assertResponseCodes(
            String document,
            String path,
            String method,
            Set<String> expectedCodes) {
        Map<String, Object> responses = JsonPath.read(
                document, "$.paths['" + path + "']." + method + ".responses");
        assertThat(responses.keySet()).containsExactlyInAnyOrderElementsOf(expectedCodes);
    }

    private static void assertProblemResponses(
            String document,
            String path,
            String method,
            Set<String> statuses) {
        for (String status : statuses) {
            String schemaReference = JsonPath.read(
                    document,
                    "$.paths['" + path + "']." + method + ".responses['" + status
                            + "'].content['application/problem+json'].schema['$ref']");
            assertThat(schemaReference)
                    .as("ProblemDetail schema for %s %s %s", method, path, status)
                    .isEqualTo("#/components/schemas/ApiProblemResponse");
        }
    }

    private static void assertJsonResponse(String document, String path, String method, String status) {
        String schemaReference = JsonPath.read(
                document,
                "$.paths['" + path + "']." + method + ".responses['" + status
                        + "'].content['application/json'].schema['$ref']");
        assertThat(schemaReference).isNotBlank();
    }

    private static void assertRequiredProperties(
            String document,
            String schemaName,
            Set<String> expectedProperties) {
        List<String> required = JsonPath.read(
                document, "$.components.schemas." + schemaName + ".required");
        assertThat(required).containsExactlyInAnyOrderElementsOf(expectedProperties);
    }

    private static void assertNullableProperty(
            String document,
            String schemaName,
            String propertyName) {
        Map<String, Object> property = JsonPath.read(
                document, "$.components.schemas." + schemaName + ".properties." + propertyName);
        Map<String, Object> components = JsonPath.read(document, "$.components");
        assertThat(schemaAllowsNull(property, components, new java.util.HashSet<>()))
                .as("OpenAPI schema %s.%s permits null: %s", schemaName, propertyName, property)
                .isTrue();
    }

    private static boolean schemaAllowsNull(
            Object schema,
            Map<String, Object> components,
            Set<String> visited) {
        if (!(schema instanceof Map<?, ?> properties)) {
            return false;
        }
        if (Boolean.TRUE.equals(properties.get("nullable"))) {
            return true;
        }
        Object type = properties.get("type");
        if ("null".equals(type)) {
            return true;
        }
        if (type instanceof List<?> types && types.contains("null")) {
            return true;
        }
        Object types = properties.get("types");
        if (types instanceof List<?> acceptedTypes && acceptedTypes.contains("null")) {
            return true;
        }
        Object reference = properties.get("$ref");
        if (reference instanceof String ref && visited.add(ref)) {
            Map<String, Object> referenced = referencedComponent(ref, components);
            if (referenced != null && schemaAllowsNull(referenced, components, visited)) {
                return true;
            }
        }
        for (String key : List.of("anyOf", "oneOf")) {
            Object alternatives = properties.get(key);
            if (alternatives instanceof List<?> schemas
                    && schemas.stream().anyMatch(
                            child -> schemaAllowsNull(child, components, new java.util.HashSet<>(visited)))) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Object> referencedComponent(
            String reference,
            Map<String, Object> components) {
        if (!reference.startsWith("#/components/")) {
            return null;
        }
        String[] segments = reference.substring(2).split("/");
        if (segments.length != 3) {
            return null;
        }
        Object group = components.get(segments[1]);
        if (!(group instanceof Map<?, ?> values)) {
            return null;
        }
        String name = segments[2].replace("~1", "/").replace("~0", "~");
        Object component = values.get(name);
        if (!(component instanceof Map<?, ?> schema)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> typedSchema = (Map<String, Object>) schema;
        return typedSchema;
    }

    private static void assertLocalReferencesResolve(Object value, Map<String, Object> components) {
        if (value instanceof Map<?, ?> object) {
            Object reference = object.get("$ref");
            if (reference instanceof String ref) {
                assertThat(ref).startsWith("#/components/");
                String[] segments = ref.substring(2).split("/");
                assertThat(segments).hasSize(3);
                Object group = components.get(segments[1]);
                assertThat(group).isInstanceOf(Map.class);
                String name = segments[2].replace("~1", "/").replace("~0", "~");
                assertThat(((Map<?, ?>) group).containsKey(name)).isTrue();
            }
            object.values().forEach(child -> assertLocalReferencesResolve(child, components));
        } else if (value instanceof List<?> list) {
            list.forEach(child -> assertLocalReferencesResolve(child, components));
        }
    }

    private static void assertSessionSecurity(
            String document,
            String path,
            String method) {
        Object security = JsonPath.read(
                document,
                "$.paths['" + path + "']." + method
                        + ".security[0].sessionCookie");
        assertThat((Object) security)
                .as("session security for %s %s", method, path)
                .isNotNull();
    }
}
