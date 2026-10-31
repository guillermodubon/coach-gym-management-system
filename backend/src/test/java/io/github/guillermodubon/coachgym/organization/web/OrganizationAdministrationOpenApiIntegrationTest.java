package io.github.guillermodubon.coachgym.organization.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** Verifies generated OpenAPI for organization-wide administration without adding routes. */
class OrganizationAdministrationOpenApiIntegrationTest extends AbstractIncidentApiIntegrationTest {

    private static final List<OperationContract> MUTATIONS = List.of(
            new OperationContract(
                    "/api/v1/organization", "put", "200", "OrganizationResponse",
                    "UpdateOrganizationRequest"),
            new OperationContract(
                    "/api/v1/branches", "post", "201", "BranchResponse",
                    "CreateBranchRequest"),
            new OperationContract(
                    "/api/v1/branches/{id}", "put", "200", "BranchResponse",
                    "UpdateBranchRequest"),
            new OperationContract(
                    "/api/v1/branches/{id}/activate", "post", "200", "BranchResponse",
                    "BranchStatusChangeRequest"),
            new OperationContract(
                    "/api/v1/branches/{id}/deactivate", "post", "200", "BranchResponse",
                    "BranchStatusChangeRequest"));

    @Test
    void documentsPersistedOrganizationAuthorityProblemsAndExistingDtosForAllFiveMutations()
            throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        String document = result.getResponse().getContentAsString();

        for (OperationContract contract : MUTATIONS) {
            Map<String, Object> operation = readObject(
                    document, operationPath(contract.path(), contract.method()));
            assertSessionCookieSecurity(operation);
            assertResponseSchema(operation, contract.successStatus(), contract.successSchema());
            assertRequestBodySchema(operation, contract.requestSchema());
            assertProblemResponse(operation, "401");
            assertProblemResponse(operation, "403");
            assertProblemResponse(operation, "500");

            String description = (String) operation.get("description");
            assertThat(description)
                    .contains("active ADMIN", "persisted staff scope is ORGANIZATION", "CSRF token")
                    .doesNotContain("ADMIN only");
            Map<String, Object> responses = asMap(operation.get("responses"));
            String forbiddenDescription = (String) asMap(responses.get("403")).get("description");
            assertThat(forbiddenDescription).contains("ACCESS_DENIED", "CSRF_TOKEN_INVALID");
            String unavailableDescription = (String) asMap(responses.get("500")).get("description");
            assertThat(unavailableDescription)
                    .contains("authority")
                    .doesNotContain("OrganizationAdministrationAuthorization", "StaffScopeQuery", "SQLException");

            assertRequestSchema(document, contract.requestSchema());
        }

