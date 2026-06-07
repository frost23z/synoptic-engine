import com.diffplug.gradle.spotless.FormatExtension

plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.jpa") version "2.3.21"

    id("com.diffplug.spotless") version "8.4.0"
}

group = "com.synopticengine"
version = "0.0.1-SNAPSHOT"
description = "api"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

extra["springModulithVersion"] = "2.0.6"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework:spring-aop")
    implementation("org.aspectj:aspectjweaver")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.apache.commons:commons-csv:1.12.0")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.0")
    implementation("com.github.librepdf:openpdf:2.0.3")
    implementation("com.anthropic:anthropic-java:2.34.0")
    implementation("org.apache.tika:tika-core:3.2.2")
    implementation("org.apache.tika:tika-parsers-standard-package:2.9.2")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.springframework.modulith:spring-modulith-starter-jpa")
    implementation("tools.jackson.module:jackson-module-kotlin")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.springframework.modulith:spring-modulith-actuator")
    runtimeOnly("org.springframework.modulith:spring-modulith-observability")
    runtimeOnly("org.springframework.modulith:spring-modulith-runtime")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("-Xmx1024m", "-Xms256m")
}

// Fast path: pure unit tests (no Spring context, no Testcontainers). Excludes
// every class tagged `integration` (the base class for integration tests carries
// the tag, so all subclasses are filtered out). Use `./gradlew unitTests` for
// sub-second local feedback while writing pure-logic code.
tasks.register<Test>("unitTests") {
    description = "Runs pure unit tests (excludes the 'integration' JUnit tag)."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { excludeTags("integration") }
}

// Drift gate: boots the context and re-dumps `/v3/api-docs` to the repo-root
// `api-docs.json` snapshot (the frontend type generator's input). CI runs this,
// then `git diff --exit-code api-docs.json` to fail on a stale spec. Needs Docker
// (Testcontainers). See OpenApiSpecDriftTest.
tasks.register<Test>("dumpOpenApiSpec") {
    description = "Re-dumps the OpenAPI spec to ../api-docs.json for the drift gate."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    filter { includeTestsMatching("com.synopticengine.api.OpenApiSpecDriftTest") }
    systemProperty("openapi.dump", "true")
    // Always re-run; never serve a cached "up-to-date" result for a dump task.
    outputs.upToDateWhen { false }
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint("1.8.0")
        commonFormatSteps()
    }

    kotlinGradle {
        target("*.gradle.kts")
        ktlint("1.8.0")
        commonFormatSteps()
    }
}

fun FormatExtension.commonFormatSteps() {
    trimTrailingWhitespace()
    endWithNewline()
}

configurations.all {
    exclude(group = "io.swagger.core.v3", module = "swagger-annotations")
}
