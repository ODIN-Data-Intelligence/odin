package com.odin.catalog.lineage.config;

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
                .title("ODIN Lineage API")
                .description("Ingest OpenLineage-compatible run events, submit DDL for automatic lineage extraction, "
                    + "and query upstream/downstream dataset lineage graphs stored in Apache AGE (Cypher over PostgreSQL). "
                    + "Compatible with Marquez, dbt, and any OpenLineage 1.x producer.")
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
                    .description("Long-lived API key. Use 'dev-lineage' for local development.")))
            .tags(List.of(
                new Tag().name("Lineage").description("OpenLineage event ingestion and graph traversal — upstream, downstream, and impact analysis"),
                new Tag().name("DDL").description("DDL-based lineage extraction — parse CREATE VIEW / TABLE AS SELECT to derive DERIVED_FROM edges")
            ));
    }
}