        assertReadProjectionAndCatalogRemainDocumented(document);
        assertExistingOperationInventoryOnly(document);
    }

    private static void assertReadProjectionAndCatalogRemainDocumented(String document) {
        Map<String, Object> organizationGet = readObject(
                document, operationPath("/api/v1/organization", "get"));
        assertThat(organizationGet.get("description"))
                .asString()
                .contains("ADMIN", "RECEPTIONIST", "administrative detail projection", "safe summary");
        assertSessionCookieSecurity(organizationGet);
        assertResponseSchema(organizationGet, "200", "OrganizationResponse");

        Map<String, Object> branchListGet = readObject(
                document, operationPath("/api/v1/branches", "get"));
        assertThat(branchListGet.get("description"))
                .asString()
                .contains("ADMIN and RECEPTIONIST", "No organization selector or branch scope");
        assertSessionCookieSecurity(branchListGet);
        assertResponseSchema(branchListGet, "200", "BranchPageResponse");

        Map<String, Object> branchDetailGet = readObject(
                document, operationPath("/api/v1/branches/{id}", "get"));
        assertThat(branchDetailGet.get("description"))
                .asString()
                .contains("ADMIN and RECEPTIONIST may read", "not a tenant or assignment selector");
        assertSessionCookieSecurity(branchDetailGet);
        assertResponseSchema(branchDetailGet, "200", "BranchResponse");
    }

    private static void assertExistingOperationInventoryOnly(String document) {
        Map<String, Object> paths = readObject(document, "$.paths");
        assertThat(asMap(paths.get("/api/v1/organization")))
                .containsKeys("get", "put")
                .doesNotContainKeys("post", "patch", "delete");
        assertThat(asMap(paths.get("/api/v1/branches")))
                .containsKeys("get", "post")
                .doesNotContainKeys("put", "patch", "delete");
        assertThat(asMap(paths.get("/api/v1/branches/{id}")))
                .containsKeys("get", "put")
                .doesNotContainKeys("post", "patch", "delete");
        assertThat(asMap(paths.get("/api/v1/branches/{id}/activate")))
                .containsKey("post")
                .doesNotContainKeys("get", "put", "patch", "delete");
        assertThat(asMap(paths.get("/api/v1/branches/{id}/deactivate")))
                .containsKey("post")
                .doesNotContainKeys("get", "put", "patch", "delete");
        assertThat(paths)
                .doesNotContainKeys(
                        "/api/v1/organization/{id}/status",
                        "/api/v1/organization/status");
    }

    private static void assertRequestSchema(String document, String schemaName) {
        Map<String, Object> schema = readObject(
                document, "$.components.schemas." + schemaName);
        assertThat(schema.get("additionalProperties")).isEqualTo(false);
        Map<String, Object> properties = asMap(schema.get("properties"));
        Set<String> propertyNames = properties.keySet();
        switch (schemaName) {
            case "UpdateOrganizationRequest" -> assertThat(propertyNames).containsExactlyInAnyOrder(
                    "legalName", "brandName", "supportEmail", "supportPhone",
                    "defaultTimezone", "defaultCurrency", "version");
            case "CreateBranchRequest" -> assertThat(propertyNames).containsExactlyInAnyOrder(
                    "code", "name", "addressLine1", "addressLine2", "city",
                    "stateOrDepartment", "postalCode", "countryCode", "phone", "email", "timezone");
            case "UpdateBranchRequest" -> assertThat(propertyNames).containsExactlyInAnyOrder(
                    "name", "addressLine1", "addressLine2", "city", "stateOrDepartment",
                    "postalCode", "countryCode", "phone", "email", "timezone", "version");
            case "BranchStatusChangeRequest" -> assertThat(propertyNames)
                    .containsExactlyInAnyOrder("reason", "version");
            default -> throw new AssertionError("Unexpected request schema: " + schemaName);
        }
        assertThat(propertyNames).doesNotContain(
                "actor", "actorId", "organizationId", "scope", "status", "updatedAt", "createdAt");
    }

    private static void assertSessionCookieSecurity(Map<String, Object> operation) {
        List<Map<String, Object>> requirements = asList(operation.get("security"));
        assertThat(requirements).anySatisfy(requirement ->
                assertThat(requirement).containsKey("sessionCookie"));
    }

    private static void assertProblemResponse(Map<String, Object> operation, String status) {
        Map<String, Object> responses = asMap(operation.get("responses"));
        Map<String, Object> response = asMap(responses.get(status));
        Map<String, Object> content = asMap(response.get("content"));
        Map<String, Object> problem = asMap(content.get("application/problem+json"));
        Map<String, Object> schema = asMap(problem.get("schema"));
        assertThat(schema.get("$ref")).isEqualTo("#/components/schemas/ApiProblemResponse");
    }

    private static void assertResponseSchema(
            Map<String, Object> operation, String status, String schemaName) {
        Map<String, Object> responses = asMap(operation.get("responses"));
        Map<String, Object> response = asMap(responses.get(status));
        Map<String, Object> content = asMap(response.get("content"));
        Map<String, Object> json = asMap(content.get("application/json"));
        Map<String, Object> schema = asMap(json.get("schema"));
        assertThat(schema.get("$ref")).isEqualTo("#/components/schemas/" + schemaName);
    }

    private static void assertRequestBodySchema(Map<String, Object> operation, String schemaName) {
        Map<String, Object> requestBody = asMap(operation.get("requestBody"));
        Map<String, Object> content = asMap(requestBody.get("content"));
        Map<String, Object> json = asMap(content.get("application/json"));
        Map<String, Object> schema = asMap(json.get("schema"));
        assertThat(schema.get("$ref")).isEqualTo("#/components/schemas/" + schemaName);
    }

    private static String operationPath(String path, String method) {
        return "$.paths['" + path + "']." + method;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readObject(String document, String path) {
        return JsonPath.parse(document).read(path, Map.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asList(Object value) {
        return (List<Map<String, Object>>) value;
    }

    private record OperationContract(
            String path,
            String method,
            String successStatus,
            String successSchema,
            String requestSchema) {}
}
