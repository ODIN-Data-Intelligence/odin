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

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    runtimeOnly(libs.postgresql)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.spring.kafka.test)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.bootJar { archiveFileName.set("app.jar") }
tasks.jar { enabled = false }
