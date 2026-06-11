rootProject.name = "data-catalog"

// gradle/libs.versions.toml is auto-discovered by Gradle 8.x as the "libs" catalog.

include(
    "shared:shared-models",
    "shared:kafka-client",
    "shared:auth-common",
    "services:inventory-service",
    "services:harvest-service",
    "services:lineage-service",
    "services:search-service",
    "services:ai-service",
    "services:identity-service",
    "services:policy-service"
)
