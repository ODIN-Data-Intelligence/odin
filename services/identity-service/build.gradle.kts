plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.mgmt)
}

dependencies {
    implementation(project(":shared:shared-models"))
    implementation(project(":shared:auth-common"))

    implementation(libs.bundles.spring.web.service)
    implementation(libs.spring.boot.security)
    implementation(libs.spring.boot.oauth2)
    implementation(libs.spring.kafka)

    // Keycloak Admin Client
    implementation("org.keycloak:keycloak-admin-client:24.0.4")

    implementation(libs.springdoc.openapi)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    implementation(libs.mapstruct)
    annotationProcessor(libs.mapstruct.processor)

    runtimeOnly(libs.postgresql)

    testImplementation(libs.bundles.testing)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.bootJar { archiveFileName.set("app.jar") }
tasks.jar { enabled = false }
