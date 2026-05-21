plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.mgmt)
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:1.0.0")
    }
}

dependencies {
    implementation(project(":shared:shared-models"))
    implementation(project(":shared:kafka-client"))
    implementation(project(":shared:auth-common"))

    implementation(libs.bundles.spring.web.service)
    implementation(libs.spring.boot.security)
    implementation(libs.spring.boot.oauth2)
    implementation(libs.spring.boot.webflux)          // SSE streaming
    implementation(libs.spring.kafka)

    // Spring AI — providers activated via application.yml
    implementation(libs.spring.ai.ollama)
    implementation(libs.spring.ai.openai)
    implementation(libs.spring.ai.pgvector)

    // pgvector JDBC type support
    implementation(libs.pgvector)

    // LangChain4j — tool-calling agent for dataset context gathering
    implementation(libs.langchain4j.core)
    implementation(libs.langchain4j)
    implementation(libs.langchain4j.ollama)

    implementation(libs.springdoc.openapi)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    implementation(libs.mapstruct)
    annotationProcessor(libs.mapstruct.processor)

    runtimeOnly(libs.postgresql)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.spring.kafka.test)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.bootJar { archiveFileName.set("app.jar") }
tasks.jar { enabled = false }
