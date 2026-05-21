plugins {
    `java-library`
    alias(libs.plugins.spring.dependency.mgmt)
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.2")
    }
}

dependencies {
    api(project(":shared:shared-models"))
    api(libs.spring.kafka)
    api("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.slf4j:slf4j-api")
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(libs.spring.kafka.test)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.testcontainers.junit5)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
