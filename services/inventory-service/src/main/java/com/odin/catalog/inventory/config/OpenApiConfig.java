package com.odin.catalog.inventory.config;

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
                .title("ODIN Inventory API")
                .description("Manage catalogs, datasets, distributions, data products, logical models, and vocabulary mappings. "
                    + "Implements DCAT 3.0, DPROD, CSV-W, and SKOS standards.")
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
                    .description("Long-lived API key. Use 'dev-inventory' for local development.")))
            .tags(List.of(
                new Tag().name("Catalogs").description("DCAT Catalog registry — top-level containers for datasets"),
                new Tag().name("Datasets").description("DCAT Datasets — logical representations of data assets"),
                new Tag().name("Distributions").description("DCAT Distributions — physical access points and CSV-W schemas"),
                new Tag().name("Data Products").description("DPROD Data Products — business-level data ownership and lifecycle"),
                new Tag().name("Vocabularies").description("Controlled vocabulary registry — schema.org, FIBO, SKOS, and custom"),
                new Tag().name("Vocabulary Profiles").description("Associate vocabularies with datasets to drive concept suggestions"),
                new Tag().name("Logical Models").description("Logical data models — semantic business view of a dataset's structure"),
                new Tag().name("Logical Elements").description("Logical data elements — individual business concepts within a model"),
                new Tag().name("Vocabulary Mappings").description("SKOS-style mappings between logical elements and vocabulary concepts")
            ));
    }
}
