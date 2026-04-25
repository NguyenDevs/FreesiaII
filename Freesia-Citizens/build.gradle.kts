plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.citizensnpcs.co/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("net.citizensnpcs:citizens-main:2.0.33-SNAPSHOT") {
        exclude(group = "*", module = "*")
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

val processResources by tasks.named<ProcessResources>("processResources") {
    val props = mapOf("version" to rootProject.version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveFileName.set("Freesia-Citizens-${project.version}.jar")
}
