plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.mgmt)
}

dependencies {
    implementation(project(":shared:shared-models"))
    implementation(project(":shared:kafka-client"))
    implementation(project(":shared:auth-common"))

    implementation(libs.bundles.spring.web.service)
    implementation(libs.spring.boot.security)
    implementation(libs.spring.boot.oauth2)
    implementation(libs.spring.kafka)

    // DDL lineage parsing
    implementation(libs.apache.calcite.core)

    // JSON Schema validation for OpenLineage events
    implementation(libs.json.schema.validator)

    implementation(libs.springdoc.openapi)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    implementation(libs.mapstruct)
    annotationProcessor(libs.mapstruct.processor)

    // Apache AGE uses standard PostgreSQL JDBC driver
    runtimeOnly(libs.postgresql)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.spring.kafka.test)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.bootJar { archiveFileName.set("app.jar") }
tasks.jar { enabled = false }
