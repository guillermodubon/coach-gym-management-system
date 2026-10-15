package io.github.guillermodubon.coachgym.reporting.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.reporting.application.ReportingCompositionApplicationService;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;

class ReportingApiWebContractTest {

    private static final Set<String> FORBIDDEN_COMPONENT_NAMES = Set.of(
            "password", "passwordhash", "token", "rawtoken", "tokenhash",
            "emailaddress", "recipientaddress", "recipientemail", "emailbody",
            "providerresponse", "providermessageid", "attachment", "attachmentbytes",
            "sessionid", "oauthsecret", "refreshtoken", "accesstoken", "qrpayload",
            "credentialfingerprint", "cardnumber", "cvc", "sql", "stacktrace");

    @Test
    void reportingControllerIsThinReadOnlyAndDoesNotResolveAuthorityFromRequestData()
            throws Exception {
        List<java.lang.reflect.Method> endpoints =
                java.util.Arrays.stream(ReportingApiController.class.getDeclaredMethods())
                        .filter(method -> method.isAnnotationPresent(GetMapping.class))
                        .toList();

        assertThat(endpoints).hasSize(4);
        assertThat(endpoints)
                .allSatisfy(method -> {
                    assertThat(method.getAnnotation(GetMapping.class)).isNotNull();
                    assertThat(method.getParameters())
                            .noneMatch(parameter -> parameter.isAnnotationPresent(RequestBody.class));
                    assertThat(method.getParameters())
                            .noneMatch(parameter -> Set.of("actorId", "userId", "role", "timezone")
                                    .contains(parameter.getName()));
                });
        assertThat(ReportingApiController.class.getDeclaredFields())
                .singleElement()
                .satisfies(field -> assertThat(field.getType())
                        .isEqualTo(ReportingCompositionApplicationService.class));
    }

    @Test
    void responseProjectionGraphContainsNoCredentialOrRecipientPayloadFields() {
        Queue<Class<?>> pending = new ArrayDeque<>(List.of(
                ReportingApiContextResponse.class,
                ReportingAdministratorDashboardResponse.class,
                ReportingReceptionistDashboardResponse.class,
                BranchComparisonResponse.class,
                FinancialTrendResponse.class,
                AccessTrendResponse.class));
        Set<Class<?>> visited = new java.util.HashSet<>();

        while (!pending.isEmpty()) {
            Class<?> current = pending.remove();
            if (!visited.add(current) || !current.getPackageName().startsWith(
                    "io.github.guillermodubon.coachgym")) {
                continue;
            }
            if (!current.isRecord()) {
                continue;
            }
            for (var component : current.getRecordComponents()) {
                assertThat(component.getName().toLowerCase(java.util.Locale.ROOT))
                        .as("response field %s.%s", current.getSimpleName(), component.getName())
                        .isNotIn(FORBIDDEN_COMPONENT_NAMES);
                enqueueCoachGymTypes(component.getGenericType(), pending);
            }
        }
    }

    private static void enqueueCoachGymTypes(Type type, Queue<Class<?>> pending) {
        if (type instanceof Class<?> candidate) {
            if (candidate.getPackageName().startsWith("io.github.guillermodubon.coachgym")) {
                pending.add(candidate);
            }
        } else if (type instanceof ParameterizedType parameterized) {
            enqueueCoachGymTypes(parameterized.getRawType(), pending);
            for (Type argument : parameterized.getActualTypeArguments()) {
                enqueueCoachGymTypes(argument, pending);
            }
        }
    }
}
