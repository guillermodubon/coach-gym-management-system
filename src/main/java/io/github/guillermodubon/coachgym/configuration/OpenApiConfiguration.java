package io.github.guillermodubon.coachgym.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    @Bean
    OpenAPI coachGymOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Coach Gym Management API")
                .version("v1")
                .description("Administrative backend for Coach Gym. Authentication uses server-side sessions."));
    }
}
