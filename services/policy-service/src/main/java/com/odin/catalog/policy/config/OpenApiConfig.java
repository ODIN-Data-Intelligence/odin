package com.odin.catalog.policy.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("ODIN Policy Service API")
                .description("Policy Decision Point (PDP) for ODIN Catalog. "
                    + "Stores ODRL policies per dataset and evaluates them using the ODRE enforcement engine. "
                    + "Platform services (enforcement points) call /evaluate to obtain a UsageDecision "
                    + "before granting access to datasets.")
                .version("1.0.0"))
            .addSecurityItem(new SecurityRequirement()
                .addList("BearerAuth")
                .addList("ApiKeyAuth"))
            .components(new Components()
                .addSecuritySchemes("BearerAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Keycloak-issued OIDC JWT token"))
                .addSecuritySchemes("ApiKeyAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.HEADER)
                    .name("X-API-Key")
                    .description("Long-lived API key. Use 'dev-policy' for local development.")))
            .tags(List.of(
                new Tag().name("Policies").description("CRUD for ODRL policy records per dataset"),
                new Tag().name("Evaluation").description("ODRE enforcement engine — evaluate policies at request time")
            ));
    }
}
