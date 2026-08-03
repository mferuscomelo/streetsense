import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.springframework.boot.gradle.tasks.run.BootRun
import java.time.Instant

plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "io.streetsense"
version = "0.1.0"

java {
    // The contest's Java 26 proof: this toolchain declaration plus the
    // committed build.log (see the `buildLog` task below).
    toolchain {
        languageVersion = JavaLanguageVersion.of(26)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
}

// All three preview JEPs used in this codebase (Structured Concurrency 525,
// Primitive Types in Patterns 530) require --enable-preview at compile time
// with --release 26, and again at runtime/test time.
tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("--release", "26", "--enable-preview"))
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("--enable-preview")
}

tasks.named<BootRun>("bootRun") {
    jvmArgs("--enable-preview")
}

tasks.named<BootJar>("bootJar") {
    archiveFileName.set("backend-${project.version}.jar")
}

// Belt-and-braces Java 26 evidence committed alongside the toolchain
// declaration above, per the contest's proof requirement.
tasks.register("buildLog") {
    group = "verification"
    description = "Writes java --version output to build.log at the repo root."
    val launcher = javaToolchains.launcherFor(java.toolchain)
    doLast {
        val javaExe = launcher.get().executablePath.asFile.absolutePath
        val process = ProcessBuilder(javaExe, "--version")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        rootDir.resolve("../build.log").writeText(
            "StreetSense backend — Java 26 build evidence\n" +
                "Generated: ${Instant.now()}\n\n" +
                output
        )
    }
}
