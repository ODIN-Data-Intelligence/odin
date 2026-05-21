package com.odin.catalog.harvest.config;

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
                .title("ODIN Harvest API")
                .description("Configure and trigger metadata harvest pipelines from external data sources. "
                    + "Supported connectors: DCAT HTTP, AWS Glue, Snowflake, Teradata. "
                    + "Jobs run via Spring Batch and are scheduled with Quartz cron expressions.")
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
                    .description("Long-lived API key. Use 'dev-harvest' for local development.")))
            .tags(List.of(
                new Tag().name("Sources").description("Harvest source configurations — connection details for external data systems"),
                new Tag().name("Jobs").description("Harvest jobs — scheduled or manual execution plans linked to a source"),
                new Tag().name("Runs").description("Harvest run history — execution records with entity counts and status")
            ));
    }
}
