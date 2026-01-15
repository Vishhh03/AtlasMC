plugins {
    kotlin("jvm") version "2.0.21"
    id("io.github.goooler.shadow") version "8.1.8"
}

group = "com.projectatlas"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.dmulloy2.net/repository/public/")
    maven("https://repo.codemc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    // ProtocolLib removed - using native Display Entities
    implementation(kotlin("stdlib"))
    implementation("com.google.code.gson:gson:2.10.1")
    compileOnly(files("test-server/plugins/Typewriter-0.8.0.jar"))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "21"
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.shadowJar {
    archiveClassifier.set("shaded")
    mergeServiceFiles()
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.kotlin_module")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.build {
    dependsOn("shadowJar")
}
