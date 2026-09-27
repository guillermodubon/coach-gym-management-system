package io.github.guillermodubon.coachgym.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Verifies that the generated OpenAPI document is a complete, safe frontend
 * inventory rather than an informal list of selected controllers.
 */
class FrontendApiContractOpenApiIntegrationTest extends AbstractIncidentApiIntegrationTest {

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
        assertSessionSecurity(document, "/api/v1/memberships/{id}", "get");
        assertSessionSecurity(document, "/api/v1/payments", "get");
        assertSessionSecurity(document, "/api/v1/audit-entries", "get");
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
                "AuditEntryPageResponse")) {
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
