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
    implementation(libs.spring.boot.batch)
    implementation(libs.spring.boot.quartz)
    implementation(libs.spring.kafka)

    // RDF parsing for DCAT HTTP connector
    implementation(libs.apache.jena.arq)

    // DDL parsing for view lineage
    implementation(libs.apache.calcite.core)

    // Cloud connectors
    implementation(libs.aws.glue)
    implementation(libs.aws.s3)
    implementation(libs.aws.sts)
    implementation(libs.snowflake.jdbc)

    // Object storage
    implementation(libs.minio)

    // JSON Schema validation
    implementation(libs.json.schema.validator)

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
